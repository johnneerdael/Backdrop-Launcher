# Guide maintenance (not published)

This directory is excluded by `mkdocs.yml`. It contains contributor and evidence metadata rather than a user navigation section.

## Build and preview

Use Python 3.11 or newer:

```sh
python3 -m venv /tmp/backdrop-guide-venv
/tmp/backdrop-guide-venv/bin/python -m pip install -r docs/site-requirements.txt
/tmp/backdrop-guide-venv/bin/mkdocs build --strict
/tmp/backdrop-guide-venv/bin/mkdocs serve --dev-addr 127.0.0.1:8000
```

The generated site is `build/user-guide-site` (ignored). The source includes a `CNAME` for `backdrop-launcher.neerdael.nl`; building it does not configure DNS or publish GitHub Pages.

## Brand and assets

The theme maps the approved Backdrop Launcher kit's dark/light semantic tokens into Material's CSS variables. Header and footer use Night; dark links use #BAA2FF and light links #653CC3. Twilight violet #A78BFA, Afterglow #D9CEF9 and Mist #C4E5E6 remain signature colours. Inter, Space Grotesk and IBM Plex Mono are copied locally with their original OFL notices. `theme.font: false` disables Material's Google Fonts integration. No external JavaScript or font service is added.

Brand assets are copied from `docs/assets/brand` into `assets/brand`. Do not modify the master assets to adjust a web layout. The site uses the mark at small sizes and brand-led typography in the landing page. It does not present the kit's illustrative interface study as the product.

`screenshot-provenance.json` records full-frame WebP derivations and source hashes. Originals remain untouched. The published asset selection is small; do not copy the entire capture archive into this directory. Never include pending authentication QR/code screens.

## Coverage and truthfulness

Content follows current Dart/native source plus the 2026-10-27 draft, its 2026-10-27-fixes follow-up, and the 2026-10-28 feature-removal evidence. Follow-up fixes supersede the original draft's screensaver configuration failure, pointer focus mismatch and generic no-release result. Current user pages omit the removed brightness controls and Wi-Fi usage tracking. The connection indicator and native network-settings shortcut remain documented.

The selected System/Status bar/Home frames are from feature-removal verification. Other selected frames show unchanged controls without retired settings. No historical Settings root/About image is published, because it would carry the former product name. The native Elegant image comes from the curated marketing pack following the screensaver fix. No artwork replacement or synthetic app screen is used.

Current guide coverage includes every visible Applications, Interface and System control; remote navigation and reorder semantics; default-Home dependency; Watch Next permission/filters/feed limit and compatibility; Trakt device login without exposing codes; catalogs/lists and offline fallback; updater conditional results and build-time availability; installed-version/about and package licences. User flows avoid source-code path details. The optional ADB permission step uses confirmed release ID `nl.neerdael.backdrop_launcher`.

## Dependencies

Versions were verified on 2026-10-28 with the primary [MkDocs PyPI listing](https://pypi.org/project/mkdocs/) and [Material for MkDocs PyPI listing](https://pypi.org/project/mkdocs-material/): MkDocs 1.6.1 and Material 9.8.0. The latter requires Python >=3.11. Top-level pins are in `docs/site-requirements.txt`.

Material's current maintainers state an end-of-life date of 2027-05-05 and identify Zensical as their maintained successor. The explicit requested stack is Material, following CueRithm. Review this maintenance deadline before that date; do not silently switch frameworks during a content update.

## Release handoff

The canonical repository is `johnneerdael/Backdrop-Launcher`. The universal latest-download alias is `backdrop-launcher.apk`. Links fall back to the Releases page and explain the first-release-pending case. Do not claim a version is available, an update was installed or Pages was published solely because a local strict build succeeds.

Before publishing, visually inspect the landing page, a screenshot-heavy page, search and navigation in both themes and at mobile width. If replacing the Settings root/About screenshots, capture the actual branded app; do not retouch the old product strings.
