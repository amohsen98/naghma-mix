import { describe, expect, it } from 'vitest';
import { formatReasons, label } from './reasons';
import { keyName } from './format';
import type { Reason } from './types';

describe('reasons', () => {
  const rs: Reason[] = [
    { code: 'sharedNotes', args: { from: 'Bayati on D', to: 'D minor', shared: 6, of: 7 }, text: 'x' },
    { code: 'bpmChange', args: { diff: -62, time: 'double' }, text: 'x' },
    { code: 'energyUp', args: {}, text: 'x' },
  ];

  it('words reasons in English', () => {
    expect(formatReasons(rs, 'en')).toBe('Bayati on D to D minor: 6 of 7 notes shared, -62 BPM (double time), energy up');
  });

  it('words reasons in Arabic, maqam names included', () => {
    expect(formatReasons(rs, 'ar').replace(/[\u2066\u2068\u2069]/g, '')).toBe('بياتي على D إلى D مينير: 6 من 7 نغمات مشتركة، -62 BPM (سرعة مضاعفة)، الطاقة ترتفع');
  });

  it('falls back to the server text for unknown codes', () => {
    expect(formatReasons([{ code: 'newThing', args: {}, text: 'server words' }], 'ar')).toBe('server words');
  });

  it('translates half-flat tonics', () => {
    expect(label('Sikah on E half-flat', 'ar')).toBe('سيكاه على E نصف بيمول');
  });
});

describe('keyName', () => {
  it('names keys', () => {
    expect(keyName(9, 'minor')).toBe('Am');
    expect(keyName(6, 'major')).toBe('F#');
    expect(keyName(null, 'major')).toBe('');
  });
});
