import { useI18n } from '../lib/i18n';
import { keyName } from '../lib/format';
import { label } from '../lib/reasons';
import type { TrackSummary, TrackView } from '../lib/types';

/** Western key as Camelot + musical name, in the "western" accent. */
export function KeyTag({ camelot, pc, mode, edited }: { camelot: string | null; pc?: number | null; mode?: string | null; edited?: boolean }) {
  const { t } = useI18n();
  if (!camelot) return <span className="muted">—</span>;
  return (
    <span className="tag tag-key" title={edited ? t('edited') : undefined}>
      <b>{camelot}</b>
      {pc !== undefined && <span className="tag-sub">{keyName(pc, mode)}</span>}
      {edited && <span className="dot" aria-label={t('edited')} />}
    </span>
  );
}

/** Maqam + tonic in the "maqam" accent; dashed when the detector is unsure. */
export function MaqamTag({ maqam, tonic, uncertain, edited }: { maqam: string | null; tonic?: string | null; uncertain?: boolean | null; edited?: boolean }) {
  const { t, maqamName } = useI18n();
  if (!maqam) return <span className="muted">—</span>;
  return (
    <span className={`tag tag-maqam${uncertain ? ' tag-uncertain' : ''}`} title={uncertain ? t('uncertain') : edited ? t('edited') : undefined}>
      <b>{maqamName(maqam)}</b>
      {tonic && <span className="tag-sub">{tonic}</span>}
      {uncertain && <span className="tag-sub">?</span>}
      {edited && <span className="dot" aria-label={t('edited')} />}
    </span>
  );
}

/** The harmonic tag that matters for this track: maqam for Arabic-modal, key otherwise. */
export function HarmonicTag({ track }: { track: TrackView }) {
  const edited = track.editedFields ?? [];
  if (track.tonality === 'ARABIC_MODAL') {
    return <MaqamTag maqam={track.maqam} tonic={track.tonicNote} uncertain={track.maqamUncertain} edited={edited.includes('maqam') || edited.includes('tonic')} />;
  }
  return <KeyTag camelot={track.camelot} pc={track.keyTonicPc} mode={track.keyMode} edited={edited.includes('key')} />;
}

export function SummaryTag({ track }: { track: TrackSummary }) {
  const { maqamName, lang } = useI18n();
  if (track.tonality === 'ARABIC_MODAL' && track.maqam) {
    const tonic = track.harmonicLabel.split(' on ')[1] ?? '';
    return <span className="tag tag-maqam"><b>{maqamName(track.maqam)}</b><span className="tag-sub">{label(tonic, lang)}</span></span>;
  }
  return track.camelot ? <span className="tag tag-key"><b>{track.camelot}</b></span> : <span className="muted">—</span>;
}

export function EnergyMeter({ value }: { value: number | null }) {
  if (value === null || value === undefined) return <span className="muted">—</span>;
  const v = Math.max(0, Math.min(1, value));
  return (
    <span className="meter" role="meter" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(v * 100)}>
      <span className="meter-fill" style={{ inlineSize: `${v * 100}%` }} />
    </span>
  );
}

export function ScoreBar({ score }: { score: number }) {
  const v = Math.max(0, Math.min(1, score));
  const tone = v >= 0.75 ? 'good' : v >= 0.55 ? 'ok' : 'weak';
  return (
    <span className={`score score-${tone}`}>
      <span className="score-track"><span className="score-fill" style={{ inlineSize: `${v * 100}%` }} /></span>
      <b>{Math.round(v * 100)}</b>
    </span>
  );
}
