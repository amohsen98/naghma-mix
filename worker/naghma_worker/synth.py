"""Synthetic test signals: a melody in a given maqam/scale over a click track.

Used by the tests and handy for sanity-checking the analyzer without copyrighted audio.
"""
from __future__ import annotations

import numpy as np

from .maqam import C4_HZ, MAQAMAT, WESTERN

SR = 22050


def tone(freq: float, seconds: float, sr: int = SR, vibrato_cents: float = 8.0) -> np.ndarray:
    t = np.arange(int(seconds * sr)) / sr
    vib = 2 ** (vibrato_cents * np.sin(2 * np.pi * 5.5 * t) / 1200.0)
    phase = 2 * np.pi * np.cumsum(freq * vib) / sr
    y = sum(a * np.sin(k * phase) for k, a in [(1, 1.0), (2, 0.45), (3, 0.25), (4, 0.12)])
    env = np.minimum(1.0, np.minimum(t / 0.02, (seconds - t) / 0.05))
    return (y * env).astype(np.float32)


def melody(
    scale_name: str,
    tonic_cents: float,
    seconds: float = 30.0,
    bpm: float = 120.0,
    seed: int = 7,
    sr: int = SR,
) -> np.ndarray:
    """A stepwise random-walk melody that keeps returning to the tonic, plus clicks on beats."""
    scale = MAQAMAT.get(scale_name) or WESTERN[scale_name]
    degrees = list(scale.cents) + [1200]
    rng = np.random.default_rng(seed)
    beat = 60.0 / bpm
    out = np.zeros(int(seconds * sr), dtype=np.float32)
    pos, i = 0.0, 0
    while pos < seconds - beat:
        dur = beat * rng.choice([0.5, 1.0, 1.0, 2.0])
        if rng.random() < 0.18:
            i = 0  # cadence on the tonic
        else:
            i = int(np.clip(i + rng.choice([-1, 1, 1, -2, 2]), 0, len(degrees) - 1))
        f = C4_HZ * 2 ** ((tonic_cents + degrees[i]) / 1200.0)
        seg = tone(f, min(dur, seconds - pos), sr)
        s = int(pos * sr)
        out[s : s + len(seg)] += 0.3 * seg[: len(out) - s]
        pos += dur
    # click track
    click = np.exp(-np.arange(int(0.03 * sr)) / (0.004 * sr)) * np.sin(
        2 * np.pi * 1800 * np.arange(int(0.03 * sr)) / sr
    )
    for k in range(int(seconds / beat)):
        s = int(k * beat * sr)
        out[s : s + len(click)] += 0.5 * click[: len(out) - s]
    return out / (np.abs(out).max() + 1e-9) * 0.8


def chords(tonic_pc: int, mode: str, seconds: float = 20.0, sr: int = SR) -> np.ndarray:
    """I-IV-V-I triads (major) or i-iv-v-i (minor) for key detection tests."""
    t = 4 if mode == "major" else 3
    progression = [[0, t, 7], [5, 5 + t, 12], [7, 11, 14], [0, t, 7]]
    seg = seconds / len(progression)
    out = []
    for notes in progression:
        y = sum(tone(C4_HZ * 2 ** ((tonic_pc + n) / 12.0) / 2, seg, sr, 0) for n in notes)
        out.append(y)
    y = np.concatenate(out)
    return y / (np.abs(y).max() + 1e-9) * 0.8
