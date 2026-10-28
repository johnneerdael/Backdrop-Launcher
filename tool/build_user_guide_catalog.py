#!/usr/bin/env python3
"""Validate a native screenshot manifest and build a local, searchable guide.

Requires Pillow. Run after capture finishes:
    python3 tool/build_user_guide_catalog.py docs/user-guide-capture/2026-10-27
    python3 tool/build_user_guide_catalog.py CAPTURE_DIRECTORY --validate-only

Only manifest-listed PNGs become catalog entries. Source inventories and the
feature catalog supply grouping hints, never assertions of functional coverage.
Original PNGs and manifest.json are never written. JPEGs are diagnostic copies.
"""
from __future__ import annotations

import argparse
import collections
import datetime as dt
import hashlib
import html
import io
import json
import math
import os
from pathlib import Path
import re
import sys
import tempfile
from urllib.parse import quote

try:
    from PIL import Image, ImageDraw, ImageFont, ImageOps
except ImportError:
    raise SystemExit("Pillow is required: python3 -m pip install Pillow")


class CatalogError(Exception):
    """A manifest or original image cannot be validated safely."""


def value_text(value):
    """Render recorded metadata without inferring missing state."""
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    if isinstance(value, (list, tuple)):
        return " → ".join(value_text(item) for item in value)
    return json.dumps(value, ensure_ascii=False, sort_keys=True)


def inside_file(root, name, description):
    """Resolve only relative, existing files confined to the capture directory."""
    if not isinstance(name, str) or not name.strip():
        raise CatalogError(f"{description}: missing relative file path")
    relative = Path(name)
    candidate = (root / relative).resolve()
    if relative.is_absolute() or not candidate.is_relative_to(root):
        raise CatalogError(f"{description}: file must stay inside capture directory: {name!r}")
    if not candidate.is_file():
        raise CatalogError(f"{description}: file does not exist: {name!r}")
    return candidate


def load_ocr(root, item, warnings):
    """Use recorded text, falling back to the real OCR sidecar when available."""
    lines = item.get("visible_text")
    if lines:
        if isinstance(lines, str):
            return lines
        if isinstance(lines, list):
            return "\n".join(value_text(line) for line in lines)
        warnings.append(f"{item['id']}: unsupported visible_text; trying OCR sidecar")
    sidecar = item.get("ocr_file")
    if not sidecar:
        guessed = Path("ocr") / (Path(item["file"]).stem + ".json")
        if (root / guessed).is_file():
            sidecar = guessed.as_posix()
    if not sidecar:
        return ""
    try:
        data = json.loads(inside_file(root, sidecar, "OCR sidecar").read_text())
        if isinstance(data, list):
            return "\n".join(value_text(row.get("text", "")) if isinstance(row, dict)
                             else value_text(row) for row in data)
        if isinstance(data, dict):
            return value_text(data.get("text", data.get("visible_text", "")))
        raise ValueError("expected an OCR row list or object")
    except (CatalogError, OSError, ValueError) as error:
        warnings.append(f"{item['id']}: OCR unavailable: {error}")
        return ""


def normalize(text):
    return re.sub(r"[^\w]+", " ", value_text(text).casefold()).strip()


def grouping_hints(root, warnings):
    """Read organizational labels only; planned entries never become captures."""
    by_id, paths, order = {}, [], []
    catalog = root / "FEATURE-CATALOG.md"
    if catalog.is_file():
        try:
            catalog_text = inside_file(root, catalog.name, "Feature catalog").read_text()
        except (CatalogError, OSError) as error:
            warnings.append(f"Feature catalog grouping unavailable: {error}")
            catalog_text = ""
        for line in catalog_text.splitlines():
            match = re.match(r"^#{2,4} (.+)$", line)
            if match:
                title = re.sub(r" capture states$", "", match.group(1))
                if title not in order:
                    order.append(title)
    for filename in ("source-settings-inventory.json", "source-functions-inventory.json"):
        path = root / filename
        if not path.is_file():
            continue
        try:
            data = json.loads(inside_file(root, filename, "Source inventory").read_text())
            if not isinstance(data, dict):
                raise ValueError("expected object")
            for section in data.get("sections", []):
                title = value_text(section.get("title"))
                route = normalize(section.get("path", []))
                if title and route:
                    paths.append((route, title))
                for planned in section.get("screenshots", []):
                    if planned.get("id") and title:
                        by_id[planned["id"]] = title
            for planned in data.get("screenshots", []):
                title = value_text(planned.get("area", "Other")).replace("_", " ").title()
                if planned.get("screenshot_id"):
                    by_id[planned["screenshot_id"]] = title
        except (CatalogError, OSError, ValueError, TypeError, AttributeError) as error:
            warnings.append(f"Grouping hints unavailable from {filename}: {error}")
    return by_id, sorted(paths, key=lambda pair: len(pair[0]), reverse=True), order


def group_for(item, hints):
    by_id, paths, _ = hints
    if item.get("group"):
        return value_text(item["group"])
    if item["id"] in by_id:
        return by_id[item["id"]]
    route = normalize(item.get("route"))
    for path, title in paths:
        if route == path:
            return title
        if route.startswith(path + " ") and title not in {"Settings", "Interface", "System"}:
            return title
    if item["id"].startswith("home-"):
        return "Home"
    words = normalize(" ".join([item["id"], value_text(item.get("route")),
                                " ".join(item.get("tags", []))]))
    aliases = [
        (("accent",), "Accent Color"), (("opacity", "icon background", "appearance"), "Appearance"),
        (("focus border", "border thickness"), "Focus border thickness"),
        (("clock style", "screensaver"), "Screensaver Clock Style"),
        (("date format", "time format", "date time"), "Date and time format"),
        (("back action", "back button"), "Back button action"),
        (("status bar", "statusbar", "network"), "Status bar"), (("misc", "app names", "category titles"), "Miscellaneous"),
        (("trakt",), "Trakt"), (("backdrop", "wallpaper"), "Backdrops"),
        (("watch next", "continue watching"), "Watch Next"), (("artwork", "banner picker"), "Artwork"),
        (("app menu", "context menu", "reorder"), "App Context Menu"),
        (("applications", "row editor", "row manager", "app inventory", "app details"), "Applications"),
        (("about", "license"), "About Backdrop Launcher"), (("update",), "Check for updates"),
        (("interface",), "Interface"), (("system settings", "external"), "External Actions"),
        (("system",), "System"), (("settings",), "Settings"), (("home",), "Home"),
    ]
    for needles, title in aliases:
        if any(needle in words for needle in needles):
            return title
    return "Other captures"


def validate(root):
    """Validate PNG content, exact dimensions, hashes, IDs and manifest metadata."""
    manifest_path = inside_file(root, "manifest.json", "Manifest")
    try:
        snapshot = manifest_path.read_bytes()
        manifest = json.loads(snapshot)
    except (OSError, ValueError) as error:
        raise CatalogError(f"Cannot read manifest.json: {error}") from error
    if not isinstance(manifest, dict) or not isinstance(manifest.get("screenshots"), list):
        raise CatalogError("manifest.json must contain a screenshots array")
    errors, warnings, captures = [], [], []
    hints = grouping_hints(root, warnings)
    ids, sequences = set(), set()
    for index, raw in enumerate(manifest["screenshots"], 1):
        name = f"Entry {index}"
        try:
            if not isinstance(raw, dict):
                raise CatalogError(f"{name}: expected object")
            item = dict(raw)
            slug = item.get("id")
            if not isinstance(slug, str) or not re.fullmatch(r"[a-z0-9][a-z0-9_-]*", slug):
                raise CatalogError(f"{name}: id must contain lowercase letters, digits, hyphens or underscores")
            name = slug
            if slug in ids:
                raise CatalogError(f"Duplicate screenshot ID: {slug}")
            ids.add(slug)
            sequence = item.get("sequence")
            if type(sequence) is not int or sequence < 1 or sequence in sequences:
                raise CatalogError(f"{slug}: sequence must be a unique positive integer")
            sequences.add(sequence)
            if not isinstance(item.get("title"), str) or not item["title"].strip():
                raise CatalogError(f"{slug}: title must be nonempty text")
            tags = item.get("tags", [])
            if not isinstance(tags, list) or not all(isinstance(tag, str) for tag in tags):
                raise CatalogError(f"{slug}: tags must be an array of strings")
            item["tags"] = list(dict.fromkeys(tags))
            path = inside_file(root, item.get("file"), slug)
            if path.suffix.casefold() != ".png":
                raise CatalogError(f"{slug}: expected a PNG file path")
            width, height = item.get("width"), item.get("height")
            if type(width) is not int or type(height) is not int or min(width, height) < 1:
                raise CatalogError(f"{slug}: width/height dimensions must be positive integers")
            expected = item.get("sha256")
            if not isinstance(expected, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", expected):
                raise CatalogError(f"{slug}: missing or malformed SHA-256")
            png = path.read_bytes()
            digest = hashlib.sha256(png).hexdigest()
            if digest != expected.casefold():
                raise CatalogError(f"{slug}: SHA-256 mismatch for {item['file']}")
            with Image.open(io.BytesIO(png)) as image:
                if image.format != "PNG":
                    raise CatalogError(f"{slug}: file content is not PNG")
                if image.size != (width, height):
                    raise CatalogError(f"{slug}: dimensions {image.size} do not match manifest {(width, height)}")
                image.verify()
            with Image.open(io.BytesIO(png)) as image:
                image.load()
            item["sha256"] = digest
            item["_path"] = path
            item["_ocr"] = load_ocr(root, item, warnings)
            item["_group"] = group_for(item, hints)
            item["_thumb"] = f"thumbnails/{sequence:03d}-{slug}.jpg"
            captures.append(item)
        except (CatalogError, OSError, ValueError, Image.DecompressionBombError) as error:
            errors.append(f"{name}: {error}")
    if errors:
        raise CatalogError("Validation failed:\n  " + "\n  ".join(errors))
    captures.sort(key=lambda item: item["sequence"])
    listed = {item["_path"] for item in captures}
    unlisted = sorted(path.relative_to(root).as_posix() for path in (root / "screenshots").rglob("*")
                      if path.is_file() and path.suffix.casefold() == ".png" and path.resolve() not in listed)
    if unlisted:
        warnings.append(f"{len(unlisted)} PNG(s) in screenshots/ are not listed in manifest; excluded from catalog")
    if manifest_path.read_bytes() != snapshot:
        raise CatalogError("manifest.json changed during validation; wait for capture to finish and rerun")
    return manifest, captures, warnings, unlisted, snapshot, hints[2]


def duplicates(captures):
    hashes = collections.defaultdict(list)
    for item in captures:
        hashes[item["sha256"]].append(item)
    return [{"sha256": digest, "captures": [{key: item[key] for key in ("sequence", "id", "title", "file")}
                                            for item in group]}
            for digest, group in hashes.items() if len(group) > 1]


def md(text):
    # Entities remain literal text to Markdown parsers as well as HTML parsers;
    # e.g. an untrusted public-list name cannot inject a remote Markdown image.
    escaped = html.escape(value_text(text))
    escaped = re.sub(r"[\[\]()*_`!\\]", lambda match: f"&#{ord(match[0])};", escaped)
    return escaped.replace("|", "&#124;").replace("\n", "<br>")


def url(path):
    return quote(str(path).replace(os.sep, "/"), safe="/-._~")


def groups(captures, order):
    by_group = collections.defaultdict(list)
    for item in captures:
        by_group[item["_group"]].append(item)
    rank = {name: index for index, name in enumerate(order)}
    return sorted(by_group.items(), key=lambda pair: (rank.get(pair[0], len(rank)), pair[0].casefold()))


def duplicate_markdown(dupes):
    lines = ["# Duplicate PNG hashes", "", f"{len(dupes)} byte-identical PNG group(s).", "",
             "An identical hash means identical file bytes. It does not prove different functionality, "
             "a successful setting change, or equivalent UI behavior. Entries remain separate and need visual review.", ""]
    if not dupes:
        lines.extend(["No duplicate SHA-256 values among validated manifest entries.", ""])
    for index, group in enumerate(dupes, 1):
        lines.extend([f"## Group {index}", "", f"SHA-256: `{group['sha256']}`", ""])
        for item in group["captures"]:
            lines.append(f"- {item['sequence']:03d} [{md(item['id'])}]({url(item['file'])}) — {md(item['title'])}")
        lines.append("")
    return "\n".join(lines)


def index_markdown(manifest, captures, dupes, warnings, unlisted, order, sheets, snapshot_hash):
    lines = ["# Backdrop Launcher screenshot index", "", "[Searchable gallery](gallery.html) · "
             "[Duplicate-hash review](DUPLICATE-HASHES.md) · [Validation report](validation-report.json)", "",
             f"**{len(captures)} actual manifest entries**, {len(dupes)} duplicate-hash group(s). "
             "Original PNG dimensions and SHA-256 values were validated before generation.", "",
             "Only screenshots recorded in manifest.json appear below. Inventory/catalog files supply grouping "
             "labels and do not establish capture or functional coverage. JPEG thumbnails/contact sheets are "
             "diagnostic derivatives; every original PNG remains unchanged.", "",
             f"Manifest SHA-256: `{snapshot_hash}`", ""]
    if (manifest.get("capture_date")):
        lines.extend([f"Capture date: {md(manifest['capture_date'])}", ""])
    if sheets:
        lines.extend(["QA contact sheets: " + " · ".join(f"[Sheet {i}]({url(p)})" for i, p in enumerate(sheets, 1)), ""])
    if warnings:
        lines.extend(["## Validation notes", ""] + [f"- {md(warning)}" for warning in warnings] + [""])
    if unlisted:
        lines.extend(["Unlisted PNGs (excluded; no capture metadata inferred):", ""]
                     + [f"- <code>{html.escape(path)}</code>" for path in unlisted] + [""])
    dup_lookup = {item["id"]: index for index, group in enumerate(dupes, 1) for item in group["captures"]}
    for group, items in groups(captures, order):
        lines.extend([f"## {md(group)}", ""])
        for item in items:
            lines.extend([f"<a id=\"{item['id']}\"></a>", "", f"### {item['sequence']:03d} · {md(item['title'])}", "",
                          f"[![{md(item['title'])}]({url(item['_thumb'])})]({url(item['file'])})", "",
                          f"- **ID:** `{item['id']}`",
                          f"- **Original PNG:** [{md(item['file'])}]({url(item['file'])}) ({item['width']} × {item['height']})",
                          f"- **Route:** {md(item.get('route')) or '(not recorded)'}",
                          f"- **State:** {md(item.get('state')) or '(not recorded)'}",
                          f"- **Purpose:** {md(item.get('purpose')) or '(not recorded)'}",
                          f"- **Tags:** {', '.join(md(tag) for tag in item['tags']) or '(none recorded)'}",
                          f"- **SHA-256:** `{item['sha256']}`"])
            for key, label in (("captured_at", "Captured at"), ("source", "Recorded source"), ("notes", "Notes")):
                if item.get(key):
                    lines.append(f"- **{label}:** {md(item[key])}")
            if item["id"] in dup_lookup:
                lines.append(f"- **Duplicate review:** Same PNG bytes as other entries in [group {dup_lookup[item['id']]}](DUPLICATE-HASHES.md#group-{dup_lookup[item['id']]}); no additional functionality inferred.")
            lines.extend(["", "<details><summary>OCR transcript (machine-read)</summary>", "",
                          "<pre>" + html.escape(item["_ocr"] or "OCR not recorded.") + "</pre>", "", "</details>", ""])
    return "\n".join(lines)


GALLERY_STYLE = """
:root{color-scheme:dark;--bg:#0b1018;--panel:#141e2b;--line:#283649;--ink:#edf4ff;--muted:#a8b7cb;--accent:#83e4d2}
*{box-sizing:border-box}body{margin:0;background:radial-gradient(ellipse at 12% 0,#193042 0,transparent 45%),var(--bg);color:var(--ink);font:15px/1.55 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif}
a{color:var(--accent);text-underline-offset:3px}button,input,select{font:inherit}button,select{cursor:pointer}button:focus-visible,a:focus-visible,input:focus-visible,select:focus-visible{outline:3px solid var(--accent);outline-offset:3px}
.wrap{max-width:1480px;margin:auto;padding:0 32px}header{padding:52px 0 28px}.eyebrow{text-transform:uppercase;letter-spacing:.18em;color:var(--accent);font-size:12px;font-weight:750}h1{font-size:clamp(32px,5vw,58px);letter-spacing:-.045em;line-height:1.1;margin:13px 0 18px}.intro{max-width:850px;color:var(--muted);font-size:17px}.links{display:flex;gap:22px;flex-wrap:wrap}.stats{display:flex;gap:32px;flex-wrap:wrap;margin:30px 0 10px}.stat strong{display:block;font-size:27px;line-height:1.2}.stat span{color:var(--muted);font-size:12px;text-transform:uppercase;letter-spacing:.08em}
.controls{position:sticky;top:0;z-index:2;background:#0b1018f5;border-block:1px solid var(--line);padding:18px 0 12px;backdrop-filter:blur(12px)}.search-row{display:flex;gap:12px;align-items:center;flex-wrap:wrap}input,select{color:var(--ink);background:#152131;border:1px solid #35465d;border-radius:10px;padding:12px 14px}input{flex:1;min-width:220px}select{max-width:280px}button{border:1px solid var(--line);background:var(--panel);color:var(--ink);border-radius:999px;padding:7px 12px}.tags{display:flex;gap:7px;overflow-x:auto;padding:12px 0 5px}.tags button{white-space:nowrap;font-size:12px}.tags button[aria-pressed=true],.tag.active{background:var(--accent);color:#082621;border-color:var(--accent)}.count{font-size:13px;color:var(--muted);margin:8px 0 0}
main{padding-top:24px;padding-bottom:50px}.section-title{display:flex;align-items:center;gap:12px;font-size:22px;letter-spacing:-.02em;margin:30px 0 16px}.section-title span{font-size:12px;color:var(--muted);font-weight:400;border:1px solid var(--line);padding:2px 9px;border-radius:12px}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,400px),1fr));gap:24px}.card{min-width:0;background:linear-gradient(140deg,#182433,#111a26);border:1px solid var(--line);border-radius:16px;overflow:hidden;box-shadow:0 15px 35px #0002;content-visibility:auto;contain-intrinsic-size:auto 650px}.image-link{display:block;aspect-ratio:16/9;background:#05080d;position:relative}.image-link img{width:100%;height:100%;object-fit:contain;display:block}.seq{position:absolute;top:12px;left:12px;padding:3px 9px;border:1px solid #ffffff44;background:#07101cdb;border-radius:7px;font-size:12px;font-variant-numeric:tabular-nums}.card-body{padding:19px 20px 21px}.card h3{margin:0 0 5px;font-size:19px;line-height:1.25;letter-spacing:-.015em}.identifier{color:var(--muted);font:11px ui-monospace,SFMono-Regular,monospace;overflow-wrap:anywhere}.metadata{margin:15px 0 13px;font-size:13px}.metadata dt{color:var(--muted);font-size:11px;text-transform:uppercase;letter-spacing:.06em;margin-top:10px}.metadata dd{margin:2px 0 0;overflow-wrap:anywhere;white-space:pre-wrap}.card-tags{display:flex;gap:6px;flex-wrap:wrap;margin:12px 0}.card-tags button{font-size:11px;padding:3px 8px;background:#223449}.duplicate{font-size:12px;color:#f7cf85;padding:9px 11px;border:1px solid #7b633b;border-radius:8px;margin:12px 0;background:#e4b5670b}.ocr-preview{font-size:12px;line-height:1.5;color:var(--muted);border-left:2px solid #3f6377;padding-left:10px;overflow-wrap:anywhere}.ocr-label{font-size:10px;text-transform:uppercase;letter-spacing:.08em;color:#819ab5}details{margin-top:12px;font-size:12px;color:var(--muted)}summary{cursor:pointer;color:#c6d7eb}pre{font:12px/1.6 ui-monospace,SFMono-Regular,monospace;white-space:pre-wrap;word-break:break-word;background:#080f18;border-radius:8px;padding:12px;max-height:260px;overflow:auto}.actions{display:flex;justify-content:space-between;gap:12px;margin-top:15px;font-size:12px}.hash{font:10px/1.6 ui-monospace,SFMono-Regular,monospace;overflow-wrap:anywhere}.empty{padding:50px 0;color:var(--muted)}footer{color:var(--muted);border-top:1px solid var(--line);padding:25px 0 40px;font-size:12px}.evidence{max-width:1000px} [hidden]{display:none!important}
@media(max-width:650px){.wrap{padding:0 16px}header{padding-top:30px}.controls{position:static}.search-row select{max-width:100%;flex:1}.stats{gap:22px}.card-body{padding:17px}h1{font-size:36px}}@media(prefers-reduced-motion:reduce){*{scroll-behavior:auto!important}}
"""

GALLERY_SCRIPT = """
const search = document.querySelector('#search');
const groupFilter = document.querySelector('#group');
const cards = [...document.querySelectorAll('.card')];
let selectedTag = '';
function filterCards(){
  const words = search.value.toLocaleLowerCase().trim().split(/\\s+/).filter(Boolean);
  let shown = 0;
  for(const card of cards){
    const text = card.dataset.search.toLocaleLowerCase();
    const tags = JSON.parse(card.dataset.tags);
    const visible = words.every(word => text.includes(word)) && (!selectedTag || tags.includes(selectedTag)) && (!groupFilter.value || card.dataset.group === groupFilter.value);
    card.hidden = !visible;
    if(visible) shown++;
  }
  for(const section of document.querySelectorAll('.capture-group')){
    const visibleCount = [...section.querySelectorAll('.card')].filter(card => !card.hidden).length;
    section.hidden = visibleCount === 0;
    section.querySelector('.group-count').textContent = visibleCount;
  }
  document.querySelector('#count').textContent = `${shown} of ${cards.length} captures shown` + (selectedTag ? ` · tag: ${selectedTag}` : '');
  document.querySelector('#empty').hidden = shown !== 0;
  for(const button of document.querySelectorAll('[data-tag]')){
    const active = button.dataset.tag === selectedTag;
    button.setAttribute('aria-pressed', String(active));
    button.classList.toggle('active', active);
  }
}
search.addEventListener('input', filterCards);
groupFilter.addEventListener('change', filterCards);
document.addEventListener('click', event => {
  const button = event.target.closest('[data-tag]');
  if(button){selectedTag = selectedTag === button.dataset.tag ? '' : button.dataset.tag;filterCards();}
});
document.querySelector('#clear').addEventListener('click', () => {search.value = '';groupFilter.value = '';selectedTag = '';filterCards();search.focus();});
document.addEventListener('keydown', event => {if(event.key === '/' && !['INPUT','TEXTAREA','SELECT'].includes(document.activeElement.tagName)){event.preventDefault();search.focus();}});
search.value = new URLSearchParams(location.search).get('q') || '';
filterCards();
"""


def gallery_html(captures, dupes, order, snapshot_hash):
    escape = lambda text: html.escape(value_text(text), quote=True)
    dup_lookup = {item["id"]: index for index, group in enumerate(dupes, 1) for item in group["captures"]}
    tags = sorted({tag for item in captures for tag in item["tags"]}, key=str.casefold)
    grouped = groups(captures, order)
    sections = []
    for group, items in grouped:
        cards = []
        for item in items:
            searchable = "\n".join([value_text(item.get(key)) for key in
                                     ("id", "title", "route", "state", "purpose", "notes")]
                                    + [group, " ".join(item["tags"]), item["_ocr"]])
            metadata = "".join(f"<dt>{label}</dt><dd>{escape(item.get(key)) or 'Not recorded'}</dd>"
                               for key, label in (("route", "Route"), ("state", "State"), ("purpose", "Purpose")))
            tag_buttons = "".join(f'<button class="tag" type="button" data-tag="{escape(tag)}" aria-pressed="false">{escape(tag)}</button>' for tag in item["tags"])
            duplicate = (f'<p class="duplicate">Same PNG bytes · <a href="DUPLICATE-HASHES.md#group-{dup_lookup[item["id"]]}">review group {dup_lookup[item["id"]]}</a>. No additional functionality inferred.</p>'
                         if item["id"] in dup_lookup else "")
            ocr = item["_ocr"] or "OCR not recorded."
            preview = " ".join(ocr.split())
            preview = preview[:210] + ("…" if len(preview) > 210 else "")
            details = "".join(f"<p><b>{label}:</b> {escape(item[key])}</p>" for key, label in
                              (("captured_at", "Captured"), ("source", "Recorded source"), ("notes", "Notes")) if item.get(key))
            cards.append(f'''<article class="card" id="{item['id']}" data-group="{escape(group)}" data-search="{escape(searchable)}" data-tags="{escape(json.dumps(item['tags'], ensure_ascii=False))}">
<a class="image-link" href="{url(item['file'])}" target="_blank" rel="noopener" aria-label="Open original PNG: {escape(item['title'])}"><img src="{url(item['_thumb'])}" loading="lazy" decoding="async" width="{item['width']}" height="{item['height']}" alt="{escape(item['title'])}"><span class="seq">{item['sequence']:03d} · {item['width']} × {item['height']}</span></a>
<div class="card-body"><h3>{escape(item['title'])}</h3><span class="identifier">{item['id']}</span><dl class="metadata">{metadata}</dl><div class="card-tags">{tag_buttons}</div>{duplicate}
<div class="ocr-label">Visible OCR · machine-read</div><p class="ocr-preview">{escape(preview)}</p><details><summary>Full OCR transcript</summary><pre>{escape(ocr)}</pre></details>
<details><summary>Capture provenance &amp; SHA-256</summary>{details}<p class="hash">{item['sha256']}</p></details><div class="actions"><a href="{url(item['file'])}" target="_blank" rel="noopener">Open original PNG ↗</a><a href="#{item['id']}" aria-label="Link to {escape(item['title'])}">#{item['sequence']:03d}</a></div></div></article>''')
        sections.append(f'<section class="capture-group"><h2 class="section-title">{escape(group)} <span class="group-count">{len(items)}</span></h2><div class="grid">' + "\n".join(cards) + '</div></section>')
    options = "".join(f'<option value="{escape(group)}">{escape(group)}</option>' for group, _ in grouped)
    buttons = "".join(f'<button type="button" data-tag="{escape(tag)}" aria-pressed="false">{escape(tag)}</button>' for tag in tags)
    sizes = ", ".join(sorted({f"{item['width']} × {item['height']}" for item in captures})) or "No captures"
    return f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="color-scheme" content="dark"><title>Backdrop Launcher · Captured screens</title><style>{GALLERY_STYLE}</style></head>
<body><header class="wrap"><div class="eyebrow">Backdrop Launcher / User guide evidence</div><h1>Captured screens.</h1><p class="intro">Browse the actual screenshot set. Search labels, routes, recorded states and visible OCR text; open any image as its original, full-resolution PNG.</p><nav class="links" aria-label="Guide artifacts"><a href="INDEX.md">Screenshot index</a><a href="FEATURE-CATALOG.md">Feature reference</a><a href="DUPLICATE-HASHES.md">Duplicate-hash review</a><a href="validation-report.json">Validation report</a><a href="manifest.json">Manifest</a></nav><div class="stats"><div class="stat"><strong>{len(captures)}</strong><span>Recorded captures</span></div><div class="stat"><strong>{len(grouped)}</strong><span>Browse groups</span></div><div class="stat"><strong>{len(dupes)}</strong><span>Duplicate hash groups</span></div><div class="stat"><strong>{escape(sizes)}</strong><span>Original PNG pixels</span></div></div></header>
<div class="controls"><div class="wrap"><div class="search-row"><label for="search" hidden>Search captures</label><input id="search" type="search" placeholder="Search visible text, setting, route or state…" autocomplete="off"><label for="group" hidden>Filter group</label><select id="group"><option value="">All groups</option>{options}</select><button id="clear" type="button">Clear filters</button></div><div class="tags" role="group" aria-label="Filter by tag">{buttons}</div><p id="count" class="count" aria-live="polite">{len(captures)} captures</p></div></div>
<main class="wrap">{''.join(sections)}<p id="empty" class="empty" hidden>No captures match these filters.</p></main>
<footer><div class="wrap"><p class="evidence">Only manifest-listed images appear here. Source inventories and the feature reference supply organizational labels; they do not establish capture or functional coverage. Identical PNG hashes mean identical file bytes and do not prove different functionality. OCR is machine-read and may contain errors. JPEG previews and contact sheets are diagnostic copies; original PNGs are unchanged.</p><p>Validated manifest SHA-256: <span class="hash">{snapshot_hash}</span></p></div></footer><script>{GALLERY_SCRIPT}</script></body></html>
'''


def qa_font(size):
    for filename in ("/System/Library/Fonts/Supplemental/Arial.ttf",
                     "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        if Path(filename).is_file():
            return ImageFont.truetype(filename, size)
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def fit_lines(draw, text, font, width, limit):
    """Wrap measured text and explicitly mark truncation in QA labels."""
    words, lines, current = " ".join(value_text(text).split()).split(" "), [], ""
    for word in words:
        trial = (current + " " + word).strip()
        if current and draw.textlength(trial, font=font) > width:
            lines.append(current)
            current = word
        else:
            current = trial
    if current:
        lines.append(current)
    if len(lines) > limit:
        lines = lines[:limit]
        lines[-1] += "…"
    result = []
    for line in lines:
        if draw.textlength(line, font=font) > width:
            while line and draw.textlength(line + "…", font=font) > width:
                line = line[:-1]
            line += "…"
        result.append(line)
    return result


def rgb_copy(path):
    with Image.open(path) as image:
        image.load()
        rgba = image.convert("RGBA")
    background = Image.new("RGBA", rgba.size, (8, 14, 23, 255))
    background.alpha_composite(rgba)
    return background.convert("RGB")


def derivatives(stage, captures, dupes, args):
    thumb_dir, sheet_dir = stage / "thumbnails", stage / "contact-sheets"
    thumb_dir.mkdir()
    sheet_dir.mkdir()
    resample = Image.Resampling.LANCZOS
    for item in captures:
        image = rgb_copy(item["_path"])
        image.thumbnail((args.thumb_width, args.thumb_width), resample)
        image.save(stage / item["_thumb"], "JPEG", quality=86, optimize=True)
    columns, rows, tile_width = args.contact_columns, args.contact_rows, 400
    image_height, tile_height, gap, margin, header = 225, 365, 18, 24, 98
    per_sheet = columns * rows
    total = math.ceil(len(captures) / per_sheet)
    dup_ids = {item["id"] for group in dupes for item in group["captures"]}
    title_font, label_font, small_font = qa_font(25), qa_font(16), qa_font(13)
    output = []
    for page in range(total):
        batch = captures[page * per_sheet:(page + 1) * per_sheet]
        actual_rows = math.ceil(len(batch) / columns)
        size = (margin * 2 + columns * tile_width + (columns - 1) * gap,
                header + actual_rows * tile_height + (actual_rows - 1) * gap + margin)
        sheet = Image.new("RGB", size, "#0b1018")
        draw = ImageDraw.Draw(sheet)
        draw.text((margin, 18), f"Backdrop Launcher / Capture QA / {page+1:03d} of {total:03d}", fill="#eaf2ff", font=title_font)
        draw.text((margin, 55), "Diagnostic JPEG contact sheet. Sequence + ID identify the unchanged full PNG in INDEX.md.", fill="#a8b7cb", font=small_font)
        for index, item in enumerate(batch):
            x = margin + (index % columns) * (tile_width + gap)
            y = header + (index // columns) * (tile_height + gap)
            draw.rounded_rectangle((x, y, x + tile_width, y + tile_height), radius=10, fill="#182433", outline="#2b3b51")
            image = rgb_copy(item["_path"])
            image.thumbnail((tile_width, image_height), resample)
            sheet.paste(image, (x + (tile_width - image.width) // 2, y + (image_height - image.height) // 2))
            yy = y + image_height + 10
            label = f"{item['sequence']:03d}  {item['id']}"
            if item["id"] in dup_ids:
                label += "  [same PNG bytes]"
            draw.text((x+10, yy), fit_lines(draw, label, small_font, tile_width-20, 1)[0], fill="#83e4d2", font=small_font)
            yy += 20
            for text, font, color, limit in ((item["title"], label_font, "#edf4ff", 2),
                                             ("Route: " + (value_text(item.get("route")) or "not recorded"), small_font, "#a8b7cb", 2),
                                             ("State: " + (value_text(item.get("state")) or "not recorded"), small_font, "#c3cfe0", 1)):
                for line in fit_lines(draw, text, font, tile_width-20, limit):
                    draw.text((x+10, yy), line, fill=color, font=font)
                    yy += 18 if font == label_font else 16
        filename = f"contact-sheets/contact-sheet-{page+1:03d}.jpg"
        sheet.save(stage / filename, "JPEG", quality=88, optimize=True)
        output.append(filename)
    return output


def generate(root, manifest, captures, dupes, warnings, unlisted, snapshot, order, args):
    """Stage derivatives, guard the input snapshot, then publish generated files."""
    snapshot_hash = hashlib.sha256(snapshot).hexdigest()
    for directory in ("thumbnails", "contact-sheets"):
        target = root / directory
        if target.is_symlink() or (target.exists() and not target.is_dir()):
            raise CatalogError(f"Refusing generated output directory {target}")
    with tempfile.TemporaryDirectory(prefix=".guide-catalog-", dir=root) as temporary:
        stage = Path(temporary)
        sheets = derivatives(stage, captures, dupes, args)
        report = {
            "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
            "manifest_sha256": snapshot_hash, "validated_screenshot_count": len(captures),
            "duplicate_hash_group_count": len(dupes), "warnings": warnings,
            "unlisted_pngs_excluded": unlisted,
            "validation_scope": "PNG decodability, manifest dimensions, SHA-256, relative paths, unique IDs/sequences. Does not establish functional coverage or screenshot authenticity.",
            "contact_sheets": sheets,
            "captures": [{key: item[key] for key in ("sequence", "id", "file", "width", "height", "sha256")} for item in captures],
        }
        (stage / "validation-report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
        (stage / "duplicate-hashes.json").write_text(json.dumps({"manifest_sha256": snapshot_hash, "duplicate_groups": dupes,
                                                                 "interpretation": "Identical bytes do not establish different functionality."}, indent=2, ensure_ascii=False) + "\n")
        (stage / "DUPLICATE-HASHES.md").write_text(duplicate_markdown(dupes))
        (stage / "INDEX.md").write_text(index_markdown(manifest, captures, dupes, warnings, unlisted, order, sheets, snapshot_hash))
        (stage / "gallery.html").write_text(gallery_html(captures, dupes, order, snapshot_hash))
        if inside_file(root, "manifest.json", "Manifest").read_bytes() != snapshot:
            raise CatalogError("manifest.json changed during generation; staged output discarded. Finish capture and rerun")
        for item in captures:
            if hashlib.sha256(item["_path"].read_bytes()).hexdigest() != item["sha256"]:
                raise CatalogError(f"{item['id']}: original PNG changed during generation; staged output discarded")
        # Only these generated artifacts are replaced. Manifest and originals are untouched.
        for directory in ("thumbnails", "contact-sheets"):
            (root / directory).mkdir(exist_ok=True)
            for path in sorted((stage / directory).iterdir()):
                os.replace(path, root / directory / path.name)
        for name in ("validation-report.json", "duplicate-hashes.json", "DUPLICATE-HASHES.md", "INDEX.md", "gallery.html"):
            os.replace(stage / name, root / name)
        # Remove stale, tool-owned sheets after a smaller capture set is regenerated.
        current = {Path(name).name for name in sheets}
        for path in (root / "contact-sheets").glob("contact-sheet-*.jpg"):
            if re.fullmatch(r"contact-sheet-\d{3,}\.jpg", path.name) and path.name not in current:
                path.unlink()
    return sheets


def positive_int(value):
    result = int(value)
    if result < 1:
        raise argparse.ArgumentTypeError("must be positive")
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("capture_dir", nargs="?", type=Path,
                        default=Path(__file__).resolve().parents[1] / "docs/user-guide-capture/2026-10-27")
    parser.add_argument("--validate-only", action="store_true", help="validate originals and print duplicate groups; write nothing")
    parser.add_argument("--thumb-width", type=positive_int, default=640, help="maximum thumbnail dimension (default 640)")
    parser.add_argument("--contact-columns", type=positive_int, default=3, help="contact-sheet columns (default 3)")
    parser.add_argument("--contact-rows", type=positive_int, default=4, help="contact-sheet rows (default 4)")
    args = parser.parse_args(argv)
    if args.contact_columns > 8 or args.contact_rows > 12 or args.thumb_width > 4096:
        parser.error("contact sheets support at most 8 columns / 12 rows; thumbnail width at most 4096")
    root = args.capture_dir.expanduser().resolve()
    try:
        manifest, captures, warnings, unlisted, snapshot, order = validate(root)
        dupes = duplicates(captures)
        print(f"Validated {len(captures)} screenshots; {len(dupes)} duplicate SHA-256 group(s).")
        print(f"Manifest SHA-256: {hashlib.sha256(snapshot).hexdigest()}")
        for group in dupes:
            print("Duplicate PNG bytes: " + ", ".join(item["id"] for item in group["captures"]))
        for warning in warnings:
            print(f"Warning: {warning}", file=sys.stderr)
        if args.validate_only:
            print("Validation only; no files written. Identical bytes do not prove different functionality.")
            return 0
        sheets = generate(root, manifest, captures, dupes, warnings, unlisted, snapshot, order, args)
        print(f"Generated INDEX.md, gallery.html, duplicate reports, thumbnails and {len(sheets)} contact sheet(s) in {root}")
        return 0
    except (CatalogError, OSError) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
