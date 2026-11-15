import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, ApiError } from '../lib/api';
import { num } from '../lib/format';
import { useI18n } from '../lib/i18n';
import { formatReasons } from '../lib/reasons';
import type { Direction, Suggestion, TrackView, Weights } from '../lib/types';
import { HarmonicTag, ScoreBar, SummaryTag } from '../components/Tags';

const DEFAULT_WEIGHTS: Weights = { harmony: 0.45, tonic: 0.1, tempo: 0.3, energy: 0.15 };

export function NextPage() {
  const { t, lang } = useI18n();
  const { trackId } = useParams();
  const navigate = useNavigate();
  const [library, setLibrary] = useState<TrackView[]>([]);
  const [direction, setDirection] = useState<Direction>('steady');
  const [weights, setWeights] = useState<Weights>(DEFAULT_WEIGHTS);
  const [suggestions, setSuggestions] = useState<Suggestion[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.tracks({ status: 'DONE', size: 200 }).then((p) => setLibrary(p.items)).catch(() => {});
  }, []);

  useEffect(() => {
    if (!trackId) return;
    setSuggestions(null);
    const h = setTimeout(() => {
      api.next(trackId, { direction, limit: 15, ...weights })
        .then((s) => { setSuggestions(s); setError(null); })
        .catch((e) => setError(e instanceof ApiError && e.status === 0 ? t('errorNetwork') : (e as Error).message));
    }, 200);
    return () => clearTimeout(h);
  }, [trackId, direction, weights, t]);

  const current = library.find((x) => x.id === trackId);

  return (
    <div className="page next-page">
      <section className="now">
        <label className="now-pick">
          <span className="muted">{t('nowPlaying')}</span>
          <select value={trackId ?? ''} onChange={(e) => navigate(`/next/${e.target.value}`)}>
            <option value="" disabled>{t('pickTrack')}</option>
            {library.map((x) => <option key={x.id} value={x.id}>{x.artist ? `${x.artist} - ${x.title}` : x.title}</option>)}
          </select>
        </label>
        {current && (
          <div className="now-tags">
            <HarmonicTag track={current} />
            <span className="big-num">{num(current.bpm, 1)}<small>{t('bpm')}</small></span>
          </div>
        )}
      </section>

      <section className="controls">
        <fieldset>
          <legend>{t('direction')}</legend>
          <div className="segmented">
            {(['down', 'steady', 'up'] as Direction[]).map((d) => (
              <button key={d} className={direction === d ? 'on' : ''} aria-pressed={direction === d} onClick={() => setDirection(d)}>{t(d)}</button>
            ))}
          </div>
        </fieldset>
        <fieldset className="weights">
          <legend>{t('weights')}</legend>
          {(Object.keys(DEFAULT_WEIGHTS) as (keyof Weights)[]).map((k) => (
            <label key={k}>
              <span>{t(k)}</span>
              <input type="range" min={0} max={1} step={0.05} value={weights[k]}
                onChange={(e) => setWeights((w) => ({ ...w, [k]: Number(e.target.value) }))} />
            </label>
          ))}
        </fieldset>
      </section>

      {!trackId && <p className="empty">{t('pickTrack')}</p>}
      {error && <p className="err" role="alert">{error}</p>}
      {suggestions && suggestions.length === 0 && <p className="empty">{t('noSuggestions')}</p>}

      {suggestions && suggestions.length > 0 && (
        <ol className="suggestions">
          {suggestions.map((s) => (
            <li key={s.track.id}>
              <ScoreBar score={s.transition.score} />
              <div className="sugg-main">
                <Link to={`/next/${s.track.id}`} className="sugg-title">
                  <span className="ellipsis">{s.track.title}</span>
                  {s.track.artist && <span className="muted ellipsis">{s.track.artist}</span>}
                </Link>
                <p className="reasons">{formatReasons(s.transition.reasons, lang)}</p>
              </div>
              <div className="sugg-tags">
                <SummaryTag track={s.track} />
                <span className="muted">{num(s.track.bpm, 0)}</span>
              </div>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
