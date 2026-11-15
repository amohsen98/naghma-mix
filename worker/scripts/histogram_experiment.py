"""Gate 1 experiment: do songs in different maqamat separate on their pitch histograms?

Put songs you have labelled in a CSV (path,maqam,tonic_note) and run:

    python scripts/histogram_experiment.py labels.csv --out report.html

The report shows, per song, the predicted maqam and tonic next to your label, an overall
accuracy, a confusion table, and each song's pitch-class histogram drawn relative to the
labelled tonic, so you can see whether Rast and Bayati really look different.
"""
from __future__ import annotations

import argparse
import csv
import html
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from naghma_worker.analyze import analyze_file  # noqa: E402

NOTE_CENTS = {
    "C": 0, "Db": 100, "D": 200, "Eb": 300, "E": 400, "F": 500, "Gb": 600, "G": 700,
    "Ab": 800, "A": 900, "Bb": 1000, "B": 1100,
}


def tonic_cents(note: str) -> float | None:
    note = note.strip()
    if not note:
        return None
    if note.endswith("half-flat"):
        base = note.replace("half-flat", "").strip()
        return (NOTE_CENTS[base] - 50) % 1200
    return NOTE_CENTS.get(note)


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("labels", help="CSV with columns path,maqam,tonic_note")
    p.add_argument("--out", default="maqam_report.html")
    args = p.parse_args()

    rows = list(csv.DictReader(open(args.labels, encoding="utf-8")))
    results = []
    for row in rows:
        try:
            r = analyze_file(row["path"])
        except Exception as e:  # noqa: BLE001
            print(f"skip {row['path']}: {e}", file=sys.stderr)
            continue
        m = r["maqam"]
        results.append({**row, "pred": m["name"], "pred_tonic": m["tonicNote"], "conf": m["confidence"],
                        "pred_tonic_cents": m["tonicCents"], "hist": r["pitchHistogram"]})
        print(f"{row['maqam']:>9} -> {m['name']:<9} ({m['confidence']:.2f})  {row['path']}")

    if not results:
        print("no songs analyzed", file=sys.stderr)
        return 1

    correct = sum(r["pred"].lower() == r["maqam"].strip().lower() for r in results)
    confusion: dict[str, Counter] = defaultdict(Counter)
    for r in results:
        confusion[r["maqam"].strip()][r["pred"]] += 1
    print(f"\nTop-1 accuracy: {correct}/{len(results)} = {correct / len(results):.0%}")

    Path(args.out).write_text(render(results, confusion, correct), encoding="utf-8")
    print(f"Report written to {args.out}")
    return 0


def render(results, confusion, correct) -> str:
    labels = sorted({*confusion, *(p for c in confusion.values() for p in c)})
    head = "".join(f"<th>{html.escape(l)}</th>" for l in labels)
    body = "".join(
        f"<tr><th>{html.escape(t)}</th>" + "".join(f"<td>{confusion[t].get(p, 0) or ''}</td>" for p in labels) + "</tr>"
        for t in sorted(confusion)
    )
    data = []
    for r in results:
        shift = tonic_cents(r.get("tonic_note", "")) if r.get("tonic_note") else r["pred_tonic_cents"]
        k = int(round((shift or 0) / 10)) % 120
        h = r["hist"][k:] + r["hist"][:k]  # rotate so the labelled tonic is at 0
        data.append({"name": Path(r["path"]).name, "label": r["maqam"], "pred": r["pred"], "h": h})
    return f"""<!doctype html><meta charset="utf-8"><title>Maqam histogram experiment</title>
<style>body{{font:14px system-ui;margin:24px;max-width:1000px}}table{{border-collapse:collapse}}
td,th{{border:1px solid #ccc;padding:4px 8px;text-align:center}}.song{{margin:16px 0}}</style>
<h1>Maqam histogram experiment</h1>
<p>Top-1 accuracy: <b>{correct}/{len(results)} ({correct / len(results):.0%})</b>. Rows = your label, columns = prediction.</p>
<table><tr><th></th>{head}</tr>{body}</table>
<h2>Pitch-class histograms (0 = labelled tonic, ticks every 100 cents)</h2><div id="songs"></div>
<script>
const data = {json.dumps(data)};
for (const s of data) {{
  const w = 960, h = 120, max = Math.max(...s.h);
  const bars = s.h.map((v, i) => `<rect x="${{i * 8}}" y="${{h - v / max * (h - 10)}}" width="7" height="${{v / max * (h - 10)}}" fill="${{s.label === s.pred ? '#3b82f6' : '#ef4444'}}"/>`).join('');
  const ticks = Array.from({{length: 12}}, (_, i) => `<line x1="${{i * 80}}" x2="${{i * 80}}" y1="0" y2="${{h}}" stroke="#ddd"/><text x="${{i * 80 + 2}}" y="12" font-size="10">${{i * 100}}</text>`).join('');
  document.getElementById('songs').insertAdjacentHTML('beforeend',
    `<div class="song"><b>${{s.name}}</b> label ${{s.label}}, predicted ${{s.pred}}<br><svg width="${{w}}" height="${{h}}">${{ticks}}${{bars}}</svg></div>`);
}}
</script>"""


if __name__ == "__main__":
    sys.exit(main())
