import { MAQAM_AR, type Lang } from './i18n';
import type { Reason } from './types';

type Args = Record<string, string | number>;

/** Bidi isolates keep Latin note names and numbers in place inside Arabic sentences. */
const iso = (x: string | number) => `\u2068${x}\u2069`;
const ltr = (x: string | number) => `\u2066${x}\u2069`;

const en: Record<string, (a: Args) => string> = {
  sameKey: (a) => `same key (${a.from})`,
  relativeMinor: (a) => `relative minor (${a.from} to ${a.to})`,
  relativeMajor: (a) => `relative major (${a.from} to ${a.to})`,
  adjacentKey: (a) => `neighbouring key (${a.from} to ${a.to})`,
  energyBoostKey: (a) => `two steps up the wheel (${a.from} to ${a.to})`,
  diagonalKey: (a) => `diagonal move (${a.from} to ${a.to})`,
  keyClash: (a) => `keys clash (${a.from} to ${a.to})`,
  keyUnknown: () => 'key unknown',
  sameMaqam: (a) => `same maqam (${a.label})`,
  sharedNotes: (a) => `${a.from} to ${a.to}: ${a.shared} of ${a.of} notes shared`,
  pitchShift: (a) => `shift pitch ${Number(a.n) > 0 ? '+' : ''}${a.n} semitone`,
  maqamUncertain: () => 'maqam uncertain',
  tempoUnknown: () => 'tempo unknown',
  sameTempo: () => 'same tempo',
  bpmChange: (a) => `${Number(a.diff) > 0 ? '+' : ''}${a.diff} BPM${a.time ? ` (${a.time} time)` : ''}`,
  tempoJump: (a) => `tempo jump ${a.from} to ${a.to} BPM`,
  energyUp: () => 'energy up',
  energyDown: () => 'energy down',
  energySteady: () => 'energy steady',
};

const ar: Record<string, (a: Args) => string> = {
  sameKey: (a) => `نفس السلّم (${a.from})`,
  relativeMinor: (a) => `المينير النسبي (${a.from} إلى ${a.to})`,
  relativeMajor: (a) => `الماجير النسبي (${a.from} إلى ${a.to})`,
  adjacentKey: (a) => `سلّم مجاور (${a.from} إلى ${a.to})`,
  energyBoostKey: (a) => `خطوتان على العجلة (${a.from} إلى ${a.to})`,
  diagonalKey: (a) => `انتقال قُطري (${a.from} إلى ${a.to})`,
  keyClash: (a) => `تنافر في السلّم (${a.from} إلى ${a.to})`,
  keyUnknown: () => 'السلّم غير معروف',
  sameMaqam: (a) => `نفس المقام (${iso(label(String(a.label), 'ar'))})`,
  sharedNotes: (a) => `${iso(label(String(a.from), 'ar'))} إلى ${iso(label(String(a.to), 'ar'))}: ${a.shared} من ${a.of} نغمات مشتركة`,
  pitchShift: (a) => `تغيير الطبقة ${Number(a.n) > 0 ? '+' : ''}${a.n} نصف تون`,
  maqamUncertain: () => 'المقام غير مؤكد',
  tempoUnknown: () => 'السرعة غير معروفة',
  sameTempo: () => 'نفس السرعة',
  bpmChange: (a) => `${ltr(`${Number(a.diff) > 0 ? '+' : ''}${a.diff} BPM`)}${a.time === 'double' ? ' (سرعة مضاعفة)' : a.time === 'half' ? ' (نصف السرعة)' : ''}`,
  tempoJump: (a) => `قفزة في السرعة ${ltr(a.from)} إلى ${ltr(`${a.to} BPM`)}`,
  energyUp: () => 'الطاقة ترتفع',
  energyDown: () => 'الطاقة تهدأ',
  energySteady: () => 'الطاقة ثابتة',
};

/** "Bayati on D" → "بياتي على D"; "D minor" → "D مينير". */
export function label(s: string, lang: Lang): string {
  if (lang !== 'ar') return s;
  let out = s.replace(' on ', ' على ').replace(/ major$/, ' ماجير').replace(/ minor$/, ' مينير').replace('half-flat', 'نصف بيمول');
  for (const [name, arabic] of Object.entries(MAQAM_AR)) out = out.replace(name, arabic);
  return out;
}

export function formatReason(r: Reason, lang: Lang): string {
  const fn = (lang === 'ar' ? ar : en)[r.code];
  return fn ? fn(r.args as Args) : r.text;
}

export function formatReasons(rs: Reason[], lang: Lang): string {
  return rs.map((r) => formatReason(r, lang)).join(lang === 'ar' ? '، ' : ', ');
}
