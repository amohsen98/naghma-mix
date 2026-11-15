import { useEffect, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api, ApiError } from '../lib/api';
import { relativeDate } from '../lib/format';
import { useI18n } from '../lib/i18n';
import type { Curve, SetListItem } from '../lib/types';

export const CURVES: Curve[] = ['WEDDING', 'WARM_UP', 'PEAK_TIME', 'WAVE', 'FLAT'];

export function SetsPage() {
  const { t, lang } = useI18n();
  const [sets, setSets] = useState<SetListItem[] | null>(null);

  useEffect(() => {
    api.sets().then(setSets).catch(() => setSets([]));
  }, []);

  return (
    <div className="page">
      {sets && sets.length === 0 && <p className="empty">{t('noSets')} {t('newSetHint')}</p>}
      {sets && sets.length > 0 && (
        <ul className="set-list">
          {sets.map((s) => (
            <li key={s.id}>
              <Link to={`/sets/${s.id}`}>
                <span className="set-name">{s.name}</span>
                <span className="muted">{t(s.curve)}</span>
                <span className="muted">{t('tracksCount', { n: s.trackCount })}</span>
                <span className="muted hide-sm">{t('updated', { d: relativeDate(s.updatedAt, lang) })}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
      <p className="muted small">{t('newSetHint')}</p>
    </div>
  );
}

export function NewSetPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const location = useLocation();
  const trackIds: string[] = (location.state as { trackIds?: string[] } | null)?.trackIds ?? [];
  const [name, setName] = useState('');
  const [curve, setCurve] = useState<Curve>('WEDDING');
  const [error, setError] = useState<{ status: number; message: string } | null>(null);
  const [busy, setBusy] = useState(false);

  async function create(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      const set = await api.createSet({ name, trackIds, curve });
      navigate(`/sets/${set.id}`, { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? { status: err.status, message: err.message } : { status: 0, message: String(err) });
    } finally {
      setBusy(false);
    }
  }

  if (!trackIds.length) {
    return <div className="page"><p className="empty">{t('newSetHint')} <Link to="/">{t('library')}</Link></p></div>;
  }

  return (
    <div className="page narrow">
      <h1>{t('buildSet')}</h1>
      <p className="muted">{t('tracksCount', { n: trackIds.length })}</p>
      <form className="stack" onSubmit={create}>
        <label>{t('setName')}<input required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} /></label>
        <fieldset>
          <legend>{t('curve')}</legend>
          <CurvePicker value={curve} onChange={setCurve} />
        </fieldset>
        {error && error.status === 403 && (
          <p className="notice">{t('setBuilderPaid')} <Link to="/plans">{t('seePlans')}</Link></p>
        )}
        {error && error.status !== 403 && <p className="err" role="alert">{error.message}</p>}
        <button className="btn" type="submit" disabled={busy || !name.trim()}>{t('createSet')}</button>
      </form>
    </div>
  );
}

/** Each option previews its shape so the choice is visual, not just a name. */
export function CurvePicker({ value, onChange }: { value: Curve; onChange: (c: Curve) => void }) {
  const { t } = useI18n();
  const shapes: Record<Curve, number[]> = {
    WARM_UP: [0.3, 0.39, 0.48, 0.56, 0.65],
    PEAK_TIME: [0.6, 0.78, 0.83, 0.87, 0.9],
    WEDDING: [0.65, 0.75, 0.4, 0.7, 0.95],
    WAVE: [0.5, 0.8, 0.5, 0.85, 0.6],
    FLAT: [0.6, 0.6, 0.6, 0.6, 0.6],
  };
  return (
    <div className="curve-picker" role="radiogroup">
      {CURVES.map((c) => (
        <button type="button" key={c} role="radio" aria-checked={value === c} className={value === c ? 'on' : ''} onClick={() => onChange(c)}>
          <svg viewBox="0 0 80 32" aria-hidden="true">
            <polyline points={shapes[c].map((v, i) => `${i * 20},${30 - v * 28}`).join(' ')} />
          </svg>
          <span>{t(c)}</span>
        </button>
      ))}
    </div>
  );
}
