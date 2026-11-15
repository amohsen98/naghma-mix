import { useI18n } from '../lib/i18n';
import { formatReasons } from '../lib/reasons';
import type { EnergyPoint, SetEntry } from '../lib/types';

const NOTE_LABELS = ['C', 'Db', 'D', 'Eb', 'E', 'F', 'F#', 'G', 'Ab', 'A', 'Bb', 'B'];

/**
 * Pitch-class histogram, 120 bins of 10 cents. Semitone lines are drawn; mass sitting
 * between them (quarter tones) shows in the maqam accent.
 */
export function PitchHistogram({ bins, tonicCents }: { bins: number[]; tonicCents: number | null }) {
  if (!bins?.length) return null;
  const w = 600;
  const h = 120;
  const max = Math.max(...bins) || 1;
  const bw = w / bins.length;
  return (
    <svg className="chart" viewBox={`0 0 ${w} ${h + 18}`} role="img" aria-label="Pitch profile">
      {NOTE_LABELS.map((n, i) => (
        <g key={n}>
          <line x1={i * 50} x2={i * 50} y1={0} y2={h} className="grid" />
          <text x={i * 50 + 2} y={h + 14} className="axis-label">{n}</text>
        </g>
      ))}
      {bins.map((v, i) => {
        const cents = i * 10;
        const offGrid = Math.abs(((cents + 50) % 100) - 50) >= 30;
        const bh = (v / max) * (h - 4);
        return <rect key={i} x={i * bw} y={h - bh} width={bw - 0.6} height={bh} className={offGrid ? 'bar-maqam' : 'bar-key'} />;
      })}
      {tonicCents !== null && (
        <line x1={(tonicCents / 1200) * w} x2={(tonicCents / 1200) * w} y1={0} y2={h} className="tonic-line" />
      )}
    </svg>
  );
}

/** Energy over the track as a filled line. */
export function EnergyLine({ points, duration }: { points: EnergyPoint[]; duration: number | null }) {
  if (!points?.length) return null;
  const w = 600;
  const h = 80;
  const total = duration || points[points.length - 1].end;
  const xy = points.map((p) => [((p.start + p.end) / 2 / total) * w, h - p.energy * (h - 6)]);
  const d = xy.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`).join(' ');
  return (
    <svg className="chart" viewBox={`0 0 ${w} ${h}`} role="img" aria-label="Energy">
      <line x1={0} x2={w} y1={h - 0.5 * (h - 6)} y2={h - 0.5 * (h - 6)} className="grid" />
      <path d={`${d} L${xy[xy.length - 1][0]} ${h} L${xy[0][0]} ${h} Z`} className="area" />
      <path d={d} className="line" />
    </svg>
  );
}

/**
 * The set's signature view: the target energy shape as a band, each track as a mark at its
 * energy, and the quality of every transition as a bar along the bottom.
 */
export function SetEnergyChart({ entries }: { entries: SetEntry[] }) {
  const { t, lang } = useI18n();
  const n = entries.length;
  if (n === 0) return null;
  const w = 900;
  const top = 16;
  const plotH = 200;
  const barsY = top + plotH + 18;
  const barH = 34;
  const h = barsY + barH + 8;
  const x = (i: number) => (n === 1 ? w / 2 : 24 + (i / (n - 1)) * (w - 48));
  const y = (e: number) => top + (1 - e) * plotH;
  const target = entries.map((e, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)} ${y(e.targetEnergy).toFixed(1)}`).join(' ');
  const band = [
    ...entries.map((e, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)} ${y(Math.min(1, e.targetEnergy + 0.08)).toFixed(1)}`),
    ...entries.slice().reverse().map((e, k) => `L${x(n - 1 - k).toFixed(1)} ${y(Math.max(0, e.targetEnergy - 0.08)).toFixed(1)}`),
    'Z',
  ].join(' ');
  return (
    <figure className="set-chart">
      <svg className="chart" viewBox={`0 0 ${w} ${h}`} role="img" aria-label={t('targetVsActual')}>
        {[0.25, 0.5, 0.75].map((g) => <line key={g} x1={0} x2={w} y1={y(g)} y2={y(g)} className="grid" />)}
        <path d={band} className="target-band" />
        <path d={target} className="target-line" />
        {entries.map((e, i) => {
          const actual = e.track.energy ?? 0.5;
          const arabic = e.track.tonality === 'ARABIC_MODAL';
          return (
            <g key={e.track.id}>
              <line x1={x(i)} x2={x(i)} y1={y(e.targetEnergy)} y2={y(actual)} className="deviation" />
              <circle cx={x(i)} cy={y(actual)} r={n > 60 ? 3 : 5} className={arabic ? 'mark-maqam' : 'mark-key'}>
                <title>{`${i + 1}. ${e.track.title}, ${Math.round(actual * 100)}%`}</title>
              </circle>
              {e.transitionToNext && (
                <rect
                  x={x(i) + 1}
                  width={Math.max(1, x(i + 1) - x(i) - 2)}
                  y={barsY + barH * (1 - e.transitionToNext.score)}
                  height={barH * e.transitionToNext.score}
                  className={e.transitionToNext.score >= 0.75 ? 'flow-good' : e.transitionToNext.score >= 0.55 ? 'flow-ok' : 'flow-weak'}
                >
                  <title>{`${Math.round(e.transitionToNext.score * 100)}: ${formatReasons(e.transitionToNext.reasons, lang)}`}</title>
                </rect>
              )}
            </g>
          );
        })}
      </svg>
      <figcaption className="legend">
        <span><i className="lg-target" />{t('target')}</span>
        <span><i className="lg-key" />{t('WESTERN')}</span>
        <span><i className="lg-maqam" />{t('ARABIC_MODAL')}</span>
        <span><i className="lg-flow" />{t('transitionQuality')}</span>
      </figcaption>
    </figure>
  );
}
