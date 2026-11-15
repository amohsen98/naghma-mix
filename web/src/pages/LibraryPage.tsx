import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError } from '../lib/api';
import { duration, num } from '../lib/format';
import { useI18n } from '../lib/i18n';
import type { TrackView } from '../lib/types';
import { HarmonicTag, EnergyMeter } from '../components/Tags';
import { TrackPanel } from '../components/TrackPanel';
import { Uploader } from '../components/Uploader';

const PAGE = 100;

export function LibraryPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [tracks, setTracks] = useState<TrackView[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [q, setQ] = useState('');
  const [tonality, setTonality] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState<string | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const query = useRef({ q, tonality });
  query.current = { q, tonality };

  const load = useCallback(async (p = 0) => {
    try {
      const res = await api.tracks({ q: query.current.q, tonality: query.current.tonality, page: 0, size: PAGE * (p + 1) });
      setTracks(res.items);
      setTotal(res.total);
      setPage(p);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError && e.status === 0 ? t('errorNetwork') : (e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    const h = setTimeout(() => load(0), 250);
    return () => clearTimeout(h);
  }, [q, tonality, load]);

  // Poll while anything is still being analyzed.
  const pending = tracks.some((tr) => tr.status === 'QUEUED' || tr.status === 'AWAITING_UPLOAD');
  useEffect(() => {
    if (!pending) return;
    const h = setInterval(() => load(page), 4000);
    return () => clearInterval(h);
  }, [pending, page, load]);

  const toggle = (id: string) => setSelected((s) => {
    const n = new Set(s);
    if (n.has(id)) n.delete(id); else n.add(id);
    return n;
  });

  const ready = useMemo(() => tracks.filter((tr) => tr.status === 'DONE'), [tracks]);
  const allReadySelected = ready.length > 0 && ready.every((tr) => selected.has(tr.id));

  return (
    <div className="page">
      <Uploader onQueued={() => load(0)} />

      <div className="toolbar">
        <input className="search" type="search" placeholder={t('search')} value={q} onChange={(e) => setQ(e.target.value)} aria-label={t('search')} />
        <div className="segmented" role="radiogroup">
          {[['', 'allTracks'], ['WESTERN', 'westernOnly'], ['ARABIC_MODAL', 'arabicOnly']].map(([v, k]) => (
            <button key={v} role="radio" aria-checked={tonality === v} className={tonality === v ? 'on' : ''} onClick={() => setTonality(v)}>
              {t(k as 'allTracks')}
            </button>
          ))}
        </div>
      </div>

      {error && <p className="err" role="alert">{error} <button className="btn-ghost" onClick={() => load(page)}>{t('retry')}</button></p>}

      {!loading && tracks.length === 0 && !error && (
        <p className="empty">{q || tonality ? t('noMatches') : t('emptyLibrary')}</p>
      )}

      {tracks.length > 0 && (
        <div className="table-wrap">
          <table className="tracks">
            <thead>
              <tr>
                <th className="col-check">
                  <input type="checkbox" checked={allReadySelected} aria-label={t('selected', { n: ready.length })}
                    onChange={() => setSelected(allReadySelected ? new Set() : new Set(ready.map((r) => r.id)))} />
                </th>
                <th>{t('title')}</th>
                <th className="num">{t('bpm')}</th>
                <th>{t('key')} / {t('maqam')}</th>
                <th>{t('energy')}</th>
                <th className="num hide-sm">{t('duration')}</th>
                <th className="hide-sm">{t('status')}</th>
              </tr>
            </thead>
            <tbody>
              {tracks.map((tr) => (
                <tr key={tr.id} className={open === tr.id ? 'is-open' : ''} onClick={() => setOpen(tr.id)}>
                  <td className="col-check" onClick={(e) => e.stopPropagation()}>
                    <input type="checkbox" disabled={tr.status !== 'DONE'} checked={selected.has(tr.id)} onChange={() => toggle(tr.id)} aria-label={tr.title} />
                  </td>
                  <td>
                    <button className="row-title" onClick={(e) => { e.stopPropagation(); setOpen(tr.id); }}>
                      <span className="ellipsis">{tr.title}</span>
                      {tr.artist && <span className="muted ellipsis">{tr.artist}</span>}
                    </button>
                  </td>
                  <td className="num">{tr.status === 'DONE' ? num(tr.bpm, 1) : ''}</td>
                  <td>{tr.status === 'DONE' ? <HarmonicTag track={tr} /> : null}</td>
                  <td>{tr.status === 'DONE' ? <EnergyMeter value={tr.energy} /> : null}</td>
                  <td className="num hide-sm">{duration(tr.durationSec)}</td>
                  <td className="hide-sm"><span className={`status status-${tr.status.toLowerCase()}`}>{t(tr.status)}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
          {tracks.length < total && <button className="btn-ghost load-more" onClick={() => load(page + 1)}>{t('loadMore')}</button>}
        </div>
      )}

      {selected.size > 0 && (
        <div className="selection-bar" role="region" aria-live="polite">
          <span>{t('selected', { n: selected.size })}</span>
          <button className="btn-ghost" onClick={() => setSelected(new Set())}>{t('clearSelection')}</button>
          <button className="btn" onClick={() => navigate('/sets/new', { state: { trackIds: [...selected] } })}>{t('buildSet')}</button>
        </div>
      )}

      {open && (
        <TrackPanel
          id={open}
          onClose={() => setOpen(null)}
          onChanged={(updated) => {
            if (!updated) {
              setTracks((ts) => ts.filter((x) => x.id !== open));
              setSelected((s) => { const n = new Set(s); n.delete(open); return n; });
            } else {
              setTracks((ts) => ts.map((x) => (x.id === updated.id ? updated : x)));
            }
          }}
        />
      )}
    </div>
  );
}
