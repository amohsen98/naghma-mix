"""Optional vocal separation with Demucs.

Install with `pip install demucs` (pulls in PyTorch and downloads model weights on first
use). Without it, analysis falls back to harmonic/percussive separation, which works for
sparse arrangements but is weaker on dense modern productions.

Set NAGHMA_DEMUCS=1 to enable it even when installed; it is off by default because it
needs several GB of RAM and is slow on CPU.
"""
from __future__ import annotations

import os

import numpy as np

_separator = None


def demucs_vocals(y: np.ndarray, sr: int) -> np.ndarray | None:
    if os.environ.get("NAGHMA_DEMUCS") != "1":
        return None
    import torch  # noqa: F401  (raises ImportError when not installed)
    import demucs.api  # type: ignore

    global _separator
    if _separator is None:
        _separator = demucs.api.Separator(model="htdemucs")
    wav = torch.tensor(np.stack([y, y]), dtype=torch.float32)
    wav = demucs.api.convert_audio(wav, sr, _separator.samplerate, _separator.audio_channels)
    _, stems = _separator.separate_tensor(wav, _separator.samplerate)
    vocals = stems["vocals"].mean(dim=0)
    vocals = demucs.api.convert_audio(vocals[None], _separator.samplerate, sr, 1)[0]
    return vocals.numpy()
