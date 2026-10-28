"""ADB-only capture utility for the authorized documentation TV.

Captures native lossless PNGs and writes their provenance to manifest.json.
No product state is changed by capture(); navigation is explicit in calling scripts.
"""
from pathlib import Path
import datetime
import hashlib
import io
import json
import subprocess
import time
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'docs/user-guide-capture' / datetime.date.today().isoformat()
SERIAL = '192.168.50.89:5555'
PACKAGE = 'nl.neerdael.backdrop_launcher'

class GuideTV:
    def ocr(self, path):
        output = subprocess.check_output(['/tmp/media-guide-ocr', str(path)], timeout=30)
        return json.loads(output)

    def tap_text(self, text, reference=None, occurrence=0):
        manifest = json.loads((OUTPUT / 'manifest.json').read_text())
        item = next((item for item in manifest['screenshots'] if item['id'] == reference), None) if reference else manifest['screenshots'][-1]
        if item is None: raise ValueError('Unknown screenshot reference')
        path = OUTPUT / item['file']
        rows = self.ocr(path)
        target = ' '.join(text.casefold().split())
        matches = [row for row in rows if target in ' '.join(row['text'].casefold().split())]
        if len(matches) <= occurrence: raise ValueError(f'No visible label matching {text!r} in {item["id"]}')
        row = matches[occurrence]
        self.tap((row['x'] + row['width']/2)*1280, (1-row['y']-row['height']/2)*720)

    def shell(self, command, timeout=30):
        return subprocess.check_output(['adb', '-s', SERIAL, 'shell', command], timeout=timeout, text=True)

    def hold_text(self, text, reference=None, duration=800):
        manifest = json.loads((OUTPUT / 'manifest.json').read_text())
        item = next(item for item in manifest['screenshots'] if item['id'] == reference) if reference else manifest['screenshots'][-1]
        target = text.casefold()
        row = next(row for row in self.ocr(OUTPUT / item['file']) if target in row['text'].casefold())
        self.hold((row['x'] + row['width']/2)*1280, (1-row['y']-row['height']/2)*720, duration)

    def key(self, *codes, pause=.4):
        self.shell('; '.join(f'input keyevent {int(code)}; sleep {pause}' for code in codes), timeout=max(30, len(codes)*2))

    def tap(self, x, y):
        # Coordinates use a 1280×720 reference; native capture is 1920×1080.
        self.shell(f'input tap {round(x*1.5)} {round(y*1.5)}')
        time.sleep(.25)

    def hold(self, x, y, duration=800):
        x, y = round(x*1.5), round(y*1.5)
        self.shell(f'input swipe {x} {y} {x} {y} {duration}')
        time.sleep(.25)

    def swipe(self, x1, y1, x2, y2, duration=450):
        self.shell(f'input swipe {round(x1*1.5)} {round(y1*1.5)} {round(x2*1.5)} {round(y2*1.5)} {duration}')
        time.sleep(.25)

    def text(self, value):
        # Guide names are deliberately limited to shell-safe ASCII identifiers.
        if not all(c.isalnum() or c in ' -_' for c in value):
            raise ValueError('Use a simple guide label')
        self.shell("input text '" + value.replace(' ', '%s') + "'")

    def launch(self):
        self.shell(f'am start -W -n {PACKAGE}/.MainActivity')
        time.sleep(2)

    def home(self):
        self.shell(f'am force-stop {PACKAGE}')
        self.launch()

    def settings(self):
        self.tap(42, 38)

    def capture(self, slug, title, route, purpose='', state='', tags=(), notes='', settle=.5, expect=(), trigger_key=None, trigger_tap=None, trigger_delay=.05):
        if not all(c.islower() or c.isdigit() or c == '-' for c in slug):
            raise ValueError('Use a lowercase hyphenated screenshot ID')
        time.sleep(settle)
        manifest_path = OUTPUT / 'manifest.json'
        manifest = json.loads(manifest_path.read_text())
        if any(item['id'] == slug for item in manifest['screenshots']):
            raise ValueError(f'Duplicate screenshot ID: {slug}')
        started = time.monotonic()
        trigger = ''
        if trigger_key is not None or trigger_tap is not None:
            if not 0 <= trigger_delay <= 1:
                raise ValueError('Use a delay between zero and one second')
            if trigger_key is not None and trigger_tap is not None:
                raise ValueError('Choose one native input trigger')
            if trigger_key is not None:
                trigger = f'input keyevent {int(trigger_key)}; '
            else:
                x, y = trigger_tap
                if not 0 <= x <= 1280 or not 0 <= y <= 720:
                    raise ValueError('Tap is outside the capture reference')
                trigger = f'input tap {round(x*1.5)} {round(y*1.5)}; '
            trigger += f'sleep {float(trigger_delay)}; '
        self.shell(trigger + 'screencap -p /data/local/tmp/media-guide-capture.png', timeout=45)
        temporary = Path('/tmp/media-guide-current.png')
        subprocess.run(['adb', '-s', SERIAL, 'pull', '/data/local/tmp/media-guide-capture.png', str(temporary)], check=True, capture_output=True, timeout=45)
        png = temporary.read_bytes()
        with Image.open(io.BytesIO(png)) as image:
            width, height = image.size
            image.verify()
        if (width, height) != (1920, 1080):
            raise RuntimeError(f'Unexpected capture size: {width}×{height}')
        elapsed = round(time.monotonic() - started, 2)
        sequence = len(manifest['screenshots']) + 1
        filename = f'{sequence:03d}-{slug}.png'
        (OUTPUT / 'screenshots' / filename).write_bytes(png)
        rows = self.ocr(OUTPUT / 'screenshots' / filename)
        visible = ' '.join(row['text'].casefold() for row in rows)
        missing = [label for label in expect if label.casefold() not in visible]
        if missing:
            (OUTPUT / 'screenshots' / filename).unlink()
            raise RuntimeError(f'Unexpected screen for {slug}; missing labels: {missing}')
        (OUTPUT / 'ocr').mkdir(exist_ok=True)
        ocr_file = 'ocr/' + filename.replace('.png', '.json')
        (OUTPUT / ocr_file).write_text(json.dumps(rows, indent=2) + '\n')
        manifest['screenshots'].append({
            'sequence': sequence, 'id': slug, 'title': title,
            'file': 'screenshots/' + filename, 'route': route, 'purpose': purpose,
            'state': state, 'tags': list(tags), 'notes': notes,
            'captured_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'width': width, 'height': height, 'sha256': hashlib.sha256(png).hexdigest(),
            'source': 'ADB native screencap; unmodified lossless PNG',
            'capture_seconds': elapsed,
            'ocr_file': ocr_file, 'visible_text': [row['text'] for row in rows],
        })
        manifest['capture_display'] = {'size': '1920x1080', 'density': 320, 'logical_size': '960x540'}
        if trigger_key is not None:
            manifest['screenshots'][-1]['on_device_trigger'] = {'android_keycode': int(trigger_key), 'delay_seconds': trigger_delay}
        elif trigger_tap is not None:
            manifest['screenshots'][-1]['on_device_trigger'] = {'tap_reference_1280x720': list(trigger_tap), 'delay_seconds': trigger_delay}
        manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
        print(f'{sequence:03d} {slug} ({len(png):,} bytes; {elapsed}s)', flush=True)
        return OUTPUT / 'screenshots' / filename

tv = GuideTV()
