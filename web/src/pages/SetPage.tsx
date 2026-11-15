import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, ApiError } from '../lib/api';
import { num } from '../lib/format';
import { useI18n } from '../lib/i18n';
import { formatReasons } from '../lib/reasons';
import type { Curve, SetView } from '../lib/types';
import { SetEnergyChart } from '../components/Charts';
import { ScoreBar, SummaryTag } from '../components/Tags';
import { CurvePicker } from './SetsPage';

export function SetPage() {
  const { t, lang } = useI18n();
  const { setId } = useParams();
  const navigate = useNavigate();
  const [set, setSet] = useState<SetView | null>(null);
  const [order, setOrder] = useState<string[]>([]);
  const [locked, setLocked] = useState<Set<string>>(new Set());
  const [curve, setCurve] = useState<Curve>('WEDDING');
  const [dirty, setDirty] = useState(false);
  const [error, setError] = useState<{ status: number; message: string } | null>(null);
  const [busy, setBusy] = useState(false);

  function accept(v: SetView) {
    setSet(v);
    setOrder(v.entries.map((e) => e.track.id));
    setLocked(new Set(v.entries.filter((e) => e.locked).map((e) => e.track.id)));
    setCurve(v.curve);
    setDirty(false);
  }

  useEffect(() => {
    if (setId) api.set(setId).then(accept).catch((e) => setError({ status: (e as ApiError).status ?? 0, message: (e as Error).message }));
  }, [setId]);

  async function save(keepOrder: boolean) {
    if (!set) return;
    setBusy(true);
    try {
      const lockMap: Record<string, number> = {};
      order.forEach((id, i) => { if (locked.has(id)) lockMap[id] = i; });
      accept(await api.updateSet(set.id, { name: set.name, trackIds: order, curve, locked: lockMap, keepOrder }));
      setError(null);
    } catch (e) {
      setError({ status: (e as ApiError).status ?? 0, message: (e as Error).message });
    } finally {
      setBusy(false);
    }
  }

  function move(i: number, delta: number) {
    const j = i + delta;
    if (j < 0 || j >= order.length) return;
    const next = [...order];
    [next[i], next[j]] = [next[j], next[i]];
    setOrder(next);
    setDirty(true);
  }

  async function remove() {
    if (!set || !confirm(t('confirmDeleteSet'))) return;
    await api.deleteSet(set.id);
    navigate('/sets');
  }

  if (!set) {
    return <div className="page">{error ? <p className="err">{error.message}</p> : <p className="muted">{t('loading')}</p>}</div>;
  }

  const byId = new Map(set.entries.map((e) => [e.track.id, e]));
  // While the DJ is reordering by hand, show the order as edited; scores refresh on save.
  const rows = dirty ? order.map((id, i) => ({ ...byId.get(id)!, position: i })) : set.entries;

  return (
    <div className="page set-page">
      <header className="set-head">
        <div>
          <h1>{set.name}</h1>
          <p className="set-meta muted"><span>{t('tracksCount', { n: set.entries.length })}</span><span>{t('curve')}: {t(set.curve)}</span></p>
        </div>
        <div className="set-score">
          <span className="muted">{t('avgFlow')}</span>
          <span className="big-num">{Math.round(set.averageTransition * 100)}</span>
        </div>
      </header>

      <SetEnergyChart entries={set.entries} />

      <section className="set-actions">
        <CurvePicker value={curve} onChange={(c) => { setCurve(c); }} />
        <div className="btn-row">
          <button className="btn" disabled={busy} onClick={() => save(false)}>{t('rebuild')}</button>
          {dirty && <button className="btn-ghost" disabled={busy} onClick={() => save(true)}>{t('saveOrder')}</button>}
          <button className="btn-ghost" onClick={() => api.downloadExport(set.id, 'm3u', set.name)}>{t('exportM3u')}</button>
          <button className="btn-ghost" onClick={() => api.downloadExport(set.id, 'rekordbox', set.name)}>{t('exportRekordbox')}</button>
        </div>
      </section>

      {error && error.status === 403 && <p className="notice">{t('setBuilderPaid')} <Link to="/plans">{t('seePlans')}</Link></p>}
      {error && error.status !== 403 && <p className="err" role="alert">{error.message}</p>}

      <ol className="set-order">
        {rows.map((e, i) => (
          <li key={e.track.id} className={locked.has(e.track.id) ? 'is-locked' : ''}>
            <span className="pos">{i + 1}</span>
            <div className="ellipsis">
              <Link to={`/next/${e.track.id}`} className="ellipsis">{e.track.title}</Link>
              {e.track.artist && <span className="muted ellipsis"> {e.track.artist}</span>}
            </div>
            <SummaryTag track={e.track} />
            <span className="num muted">{num(e.track.bpm, 0)}</span>
            <span className="row-tools">
              <button className="icon" aria-label={t('moveUp')} title={t('moveUp')} onClick={() => move(i, -1)} disabled={i === 0}>▲</button>
              <button className="icon" aria-label={t('moveDown')} title={t('moveDown')} onClick={() => move(i, 1)} disabled={i === rows.length - 1}>▼</button>
              <button
                className={`icon lock${locked.has(e.track.id) ? ' on' : ''}`}
                aria-pressed={locked.has(e.track.id)}
                title={locked.has(e.track.id) ? t('unlock') : t('lock')}
                aria-label={locked.has(e.track.id) ? t('unlock') : t('lock')}
                onClick={() => setLocked((s) => { const n = new Set(s); if (n.has(e.track.id)) n.delete(e.track.id); else n.add(e.track.id); return n; })}
              >
                <svg viewBox="0 0 16 16" aria-hidden="true"><rect x="3" y="7" width="10" height="7" rx="1.5" /><path d={locked.has(e.track.id) ? 'M5 7V5a3 3 0 0 1 6 0v2' : 'M5 7V5a3 3 0 0 1 6 0'} /></svg>
              </button>
            </span>
            {!dirty && e.transitionToNext && (
              <div className="transition">
                <ScoreBar score={e.transitionToNext.score} />
                <span className="muted small">{formatReasons(e.transitionToNext.reasons, lang)}</span>
              </div>
            )}
          </li>
        ))}
      </ol>

      <button className="btn-ghost danger" onClick={remove}>{t('deleteSet')}</button>
    </div>
  );
}
