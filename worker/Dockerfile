FROM python:3.12-slim

# ffmpeg decodes M4A/AAC; libsndfile (bundled with soundfile) covers WAV, FLAC and MP3
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY naghma_worker ./naghma_worker

# pre-compile numba kernels used by librosa so the first job is not slow
RUN python -c "import numpy as np, librosa; librosa.pyin(np.zeros(22050, dtype=np.float32), fmin=80, fmax=1000)"

RUN useradd --create-home worker
USER worker
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=3s CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8090')"
CMD ["python", "-m", "naghma_worker.main"]
