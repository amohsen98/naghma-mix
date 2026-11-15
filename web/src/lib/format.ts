const KEY_NAMES = ['C', 'Db', 'D', 'Eb', 'E', 'F', 'F#', 'G', 'Ab', 'A', 'Bb', 'B'];

export function keyName(pc: number | null | undefined, mode: string | null | undefined): string {
  if (pc === null || pc === undefined || !mode) return '';
  return `${KEY_NAMES[((pc % 12) + 12) % 12]}${mode === 'minor' ? 'm' : ''}`;
}

export function duration(sec: number | null | undefined): string {
  if (!sec) return '';
  const m = Math.floor(sec / 60);
  const s = Math.round(sec % 60);
  return `${m}:${String(s).padStart(2, '0')}`;
}

export function pct(v: number | null | undefined): string {
  return v === null || v === undefined ? '' : `${Math.round(v * 100)}%`;
}

export function num(v: number | null | undefined, digits = 0): string {
  return v === null || v === undefined ? '' : v.toFixed(digits);
}

export const NOTE_CHOICES = [
  'C', 'Db', 'D', 'Eb', 'E', 'F', 'F#', 'G', 'Ab', 'A', 'Bb', 'B',
];

/** Tonic choices for maqamat, including the common half-flat tonics. */
export const MAQAM_TONICS = [
  'C', 'Db', 'D', 'Eb', 'E half-flat', 'E', 'F', 'F#', 'G', 'Ab', 'A', 'Bb', 'B half-flat', 'B',
];

export const MAQAMAT = ['Rast', 'Bayati', 'Saba', 'Sikah', 'Hijaz', 'Nahawand', 'Kurd', 'Ajam'];

export function relativeDate(iso: string, lang: string): string {
  const d = new Date(iso);
  return new Intl.DateTimeFormat(lang === 'ar' ? 'ar-EG' : 'en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(d);
}
