"""Western key detection (Krumhansl-Kessler profiles on chroma) and Camelot notation."""
from __future__ import annotations

import numpy as np

# Krumhansl & Kessler (1982) probe-tone profiles
MAJOR_PROFILE = np.array([6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88])
MINOR_PROFILE = np.array([6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17])

PITCH_NAMES = ["C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"]

# Camelot number by tonic pitch class
CAMELOT_MAJOR = {0: 8, 7: 9, 2: 10, 9: 11, 4: 12, 11: 1, 6: 2, 1: 3, 8: 4, 3: 5, 10: 6, 5: 7}
CAMELOT_MINOR = {9: 8, 4: 9, 11: 10, 6: 11, 1: 12, 8: 1, 3: 2, 10: 3, 5: 4, 0: 5, 7: 6, 2: 7}


def camelot(tonic_pc: int, mode: str) -> str:
    if mode == "major":
        return f"{CAMELOT_MAJOR[tonic_pc % 12]}B"
    return f"{CAMELOT_MINOR[tonic_pc % 12]}A"


def detect_key(chroma_mean: np.ndarray) -> dict:
    """Return {tonic, tonicPc, mode, camelot, confidence} from a 12-bin mean chroma vector."""
    chroma = np.asarray(chroma_mean, dtype=float)
    if chroma.sum() <= 0:
        return {"tonic": "", "tonicPc": None, "mode": "", "camelot": "", "confidence": 0.0}
    scores = []
    for pc in range(12):
        scores.append((np.corrcoef(chroma, np.roll(MAJOR_PROFILE, pc))[0, 1], pc, "major"))
        scores.append((np.corrcoef(chroma, np.roll(MINOR_PROFILE, pc))[0, 1], pc, "minor"))
    scores.sort(key=lambda s: -s[0])
    r = np.array([s[0] for s in scores])
    e = np.exp((r - r.max()) * 20.0)
    conf = float(e[0] / e.sum())
    best_r, pc, mode = scores[0]
    return {
        "tonic": PITCH_NAMES[pc],
        "tonicPc": pc,
        "mode": mode,
        "camelot": camelot(pc, mode),
        "correlation": round(float(best_r), 3),
        "confidence": round(conf, 3),
    }
