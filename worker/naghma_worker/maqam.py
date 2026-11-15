"""Maqam and tonic detection from a melody pitch track.

Method (template matching, v0):
  1. Isolate the melody (Demucs vocals if installed, otherwise harmonic/percussive separation).
  2. Track f0 with pYIN and convert voiced frames to cents relative to C.
  3. Keep stable frames only (glides and vibrato blur the scale).
  4. Build a circular pitch-class histogram with 10-cent bins and light smoothing.
  5. Correlate the histogram with every maqam template at every tonic rotation (120 x 10 cents).
  6. The best rotation gives the tonic; the scores give top-3 maqamat with softmax confidence.

Scales are written in cents above the tonic using the common 24-tone approximation
(Maqam World / Inside Arabic Music conventions). Real intonation varies by region and
singer; these templates are a baseline to be replaced by a trained classifier once a
labelled dataset exists.
"""
from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np

BIN_CENTS = 10
N_BINS = 1200 // BIN_CENTS
C4_HZ = 440.0 * 2 ** (-9 / 12)  # pitch class 0 = C

MODEL_VERSION = "maqam-templates-0.1"
QUARTER_TONE_MIN = 0.06  # share of stable pitch mass off the 12-tone grid


@dataclass(frozen=True)
class Scale:
    name: str
    arabic: str
    cents: tuple[int, ...]
    lower_jins: str
    upper_jins: str
    quarter_tone: bool
    western: bool = False
    # Relative weight per degree; tonic and fifth/fourth are heard more often.
    weights: tuple[float, ...] = field(default=())

    def weight(self, i: int) -> float:
        if self.weights:
            return self.weights[i]
        return 1.6 if i == 0 else 1.0


MAQAMAT: dict[str, Scale] = {
    s.name: s
    for s in [
        Scale("Rast", "راست", (0, 200, 350, 500, 700, 900, 1050), "Rast", "Rast", True),
        Scale("Bayati", "بياتي", (0, 150, 300, 500, 700, 800, 1000), "Bayati", "Nahawand", True),
        Scale("Saba", "صبا", (0, 150, 300, 400, 700, 800, 1000), "Saba", "Hijaz", True),
        Scale("Sikah", "سيكاه", (0, 150, 350, 550, 700, 850, 1050), "Sikah", "Rast", True),
        Scale("Hijaz", "حجاز", (0, 100, 400, 500, 700, 800, 1000), "Hijaz", "Nahawand", False),
        Scale("Nahawand", "نهاوند", (0, 200, 300, 500, 700, 800, 1100), "Nahawand", "Hijaz", False),
        Scale("Kurd", "كرد", (0, 100, 300, 500, 700, 800, 1000), "Kurd", "Nahawand", False),
        Scale("Ajam", "عجم", (0, 200, 400, 500, 700, 900, 1100), "Ajam", "Ajam", False),
    ]
}

WESTERN: dict[str, Scale] = {
    "major": Scale("major", "ماجير", (0, 200, 400, 500, 700, 900, 1100), "-", "-", False, True),
    "minor": Scale("minor", "مينير", (0, 200, 300, 500, 700, 800, 1000), "-", "-", False, True),
}

SEMITONE_NAMES = ["C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"]


@dataclass
class MaqamResult:
    tonality: str  # "ARABIC_MODAL" | "WESTERN"
    name: str
    confidence: float
    uncertain: bool
    candidates: list[dict]
    tonic_cents: float  # pitch class, 0 = C
    tonic_hz: float
    tonic_note: str
    quarter_tone_ratio: float
    histogram: list[float]
    western_best: dict

    def to_dict(self) -> dict:
        return {
            "tonality": self.tonality,
            "name": self.name,
            "confidence": round(self.confidence, 3),
            "uncertain": self.uncertain,
            "candidates": self.candidates,
            "tonicCents": round(self.tonic_cents, 1),
            "tonicHz": round(self.tonic_hz, 2),
            "tonicNote": self.tonic_note,
            "quarterToneRatio": round(self.quarter_tone_ratio, 3),
            "westernBest": self.western_best,
            "modelVersion": MODEL_VERSION,
        }


def note_name(pc_cents: float) -> str:
    """Name a pitch class on the 24-tone grid, e.g. 350 -> 'E half-flat'."""
    q = int(round(pc_cents / 50.0)) % 24
    if q % 2 == 0:
        return SEMITONE_NAMES[q // 2]
    upper = SEMITONE_NAMES[((q + 1) // 2) % 12]
    return f"{upper} half-flat"


def hz_to_cents(f0: np.ndarray) -> np.ndarray:
    return 1200.0 * np.log2(f0 / C4_HZ)


def stable_frames(cents: np.ndarray, window: int = 5, max_spread: float = 30.0) -> np.ndarray:
    """Mask of frames whose pitch stays within max_spread cents over a short window."""
    if cents.size < window:
        return np.ones_like(cents, dtype=bool)
    pad = window // 2
    padded = np.pad(cents, pad, mode="edge")
    views = np.lib.stride_tricks.sliding_window_view(padded, window)
    spread = views.max(axis=1) - views.min(axis=1)
    return spread <= max_spread


def pitch_class_histogram(cents: np.ndarray, smooth_cents: float = 15.0) -> np.ndarray:
    pc = np.mod(cents, 1200.0)
    idx = np.round(pc / BIN_CENTS).astype(int) % N_BINS  # bin k is centred on k*10 cents
    hist = np.bincount(idx, minlength=N_BINS).astype(float)
    hist = _circular_smooth(hist, smooth_cents)
    total = hist.sum()
    return hist / total if total > 0 else hist


def _circular_smooth(hist: np.ndarray, sigma_cents: float) -> np.ndarray:
    sigma = sigma_cents / BIN_CENTS
    offsets = np.arange(-N_BINS // 2, N_BINS // 2)
    kernel = np.exp(-0.5 * (offsets / sigma) ** 2)
    kernel /= kernel.sum()
    # circular convolution via FFT
    return np.real(np.fft.ifft(np.fft.fft(hist) * np.fft.fft(np.fft.ifftshift(kernel))))


def template_vector(scale: Scale, sigma_cents: float = 20.0) -> np.ndarray:
    """Template with the tonic at bin 0."""
    v = np.zeros(N_BINS)
    centers = np.arange(N_BINS) * BIN_CENTS
    for i, c in enumerate(scale.cents):
        d = np.abs(((centers - c) + 600) % 1200 - 600)
        v += scale.weight(i) * np.exp(-0.5 * (d / sigma_cents) ** 2)
    return v / np.linalg.norm(v)


_TEMPLATES = {name: template_vector(s) for name, s in {**MAQAMAT, **WESTERN}.items()}


def rotation_scores(hist: np.ndarray, template: np.ndarray) -> np.ndarray:
    """Cosine similarity of hist with the template rotated to every tonic bin."""
    norm = np.linalg.norm(hist)
    if norm == 0:
        return np.zeros(N_BINS)
    # score[s] = sum_k hist[k] * template[k - s]  (circular cross-correlation)
    scores = np.real(np.fft.ifft(np.fft.fft(hist) * np.conj(np.fft.fft(template))))
    return scores / norm


def quarter_tone_ratio(cents: np.ndarray) -> float:
    """Share of stable pitch mass that sits about a quarter tone off the 12-tone grid.

    The grid is first aligned to the recording's own tuning, so a song tuned 20 cents sharp
    is not mistaken for a quarter-tone maqam. The tuning is estimated with a 50-cent period,
    which maps semitones and quarter tones to the same point; otherwise a melody rich in
    quarter tones would drag the estimate halfway between the two and hide them. Of the two
    resulting alignments, the one that puts most notes on the semitone grid wins (every
    maqam keeps most of its degrees on semitones).
    """
    if cents.size == 0:
        return 0.0
    angle = np.mod(cents, 50.0) / 50.0 * 2 * np.pi
    off = np.angle(np.mean(np.exp(1j * angle))) / (2 * np.pi) * 50.0
    best_aligned, best_on_grid = None, -1.0
    for offset in (off, off + 50.0):
        aligned = np.mod(cents - offset + 50.0, 100.0) - 50.0  # [-50, 50)
        on_grid = float(np.mean(np.abs(aligned) < 20.0))
        if on_grid > best_on_grid:
            best_aligned, best_on_grid = aligned, on_grid
    return float(np.mean(np.abs(best_aligned) >= 30.0))


def classify(cents: np.ndarray, median_hz: float | None = None) -> MaqamResult:
    """Classify a melody (voiced pitch in cents re C4) into maqam/tonic."""
    stable = cents[stable_frames(cents)] if cents.size else cents
    if stable.size < 50:
        return _empty_result()

    hist = pitch_class_histogram(stable)
    qt = quarter_tone_ratio(stable)

    best: dict[str, tuple[float, int]] = {}
    for name, tmpl in _TEMPLATES.items():
        s = rotation_scores(hist, tmpl)
        k = int(np.argmax(s))
        best[name] = (float(s[k]), k)

    maqam_names = list(MAQAMAT)
    maqam_scores = np.array([best[n][0] for n in maqam_names])
    probs = _softmax(maqam_scores * 40.0)
    order = np.argsort(-probs)
    top = maqam_names[order[0]]
    top_score, top_bin = best[top]

    w_name = max(WESTERN, key=lambda n: best[n][0])
    w_score, w_bin = best[w_name]

    distinctive = top in {"Hijaz", "Saba"} and top_score > w_score + 0.01
    # Rast has only 2 of 7 degrees on quarter tones, so even a clear Rast melody shows a
    # modest ratio; Western recordings aligned to their own tuning sit near zero.
    quarter_toned = MAQAMAT[top].quarter_tone and qt >= QUARTER_TONE_MIN and top_score >= 0.9 * w_score
    tonality = "ARABIC_MODAL" if (distinctive or quarter_toned) else "WESTERN"

    tonic_pc = top_bin * BIN_CENTS
    tonic_hz = _tonic_hz(tonic_pc, median_hz)
    candidates = [
        {"name": maqam_names[i], "arabic": MAQAMAT[maqam_names[i]].arabic, "confidence": round(float(probs[i]), 3)}
        for i in order[:3]
    ]
    confidence = float(probs[order[0]])
    return MaqamResult(
        tonality=tonality,
        name=top,
        confidence=confidence,
        uncertain=confidence < 0.45 or top_score < 0.55,
        candidates=candidates,
        tonic_cents=float(tonic_pc),
        tonic_hz=tonic_hz,
        tonic_note=note_name(tonic_pc),
        quarter_tone_ratio=qt,
        histogram=[round(float(x), 5) for x in hist],
        western_best={
            "mode": w_name,
            "tonicNote": note_name(w_bin * BIN_CENTS),
            "score": round(w_score, 3),
        },
    )


def _tonic_hz(tonic_pc: float, median_hz: float | None) -> float:
    hz = C4_HZ * 2 ** (tonic_pc / 1200.0)
    if median_hz:
        # place the tonic in the octave at or just below the melody's median pitch
        while hz > median_hz:
            hz /= 2
        while hz * 2 <= median_hz:
            hz *= 2
    return float(hz)


def _softmax(x: np.ndarray) -> np.ndarray:
    e = np.exp(x - x.max())
    return e / e.sum()


def _empty_result() -> MaqamResult:
    return MaqamResult(
        tonality="WESTERN",
        name="",
        confidence=0.0,
        uncertain=True,
        candidates=[],
        tonic_cents=0.0,
        tonic_hz=0.0,
        tonic_note="",
        quarter_tone_ratio=0.0,
        histogram=[],
        western_best={},
    )
