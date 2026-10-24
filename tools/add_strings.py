#!/usr/bin/env python3
"""Appends string pairs to values/ and values-zh-rCN/. Usage: add_strings.py pairs.tsv
Each line: key<TAB>english<TAB>chinese. Existing keys are replaced."""
import re
import sys
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app/src/main/res"


def esc(text: str) -> str:
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace("'", "\\'").replace('"', '\\"')
    if text[:1] in ("@", "?"):
        text = "\\" + text
    return text


def apply(path: Path, entries):
    s = path.read_text(encoding="utf-8")
    for key, value in entries:
        s = re.sub(rf'\n\s*<string name="{key}">.*?</string>', "", s, flags=re.S)
    block = "".join(f'    <string name="{k}">{esc(v)}</string>\n' for k, v in entries)
    s = s.replace("</resources>", block + "</resources>")
    path.write_text(s, encoding="utf-8")


def main():
    rows = [l.rstrip("\n").split("\t") for l in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")]
    for r in rows:
        if len(r) != 3:
            sys.exit(f"bad row: {r}")
    apply(RES / "values/strings.xml", [(k, en) for k, en, _ in rows])
    apply(RES / "values-zh-rCN/strings.xml", [(k, zh) for k, _, zh in rows])
    print(f"{len(rows)} strings written")


if __name__ == "__main__":
    main()
