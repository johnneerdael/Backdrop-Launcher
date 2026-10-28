#!/usr/bin/env python3
"""Write ignored Flutter build definitions from local files or Actions secrets."""
import json
import os
from pathlib import Path
import re


def credential(name: str, filename: str, aliases: tuple[str, ...]) -> str:
    value = os.environ.get(name, '').strip()
    if not value and Path(filename).is_file():
        raw = Path(filename).read_text().strip()
        try:
            fields = json.loads(raw)
        except ValueError:
            fields = {}
            for line in raw.splitlines():
                match = re.fullmatch(r'\s*(?:export\s+)?([A-Za-z_][\w]*)\s*[:=]\s*(.*?)\s*', line)
                if match:
                    fields[match[1].upper()] = match[2].strip().strip('\"\'')
            if not fields and len(raw.splitlines()) == 1:
                value = raw
        if isinstance(fields, dict):
            normalized = {key.upper(): val for key, val in fields.items()}
            for key in (name, *aliases):
                if isinstance(normalized.get(key), str):
                    value = normalized[key].strip()
                    break
    if not value or re.search(r'\s', value):
        raise SystemExit(f'Missing or invalid {name}; set the Actions secret or provide {filename}.')
    return value


def main() -> None:
    definitions = {
        'TMDB_API_KEY': credential('TMDB_API_KEY', '.tmdb', ('API', 'API_KEY')),
        'TRAKT_CLIENT_ID': credential('TRAKT_CLIENT_ID', '.trakt', ('CLIENTID', 'CLIENT_ID')),
    }
    if os.environ.get('TRAKT_REDIRECT_URI'):
        definitions['TRAKT_REDIRECT_URI'] = os.environ['TRAKT_REDIRECT_URI']
    output = Path('.dart_tool/trakt-config.json')
    output.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, 'w') as stream:
        json.dump(definitions, stream)
    print(f'Wrote {output} (credential values hidden).')


if __name__ == '__main__':
    main()
