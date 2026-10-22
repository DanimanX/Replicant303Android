#!/usr/bin/env python3
"""Check the Play listing copy against Play's character limits.

Parses the fenced code blocks in store/listing/*.md, keyed by the heading above
each block, and fails when a field is missing or too long:

  App name / Nombre de la app                 30 characters
  Short description / Descripción corta       80 characters
  Full description / Descripción completa   4000 characters

Usage: python3 tools/check_store_listing.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LISTINGS = ROOT / "store" / "listing"

# heading keyword -> (character limit, required)
FIELDS = {
    "app name": (30, True),
    "nombre de la app": (30, True),
    "short description": (80, True),
    "descripción corta": (80, True),
    "full description": (4000, True),
    "descripción completa": (4000, True),
}

HEADING = re.compile(r"^##\s+(.+?)\s*$", re.M)
FENCE = re.compile(r"```[a-zA-Z]*\n(.*?)```", re.S)


def fields_in(text: str) -> dict[str, str]:
    """Map each heading to the first fenced block that follows it."""
    found: dict[str, str] = {}
    headings = list(HEADING.finditer(text))
    for index, match in enumerate(headings):
        heading = match.group(1).lower()
        end = headings[index + 1].start() if index + 1 < len(headings) else len(text)
        body = text[match.end():end]
        fence = FENCE.search(body)
        if not fence:
            continue
        for keyword in FIELDS:
            if keyword in heading:
                found[keyword] = fence.group(1).strip()
    return found


def main() -> int:
    problems = []
    checked = 0
    for path in sorted(LISTINGS.glob("*.md")):
        values = fields_in(path.read_text(encoding="utf-8"))
        for keyword, (limit, required) in FIELDS.items():
            if keyword not in values:
                continue
            value = values[keyword]
            checked += 1
            length = len(value)
            status = "ok" if length <= limit else "TOO LONG"
            print(f"{path.name:12} {keyword:24} {length:5}/{limit}  {status}")
            if length > limit:
                problems.append(f"{path.name}: {keyword} is {length} chars, limit {limit}")
        # Every listing must carry the three core fields in its own language.
        if not any(k in values for k in ("app name", "nombre de la app")):
            problems.append(f"{path.name}: no app name field")
        if not any(k in values for k in ("short description", "descripción corta")):
            problems.append(f"{path.name}: no short description field")
        if not any(k in values for k in ("full description", "descripción completa")):
            problems.append(f"{path.name}: no full description field")

    if checked == 0:
        print("No listing files found under store/listing/.", file=sys.stderr)
        return 1
    if problems:
        print("\nLISTING PROBLEMS:", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1
    print("\nAll listing fields are within Play's limits.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
