import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError } from '../lib/api';
import { duration, MAQAM_TONICS, MAQAMAT, NOTE_CHOICES, num, pct } from '../lib/format';
import { useI18n } from '../lib/i18n';
import type { TrackDetail, TrackView } from '../lib/types';
import { EnergyLine, PitchHistogram } from './Charts';
import { hiddenMaqam, showJourney } from '../lib/maqamHints';
import { MaqamJourney } from './MaqamJourney';
import { HarmonicTag } from './Tags';

const PC_NAMES = NOTE_CHOICES;

export function TrackPanel({ id, onClose, onChanged }: { id: string; onClose: () => void; onChanged: (t: TrackView | null) => void }) {
  const { t, maqamName } = useI18n();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<TrackDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [form, setForm] = useState<Record<string, string>>({});

  useEffect(() => {
    setDetail(null);
    setError(null);
    api.track(id).then((d) => {
      setDetail(d);
      const tr = d.track;
      setForm({
        title: tr.title ?? '',
        artist: tr.artist ?? '',
        bpm: tr.bpm ? tr.bpm.toFixed(1) : '',
        tonality: tr.tonality ?? 'WESTERN',
        keyTonic: tr.keyTonicPc !== null ? PC_NAMES[tr.keyTonicPc] : '',
        keyMode: tr.keyMode ?? 'minor',
        maqam: tr.maqam ?? '',
        tonicNote: tr.tonicNote ?? '',
      });
    }).catch((e) => setError(e instanceof ApiError ? e.message : String(e)));
  }, [id]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  async function save(e: FormEvent) {
    e.preventDefault();
    if (!detail) return;
    const tr = detail.track;
    const body: Record<string, string | number | undefined> = {};
    if (form.title !== tr.title) body.title = form.title;
    if (form.artist !== (tr.artist ?? '')) body.artist = form.artist;
    if (form.bpm && Number(form.bpm) !== Number(tr.bpm?.toFixed(1))) body.bpm = Number(form.bpm);
    if (form.tonality !== tr.tonality) body.tonality = form.tonality;
    if (form.keyTonic && (form.keyTonic !== (tr.keyTonicPc !== null ? PC_NAMES[tr.keyTonicPc] : '') || form.keyMode !== tr.keyMode)) {
      body.keyTonic = form.keyTonic;
      body.keyMode = form.keyMode;
    }
    if (form.tonality === 'ARABIC_MODAL') {
      if (form.maqam && form.maqam !== tr.maqam) body.maqam = form.maqam;
      if (form.tonicNote && form.tonicNote !== tr.tonicNote) body.tonicNote = form.tonicNote;
    }
    try {
      const updated = await api.editTags(tr.id, body);
      setDetail({ ...detail, track: updated });
      onChanged(updated);
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : String(err));
    }
  }

  async function remove() {
    if (!detail || !confirm(t('confirmDelete'))) return;
    await api.deleteTrack(detail.track.id);
    onChanged(null);
    onClose();
  }

  async function reanalyze() {
    if (!detail) return;
    try {
      const tr = await api.reanalyze(detail.track.id);
      onChanged(tr);
      onClose();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : String(err));
    }
  }

  const set = (k: string) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [k]: e.target.value }));
  const tr = detail?.track;
  const hint = tr && detail ? hiddenMaqam(tr, detail.maqamCandidates) : null;

  return (
    <aside className="panel" aria-label={t('details')}>
      <header className="panel-head">
        <div className="ellipsis">
          <h2 className="ellipsis">{tr?.title ?? t('loading')}</h2>
          {tr?.artist && <p className="muted ellipsis">{tr.artist}</p>}
        </div>
        <button className="btn-ghost" onClick={onClose} aria-label={t('close')}>✕</button>
      </header>

      {error && <p className="err" role="alert">{error}</p>}
      {tr && tr.status !== 'DONE' && <p className="muted">{tr.status === 'FAILED' ? tr.error : t('noAnalysis')}</p>}

      {tr && tr.status === 'DONE' && detail && (
        <>
          <dl className="facts">
            <div><dt>{t('tonality')}</dt><dd className="harmonic">
              <HarmonicTag track={tr} />
              {hint && (
                <span className="tag tag-hint" title={t('hiddenMaqamHint', { m: maqamName(hint.name) })} aria-label={t('hiddenMaqam', { m: maqamName(hint.name) })}>
                  ≈ {maqamName(hint.name)}
                </span>
              )}
            </dd></div>
            <div><dt>{t('bpm')}</dt><dd>{num(tr.bpm, 1)}</dd></div>
            <div><dt>{t('energy')}</dt><dd>{pct(tr.energy)}</dd></div>
            <div><dt>{t('duration')}</dt><dd>{duration(tr.durationSec)}</dd></div>
            <div><dt>{t('loudness')}</dt><dd>{tr.lufs !== null ? `${num(tr.lufs, 1)} LUFS` : '—'}</dd></div>
          </dl>

          <button className="btn btn-wide" onClick={() => navigate(`/next/${tr.id}`)}>{t('suggestNext')}</button>

          {hint && <p className="muted small">{t('hiddenMaqamHint', { m: maqamName(hint.name) })}</p>}

          {showJourney(tr, detail.maqamTimeline, hint) && (
            <section>
              <h3>{t('maqamJourney')}</h3>
              <MaqamJourney sections={detail.maqamTimeline} />
              <p className="muted small">{t('maqamJourneyHint')}</p>
            </section>
          )}

          {detail.maqamCandidates?.length > 0 && (tr.tonality === 'ARABIC_MODAL' || hint) && (
            <section>
              <h3>{t('candidates')}</h3>
              <ul className="candidates">
                {detail.maqamCandidates.map((c) => (
                  <li key={c.name}>
                    <span>{maqamName(c.name)}</span>
                    <span className="meter"><span className="meter-fill maqam" style={{ inlineSize: `${c.confidence * 100}%` }} /></span>
                    <span className="muted">{pct(c.confidence)}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}

          <section>
            <h3>{t('pitchProfile')}</h3>
            <PitchHistogram bins={detail.pitchHistogram} tonicCents={detail.tonicCents} />
            <p className="muted small">{t('pitchProfileHint')}</p>
          </section>

          <section>
            <h3>{t('energyCurve')}</h3>
            <EnergyLine points={detail.energyCurve} duration={tr.durationSec} />
          </section>

          <section>
            <h3>{t('correctTags')}</h3>
            <p className="muted small">{t('correctHint')}</p>
            <form className="form-grid" onSubmit={save}>
              <label>{t('title')}<input value={form.title} onChange={set('title')} /></label>
              <label>{t('artist')}<input value={form.artist} onChange={set('artist')} /></label>
              <label>{t('bpm')}<input type="number" step="0.1" min="40" max="250" value={form.bpm} onChange={set('bpm')} /></label>
              <label>{t('tonality')}
                <select value={form.tonality} onChange={set('tonality')}>
                  <option value="WESTERN">{t('WESTERN')}</option>
                  <option value="ARABIC_MODAL">{t('ARABIC_MODAL')}</option>
                </select>
              </label>
              <label>{t('keyTonic')}
                <select value={form.keyTonic} onChange={set('keyTonic')}>
                  <option value="">—</option>
                  {NOTE_CHOICES.map((n) => <option key={n}>{n}</option>)}
                </select>
              </label>
              <label>{t('mode')}
                <select value={form.keyMode} onChange={set('keyMode')}>
                  <option value="major">{t('major')}</option>
                  <option value="minor">{t('minor')}</option>
                </select>
              </label>
              {form.tonality === 'ARABIC_MODAL' && (
                <>
                  <label>{t('maqam')}
                    <select value={form.maqam} onChange={set('maqam')}>
                      <option value="">—</option>
                      {MAQAMAT.map((m) => <option key={m} value={m}>{maqamName(m)}</option>)}
                    </select>
                  </label>
                  <label>{t('maqamTonic')}
                    <select value={form.tonicNote} onChange={set('tonicNote')}>
                      <option value="">—</option>
                      {MAQAM_TONICS.map((n) => <option key={n}>{n}</option>)}
                    </select>
                  </label>
                </>
              )}
              <div className="form-actions">
                <button className="btn" type="submit">{t('saveTags')}</button>
                {saved && <span className="ok" role="status">{t('tagsSaved')}</span>}
              </div>
            </form>
          </section>
        </>
      )}

      {tr && (
        <footer className="panel-foot">
          {tr.status !== 'QUEUED' && tr.status !== 'AWAITING_UPLOAD' && <button className="btn-ghost" onClick={reanalyze}>{t('reanalyze')}</button>}
          <button className="btn-ghost danger" onClick={remove}>{t('deleteTrack')}</button>
        </footer>
      )}
    </aside>
  );
}
