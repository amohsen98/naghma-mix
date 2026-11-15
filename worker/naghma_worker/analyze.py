"""Full per-track analysis: rhythm, loudness, energy, Western key and maqam."""
from __future__ import annotations

import logging
import time
from contextlib import contextmanager

import librosa
import numpy as np
import pyloudnorm

from . import maqam, tonal

log = logging.getLogger(__name__)

SR = 22050
MAQAM_EXCERPT_S = 90.0
MAQAM_MAX_TRACK_S = 480.0  # pitch-track at most 8 minutes for the maqam journey
HOP = 512
BEATS_PER_SEGMENT = 32  # 8 bars of 4/4
ANALYZER_VERSION = "naghma-analyzer-0.2"


class Timer:
    def __init__(self) -> None:
        self.ms: dict[str, int] = {}

    @contextmanager
    def stage(self, name: str):
        t0 = time.perf_counter()
        try:
            yield
        finally:
            self.ms[name] = int((time.perf_counter() - t0) * 1000)


def load_audio(path: str) -> np.ndarray:
    y, _ = librosa.load(path, sr=SR, mono=True)
    if y.size == 0:
        raise ValueError("empty or undecodable audio")
    return y


def analyze_file(path: str) -> dict:
    timer = Timer()
    with timer.stage("load"):
        y = load_audio(path)
    result = analyze_signal(y, timer)
    result["timingsMs"] = timer.ms
    return result


def analyze_signal(y: np.ndarray, timer: Timer | None = None) -> dict:
    timer = timer or Timer()
    duration = float(len(y) / SR)

    with timer.stage("rhythm"):
        tempo, beat_frames = librosa.beat.beat_track(y=y, sr=SR)
        beat_times = librosa.frames_to_time(beat_frames, sr=SR)
        bpm = _refine_bpm(float(np.atleast_1d(tempo)[0]), beat_times)

    with timer.stage("loudness"):
        lufs = _lufs(y)

    with timer.stage("energy"):
        curve = energy_curve(y, beat_times, duration)

    with timer.stage("separation"):
        y_harm = isolate_melody(y)

    with timer.stage("key"):
        chroma = librosa.feature.chroma_cqt(y=y_harm, sr=SR)
        key = tonal.detect_key(chroma.mean(axis=1))

    with timer.stage("maqam"):
        times, cents, hz = melody_track(y_harm[: int(MAQAM_MAX_TRACK_S * SR)])
        # The overall label still comes from the middle of the song, where the main
        # maqam usually sits; the journey covers the whole track.
        mid_start = max(0.0, (duration - MAQAM_EXCERPT_S) / 2)
        mid = (times >= mid_start) & (times < mid_start + MAQAM_EXCERPT_S)
        mq = maqam.classify(cents[mid], float(np.median(hz[mid])) if mid.any() else None)
        journey = maqam.timeline(times, cents, hz, min(duration, MAQAM_MAX_TRACK_S))

    return {
        "analyzerVersion": ANALYZER_VERSION,
        "modelVersion": maqam.MODEL_VERSION,
        "durationSec": round(duration, 2),
        "bpm": round(_normalize_bpm(bpm), 2),
        "beatTimes": [round(float(t), 3) for t in beat_times],
        "lufs": None if lufs is None else round(lufs, 2),
        "energy": round(float(np.mean([s["energy"] for s in curve])) if curve else 0.0, 3),
        "energyCurve": curve,
        "tonality": mq.tonality,
        "key": key,
        "maqam": mq.to_dict(),
        "pitchHistogram": mq.histogram,
        "maqamTimeline": journey,
    }


def isolate_melody(y: np.ndarray) -> np.ndarray:
    """Vocals via Demucs when installed (better on full mixes), else harmonic separation."""
    try:
        from .separation import demucs_vocals  # optional, heavy dependency

        vocals = demucs_vocals(y, SR)
        if vocals is not None:
            return vocals
    except ImportError:
        pass
    return librosa.effects.harmonic(y, margin=2.0)


def melody_track(y: np.ndarray) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Confidently voiced melody frames: (times in s, pitch in cents re C4, pitch in Hz)."""
    f0, voiced, prob = librosa.pyin(
        y, fmin=80.0, fmax=1000.0, sr=SR, frame_length=2048, hop_length=HOP
    )
    times = librosa.times_like(f0, sr=SR, hop_length=HOP)
    mask = voiced & np.isfinite(f0) & (prob > 0.3)
    cents = np.full(f0.shape, np.nan)
    cents[mask] = maqam.hz_to_cents(f0[mask])
    cents = maqam.smooth_vibrato(cents)
    return times[mask], cents[mask], f0[mask]


def melody_cents(y: np.ndarray) -> tuple[np.ndarray, float | None]:
    _, cents, hz = melody_track(y)
    if hz.size == 0:
        return np.array([]), None
    return cents, float(np.median(hz))


def energy_curve(y: np.ndarray, beat_times: np.ndarray, duration: float) -> list[dict]:
    """Energy 0..1 per 8-bar segment (fixed 16 s windows when no beat grid)."""
    if len(beat_times) > BEATS_PER_SEGMENT:
        bounds = list(beat_times[::BEATS_PER_SEGMENT])
    else:
        bounds = list(np.arange(0.0, duration, 16.0))
    if not bounds or bounds[0] > 0.5:
        bounds = [0.0] + bounds
    bounds.append(duration)

    hop = 512
    rms = librosa.feature.rms(y=y, hop_length=hop)[0]
    onset = librosa.onset.onset_strength(y=y, sr=SR, hop_length=hop)
    centroid = librosa.feature.spectral_centroid(y=y, sr=SR, hop_length=hop)[0]
    times = librosa.frames_to_time(np.arange(len(rms)), sr=SR, hop_length=hop)

    curve = []
    for start, end in zip(bounds[:-1], bounds[1:]):
        if end - start < 1.0:
            continue
        sel = (times >= start) & (times < end)
        if not sel.any():
            continue
        db = 20 * np.log10(np.mean(rms[sel]) + 1e-9)
        loud = np.clip((db + 35.0) / 30.0, 0, 1)
        n = min(len(onset), len(sel))
        punch = np.clip(np.mean(onset[:n][sel[:n]]) / 3.0, 0, 1)
        bright = np.clip(np.mean(centroid[sel]) / 4000.0, 0, 1)
        energy = 0.6 * loud + 0.25 * punch + 0.15 * bright
        curve.append({"start": round(float(start), 2), "end": round(float(end), 2), "energy": round(float(energy), 3)})
    return curve


def _refine_bpm(estimate: float, beat_times: np.ndarray) -> float:
    """Refine the quantized tempogram estimate from the beat grid.

    Single beat intervals are quantized to the analysis hop (~23 ms), so the mean of the
    intervals near the median is used: the quantization errors average out.
    """
    if len(beat_times) < 9:
        return estimate
    ibi = np.diff(beat_times)
    med = np.median(ibi)
    ibi = ibi[np.abs(ibi - med) < 0.2 * med]
    if ibi.size < 8:
        return estimate
    return float(60.0 / np.mean(ibi))


def _normalize_bpm(bpm: float) -> float:
    """Fold tempo into the 70-180 range DJs read (half/double-time errors are common)."""
    if bpm <= 0:
        return 0.0
    while bpm < 70:
        bpm *= 2
    while bpm > 180:
        bpm /= 2
    return bpm


def _lufs(y: np.ndarray) -> float | None:
    if len(y) < SR:  # pyloudnorm needs > 0.4 s; keep a margin
        return None
    value = pyloudnorm.Meter(SR).integrated_loudness(y)
    return float(value) if np.isfinite(value) else None


def _middle_excerpt(y: np.ndarray, seconds: float) -> np.ndarray:
    n = int(seconds * SR)
    if len(y) <= n:
        return y
    start = (len(y) - n) // 2
    return y[start : start + n]
