import { api, ApiError } from './api';

export type FileState =
  | { phase: 'hashing' }
  | { phase: 'uploading'; progress: number }
  | { phase: 'queued'; trackId: string }
  | { phase: 'duplicate'; trackId: string | null }
  | { phase: 'error'; message: string };

export interface UploadItem {
  key: string;
  file: File;
  state: FileState;
}

const MAX_BYTES = 30 * 1024 * 1024;
const ALLOWED = /\.(mp3|wav|flac|m4a)$/i;
const BATCH = 100;
const PARALLEL_PUTS = 3;

export function acceptable(file: File): string | null {
  if (!ALLOWED.test(file.name)) return 'format';
  if (file.size > MAX_BYTES) return 'size';
  return null;
}

export async function sha256Hex(file: File): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('');
}

function put(url: string, file: File, contentType: string, onProgress: (p: number) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url);
    xhr.setRequestHeader('Content-Type', contentType);
    xhr.upload.onprogress = (e) => e.lengthComputable && onProgress(e.loaded / e.total);
    xhr.onload = () => (xhr.status < 300 ? resolve() : reject(new Error(`storage answered ${xhr.status}`)));
    xhr.onerror = () => reject(new Error('network error during upload'));
    xhr.send(file);
  });
}

async function pool<T>(items: T[], size: number, fn: (item: T) => Promise<void>) {
  const queue = [...items];
  await Promise.all(Array.from({ length: Math.min(size, queue.length) }, async () => {
    for (let item = queue.shift(); item !== undefined; item = queue.shift()) await fn(item);
  }));
}

/**
 * Hash → ask for upload slots (duplicates are skipped server-side) → PUT straight to
 * storage → confirm. Reports per-file state through `update`.
 */
export async function uploadFiles(
  items: UploadItem[],
  update: (key: string, state: FileState) => void,
): Promise<{ quotaRemaining: number | null }> {
  let quotaRemaining: number | null = null;
  for (let i = 0; i < items.length; i += BATCH) {
    const batch = items.slice(i, i + BATCH);
    const hashes: string[] = [];
    for (const it of batch) {
      update(it.key, { phase: 'hashing' });
      hashes.push(await sha256Hex(it.file));
    }
    let res;
    try {
      res = await api.startUpload(batch.map((it, k) => ({ filename: it.file.name, sizeBytes: it.file.size, sha256: hashes[k] })));
    } catch (e) {
      const message = e instanceof ApiError ? e.message : String(e);
      batch.forEach((it) => update(it.key, { phase: 'error', message }));
      if (e instanceof ApiError && e.status === 402) throw e;
      continue;
    }
    quotaRemaining = res.quotaRemaining;
    const toConfirm: string[] = [];
    const work: Array<{ item: UploadItem; url: string; contentType: string; trackId: string }> = [];
    batch.forEach((it, k) => {
      const slot = res.slots[k];
      if (slot.duplicate) update(it.key, { phase: 'duplicate', trackId: slot.trackId });
      else work.push({ item: it, url: slot.uploadUrl!, contentType: slot.contentType!, trackId: slot.trackId! });
    });
    await pool(work, PARALLEL_PUTS, async (w) => {
      try {
        update(w.item.key, { phase: 'uploading', progress: 0 });
        await put(w.url, w.item.file, w.contentType, (p) => update(w.item.key, { phase: 'uploading', progress: p }));
        toConfirm.push(w.trackId);
      } catch (e) {
        update(w.item.key, { phase: 'error', message: (e as Error).message });
      }
    });
    if (toConfirm.length) {
      const results = await api.completeUpload(toConfirm);
      for (const r of results) {
        const w = work.find((x) => x.trackId === r.trackId);
        if (!w) continue;
        update(w.item.key, r.error ? { phase: 'error', message: r.error } : { phase: 'queued', trackId: r.trackId });
      }
    }
  }
  return { quotaRemaining };
}
