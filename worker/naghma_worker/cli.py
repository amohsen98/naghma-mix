"""Analyze local files without Kafka or S3.

    python -m naghma_worker.cli analyze song1.mp3 song2.wav
    python -m naghma_worker.cli analyze --compact folder/*.mp3
"""
from __future__ import annotations

import argparse
import json
import sys

from .analyze import analyze_file


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="naghma-analyze")
    sub = p.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("analyze", help="analyze audio files and print JSON")
    a.add_argument("files", nargs="+")
    a.add_argument("--compact", action="store_true", help="one summary line per file")
    args = p.parse_args(argv)

    status = 0
    for path in args.files:
        try:
            r = analyze_file(path)
        except Exception as e:  # keep going through a folder
            print(f"{path}\tERROR\t{e}", file=sys.stderr)
            status = 1
            continue
        if args.compact:
            m = r["maqam"]
            print(
                f"{path}\t{r['bpm']:.1f} BPM\t{r['key']['camelot']} ({r['key']['tonic']} {r['key']['mode']})"
                f"\t{r['tonality']}\t{m['name']} on {m['tonicNote']} ({m['confidence']:.2f})"
                f"{' uncertain' if m['uncertain'] else ''}\tenergy {r['energy']:.2f}"
            )
        else:
            r.pop("beatTimes", None)
            print(json.dumps({"file": path, **r}, ensure_ascii=False, indent=2))
    return status


if __name__ == "__main__":
    sys.exit(main())
