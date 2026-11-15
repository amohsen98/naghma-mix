import type { Candidate, MaqamSection, TrackView } from './types';

/** Maqamat whose notes fit a Western scale, so they can hide behind a key label. */
const FITS: Record<string, Array<'major' | 'minor'>> = {
  Nahawand: ['minor'],
  Kurd: ['minor'],
  Hijaz: ['minor', 'major'],
  Ajam: ['major'],
};
const QUARTER_TONE = new Set(['Rast', 'Bayati', 'Saba', 'Sikah']);
export const HINT_MIN_CONFIDENCE = 0.3;

/**
 * The maqam a listener may hear behind a Western key: a "Dm" that is really sung as Nahawand.
 * Only for Western-tagged tracks, only when the candidate fits the key's mode and is not a guess.
 */
export function hiddenMaqam(track: Pick<TrackView, 'tonality' | 'keyMode'>, candidates: Candidate[] | null | undefined): Candidate | null {
  if (track.tonality !== 'WESTERN' || !candidates?.length) return null;
  const mode = track.keyMode === 'major' ? 'major' : 'minor';
  const best = [...candidates]
    .filter((c) => FITS[c.name]?.includes(mode) && c.confidence >= HINT_MIN_CONFIDENCE)
    .sort((a, b) => b.confidence - a.confidence)[0];
  return best ?? null;
}

/** Named stops of the journey in order, dropping "no melody" gaps and repeats across them. */
export function journeyStops(sections: MaqamSection[] | null | undefined): string[] {
  const out: string[] = [];
  for (const s of sections ?? []) {
    if (s.maqam && out[out.length - 1] !== s.maqam) out.push(s.maqam);
  }
  return out;
}

/**
 * Worth showing when the song is Arabic, a maqam hides behind its key, or some stretch is
 * confidently sung in a maqam a Western key can't spell. A pop song stays clean.
 */
export function showJourney(track: Pick<TrackView, 'tonality'>, sections: MaqamSection[] | null | undefined, hint: Candidate | null): boolean {
  if (!journeyStops(sections).length) return false;
  if (track.tonality === 'ARABIC_MODAL' || hint) return true;
  return (sections ?? []).some((s) => s.maqam && !s.uncertain && (QUARTER_TONE.has(s.maqam) || s.maqam === 'Hijaz'));
}
