import { describe, expect, it } from 'vitest';
import { hiddenMaqam, journeyStops, showJourney } from './maqamHints';
import type { MaqamSection } from './types';

const sec = (start: number, end: number, maqam: string | null, uncertain = false): MaqamSection => ({
  start, end, maqam, arabic: null, tonicNote: null, tonicCents: null, confidence: 0.6, uncertain,
});

describe('hidden maqam next to the Western key', () => {
  const ahwak = [
    { name: 'Nahawand', arabic: 'نهاوند', confidence: 0.4 },
    { name: 'Ajam', arabic: 'عجم', confidence: 0.33 },
    { name: 'Hijaz', arabic: 'حجاز', confidence: 0.12 },
  ];

  it('shows Nahawand behind a minor key', () => {
    expect(hiddenMaqam({ tonality: 'WESTERN', keyMode: 'minor' }, ahwak)?.name).toBe('Nahawand');
  });

  it('shows Ajam behind a major key, never Nahawand', () => {
    expect(hiddenMaqam({ tonality: 'WESTERN', keyMode: 'major' }, ahwak)?.name).toBe('Ajam');
  });

  it('stays quiet when the guess is weak or the track is already tagged Arabic', () => {
    expect(hiddenMaqam({ tonality: 'WESTERN', keyMode: 'minor' }, [{ name: 'Kurd', arabic: '', confidence: 0.2 }])).toBeNull();
    expect(hiddenMaqam({ tonality: 'ARABIC_MODAL', keyMode: 'minor' }, ahwak)).toBeNull();
    expect(hiddenMaqam({ tonality: 'WESTERN', keyMode: 'minor' }, [{ name: 'Saba', arabic: '', confidence: 0.9 }])).toBeNull();
  });
});

describe('maqam journey', () => {
  it('lists stops in order across silent gaps', () => {
    expect(journeyStops([sec(0, 60, 'Ajam'), sec(60, 90, 'Sikah'), sec(90, 120, null), sec(120, 180, 'Ajam')]))
      .toEqual(['Ajam', 'Sikah', 'Ajam']);
    expect(journeyStops([sec(0, 30, 'Ajam'), sec(30, 60, null), sec(60, 90, 'Ajam')])).toEqual(['Ajam']);
  });

  it('is shown for a Sikah couplet but not for a pop song in plain Ajam', () => {
    const western = { tonality: 'WESTERN' as const };
    expect(showJourney(western, [sec(0, 60, 'Ajam'), sec(60, 90, 'Sikah')], null)).toBe(true);
    expect(showJourney(western, [sec(0, 90, 'Ajam')], null)).toBe(false);
    expect(showJourney(western, [sec(0, 60, 'Ajam'), sec(60, 90, 'Sikah', true)], null)).toBe(false);
    expect(showJourney(western, [sec(0, 90, null)], null)).toBe(false);
  });
});
