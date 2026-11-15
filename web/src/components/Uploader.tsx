import { useRef, useState } from 'react';
import { ApiError } from '../lib/api';
import { useI18n } from '../lib/i18n';
import { acceptable, uploadFiles, type FileState, type UploadItem } from '../lib/upload';

export function Uploader({ onQueued }: { onQueued: () => void }) {
  const { t } = useI18n();
  const input = useRef<HTMLInputElement>(null);
  const [items, setItems] = useState<UploadItem[]>([]);
  const [over, setOver] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const update = (key: string, state: FileState) =>
    setItems((prev) => prev.map((it) => (it.key === key ? { ...it, state } : it)));

  async function start(files: File[]) {
    if (!files.length || busy) return;
    setNotice(null);
    const fresh: UploadItem[] = [];
    const rejected: UploadItem[] = [];
    files.forEach((file, i) => {
      const key = `${Date.now()}-${i}-${file.name}`;
      const problem = acceptable(file);
      if (problem) rejected.push({ key, file, state: { phase: 'error', message: t(problem === 'size' ? 'rejectSize' : 'rejectFormat') } });
      else fresh.push({ key, file, state: { phase: 'hashing' } });
    });
    setItems([...fresh, ...rejected]);
    if (!fresh.length) return;
    setBusy(true);
    try {
      const { quotaRemaining } = await uploadFiles(fresh, update);
      if (quotaRemaining !== null && quotaRemaining >= 0) setNotice(t('quotaLeft', { n: quotaRemaining }));
      onQueued();
    } catch (e) {
      setNotice(e instanceof ApiError && e.status === 402 ? t('quotaReached') : e instanceof ApiError && e.status === 0 ? t('errorNetwork') : String((e as Error).message));
    } finally {
      setBusy(false);
    }
  }

  const done = items.filter((i) => i.state.phase === 'queued' || i.state.phase === 'duplicate').length;

  return (
    <section className="uploader">
      <div
        className={`dropzone${over ? ' is-over' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setOver(true); }}
        onDragLeave={() => setOver(false)}
        onDrop={(e) => { e.preventDefault(); setOver(false); start(Array.from(e.dataTransfer.files)); }}
      >
        <div>
          <p className="drop-title">{t('dropTitle')}</p>
          <p className="muted small">{t('dropHint')}</p>
        </div>
        <button type="button" className="btn" onClick={() => input.current?.click()} disabled={busy}>{t('chooseFiles')}</button>
        <input ref={input} type="file" multiple accept=".mp3,.wav,.flac,.m4a,audio/*" hidden
          onChange={(e) => { start(Array.from(e.target.files ?? [])); e.target.value = ''; }} />
      </div>
      {notice && <p className="notice" role="status">{notice}</p>}
      {items.length > 0 && (
        <details className="upload-list" open={busy}>
          <summary>{busy ? `${t('uploading')} ${done}/${items.length}` : `${done}/${items.length}`}</summary>
          <ul>
            {items.map((it) => (
              <li key={it.key}>
                <span className="ellipsis">{it.file.name}</span>
                <UploadState state={it.state} />
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  );
}

function UploadState({ state }: { state: FileState }) {
  const { t } = useI18n();
  switch (state.phase) {
    case 'hashing':
      return <span className="muted">{t('hashing')}</span>;
    case 'uploading':
      return <span className="progress"><span style={{ inlineSize: `${Math.round(state.progress * 100)}%` }} /></span>;
    case 'queued':
      return <span className="ok">{t('queuedForAnalysis')}</span>;
    case 'duplicate':
      return <span className="muted">{t('duplicate')}</span>;
    case 'error':
      return <span className="err">{state.message}</span>;
  }
}
