import json

import librosa
import numpy as np
import pytest

from naghma_worker import analyze, maqam, synth, tonal
from naghma_worker.main import Worker


@pytest.mark.parametrize(
    "scale,tonic,tonality",
    [
        ("Rast", 0, "ARABIC_MODAL"),
        ("Bayati", 200, "ARABIC_MODAL"),
        ("Saba", 200, "ARABIC_MODAL"),
        ("Sikah", 350, "ARABIC_MODAL"),
        ("Hijaz", 200, "ARABIC_MODAL"),
        ("Nahawand", 0, "WESTERN"),
        ("Kurd", 200, "WESTERN"),
        ("Ajam", 500, "WESTERN"),
    ],
)
def test_maqam_and_tonic_on_synthetic_melody(scale, tonic, tonality):
    for seed in (3, 5, 7):
        y = synth.melody(scale, tonic, seconds=25, seed=seed)
        r = analyze.analyze_signal(y)
        m = r["maqam"]
        assert m["name"] == scale
        assert abs(((m["tonicCents"] - tonic) + 600) % 1200 - 600) <= 20
        assert r["tonality"] == tonality
        assert not m["uncertain"]


def test_melody_that_rarely_leaves_the_tonic_is_flagged_uncertain():
    # seed 11 sits on the tonic ~65% of the time and touches the quarter tone in ~3% of
    # frames: the right maqam still wins, but the result must not claim confidence.
    r = analyze.analyze_signal(synth.melody("Bayati", 200, seconds=25, seed=11))
    assert r["maqam"]["name"] == "Bayati"
    assert r["maqam"]["uncertain"]


def test_western_melody_is_not_called_arabic():
    y = synth.melody("major", 700, seconds=25, seed=2)
    r = analyze.analyze_signal(y)
    assert r["tonality"] == "WESTERN"
    assert r["key"]["camelot"] == "9B"


@pytest.mark.parametrize("pc,mode,camelot", [(0, "major", "8B"), (9, "minor", "8A"), (7, "major", "9B"), (2, "minor", "7A")])
def test_key_detection_on_chords(pc, mode, camelot):
    y = synth.chords(pc, mode)
    k = tonal.detect_key(librosa.feature.chroma_cqt(y=y, sr=synth.SR).mean(axis=1))
    assert (k["tonicPc"], k["mode"], k["camelot"]) == (pc, mode, camelot)


@pytest.mark.parametrize("bpm", [90, 124, 140])
def test_bpm_within_two_percent(bpm):
    r = analyze.analyze_signal(synth.melody("Bayati", 200, seconds=30, bpm=bpm, seed=5))
    assert abs(r["bpm"] - bpm) / bpm < 0.02


def test_energy_curve_shape():
    r = analyze.analyze_signal(synth.melody("Hijaz", 200, seconds=40, seed=1))
    assert r["energyCurve"]
    assert all(0.0 <= s["energy"] <= 1.0 for s in r["energyCurve"])
    assert r["energyCurve"][0]["start"] == 0.0


def test_tuning_offset_is_not_mistaken_for_quarter_tones():
    # a Western melody tuned 25 cents sharp
    cents = np.repeat(np.array([0, 200, 400, 500, 700]) + 25.0, 200)
    assert maqam.quarter_tone_ratio(cents) < 0.05


def test_note_names():
    assert maqam.note_name(0) == "C"
    assert maqam.note_name(350) == "E half-flat"
    assert maqam.note_name(1050) == "B half-flat"
    assert tonal.camelot(0, "major") == "8B"
    assert tonal.camelot(9, "minor") == "8A"


def test_too_little_melody_is_uncertain():
    r = maqam.classify(np.array([0.0, 10.0]))
    assert r.uncertain and r.name == ""


class FakeProducer:
    def __init__(self):
        self.sent = []

    def produce(self, topic, key=None, value=None):
        self.sent.append((topic, key, value))

    def flush(self, timeout=None):
        return 0


def _worker(process):
    w = Worker.__new__(Worker)  # skip Kafka/S3 connections
    w.s = type("S", (), {"results_topic": "analysis.results", "dlq_topic": "analysis.jobs.dlq", "max_attempts": 2})()
    w.producer = FakeProducer()
    w.process = process
    return w


def test_worker_publishes_failed_result_and_dlq_after_retries(monkeypatch):
    monkeypatch.setattr("naghma_worker.main.time.sleep", lambda s: None)
    calls = []

    def boom(job):
        calls.append(job)
        raise RuntimeError("decoder error")

    w = _worker(boom)
    w.handle(json.dumps({"jobId": "j1", "trackId": "t1", "bucket": "b", "objectKey": "k.mp3"}).encode())
    assert len(calls) == 2
    topics = [t for t, _, _ in w.producer.sent]
    assert topics == ["analysis.results", "analysis.jobs.dlq"]
    assert json.loads(w.producer.sent[0][2])["status"] == "FAILED"


def test_worker_sends_malformed_job_to_dlq():
    w = _worker(lambda job: {})
    w.handle(b"not json")
    assert [t for t, _, _ in w.producer.sent] == ["analysis.jobs.dlq"]


def test_maqam_journey_follows_a_modulation():
    # 60 s in Ajam on F, then 60 s in Sikah on E half-flat: the journey should show both,
    # in order, at about the right place.
    y = np.concatenate([
        synth.melody("Ajam", 500, seconds=60, seed=3),
        synth.melody("Sikah", 350, seconds=60, seed=5),
    ])
    journey = analyze.analyze_signal(y)["maqamTimeline"]
    names = [s["maqam"] for s in journey]
    assert names == ["Ajam", "Sikah"], journey
    assert journey[0]["start"] == 0.0 and journey[1]["start"] == 60.0
    assert journey[1]["tonicNote"] == "E half-flat"
    assert journey[1]["arabic"] == "سيكاه"


def test_journey_marks_windows_without_melody():
    t = np.arange(int(30 * synth.SR)) / synth.SR
    clicks = (np.sin(2 * np.pi * 1800 * t) * (np.mod(t, 0.5) < 0.02)).astype(np.float32) * 0.5
    y = np.concatenate([synth.melody("Hijaz", 200, seconds=30, seed=3), clicks])
    journey = analyze.analyze_signal(y)["maqamTimeline"]
    assert journey[0]["maqam"] == "Hijaz"
    assert journey[-1]["maqam"] is None


def test_short_tail_is_folded_into_the_last_window():
    times = np.linspace(0, 64, 5000)
    cents = np.full_like(times, 200.0)
    from naghma_worker import maqam as m
    windows = [round(w["end"] - w["start"]) for w in m.timeline(times, cents, np.full_like(times, 293.0), 64.0)]
    assert sum(windows) == 64 and len(windows) <= 2


@pytest.mark.parametrize("vibrato", [30, 50, 70])
def test_pop_vibrato_is_not_mistaken_for_quarter_tones(vibrato):
    # A belting pop voice swings ±40-80 cents. Before smoothing, only the turning points of
    # the swing looked "stable", half a semitone off the note: a D minor ballad came out as
    # Saba and most of the song as "no melody".
    y = synth.melody("minor", 200, seconds=60, seed=4, vibrato_cents=vibrato)
    out = analyze.analyze_signal(y)
    assert out["tonality"] == "WESTERN"
    assert out["maqam"]["quarterToneRatio"] < 0.05
    assert all(s["maqam"] for s in out["maqamTimeline"]), out["maqamTimeline"]


@pytest.mark.parametrize("name,tonic", [("Bayati", 200), ("Sikah", 350), ("Rast", 0)])
def test_quarter_tones_survive_heavy_vibrato(name, tonic):
    y = synth.melody(name, tonic, seconds=40, seed=4, vibrato_cents=50)
    out = analyze.analyze_signal(y)
    assert out["tonality"] == "ARABIC_MODAL"
    assert out["maqam"]["name"] == name
