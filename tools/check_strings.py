#!/usr/bin/env python3
"""Fails when the English and Simplified Chinese string sets drift apart."""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app/src/main/res"


def keys(path: Path) -> dict:
    root = ET.parse(path).getroot()
    out = {}
    for el in root:
        if el.tag in ("string", "plurals", "string-array") and el.get("translatable") != "false":
            out[f"{el.tag}:{el.get('name')}"] = el
    return out


def placeholders(el) -> set:
    import re
    text = "".join(el.itertext())
    return set(re.findall(r"%\d+\$[sd]|%[sd]", text))


def main() -> int:
    en = keys(RES / "values/strings.xml")
    zh = keys(RES / "values-zh-rCN/strings.xml")
    ok = True
    for k in sorted(set(en) - set(zh)):
        print(f"missing in zh-rCN: {k}"); ok = False
    for k in sorted(set(zh) - set(en)):
        print(f"missing in values: {k}"); ok = False
    for k in sorted(set(en) & set(zh)):
        if en[k].tag == "string" and placeholders(en[k]) != placeholders(zh[k]):
            print(f"placeholder mismatch: {k} {placeholders(en[k])} vs {placeholders(zh[k])}"); ok = False
    print(f"{len(en)} keys checked" if ok else "string check failed")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
