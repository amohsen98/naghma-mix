// Front-end development without the backend: serves dist/ and answers /api with sample data.
//   npm run build && node mock-server.mjs   →   http://localhost:4173
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join } from 'node:path';
import { randomUUID } from 'node:crypto';

const PORT = Number(process.env.PORT || 4173);
const DIST = new URL('./dist/', import.meta.url).pathname;
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' };

const user = { id: 'u1', email: 'karim@example.com', displayName: 'DJ Karim', tier: 'DJ', locale: 'en', monthlyTrackQuota: 1500, setBuilder: true };
const NOTES = ['C', 'Db', 'D', 'Eb', 'E', 'F', 'F#', 'G', 'Ab', 'A', 'Bb', 'B'];
const CAM_MAJ = [8, 3, 10, 5, 12, 7, 2, 9, 4, 11, 6, 1];
const CAM_MIN = [5, 12, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10];

const sample = [
  ['Om Kalthoum', 'Enta Omri (excerpt)', 'ARABIC_MODAL', 'Rast', 0, 'C', 92, 0.45],
  ['Amr Diab', 'Tamally Maak', 'ARABIC_MODAL', 'Nahawand', 0, 'C', 96, 0.62],
  ['Hassan Shakosh', 'Mahraganat Mix', 'ARABIC_MODAL', 'Hijaz', 200, 'D', 128, 0.86],
  ['Wegz', 'El Bakht', 'WESTERN', null, null, null, 90, 0.58],
  ['Dua Lipa', 'Levitating', 'WESTERN', null, null, null, 103, 0.74],
  ['Hussain Al Jassmi', 'Boshret Kheir', 'ARABIC_MODAL', 'Bayati', 200, 'D', 118, 0.79],
  ['The Weeknd', 'Blinding Lights', 'WESTERN', null, null, null, 171, 0.8],
  ['Mohamed Mounir', 'Ezay', 'ARABIC_MODAL', 'Saba', 200, 'D', 100, 0.5],
  ['Calvin Harris', 'Summer', 'WESTERN', null, null, null, 128, 0.77],
  ['Nancy Ajram', 'Ah W Noss', 'ARABIC_MODAL', 'Sikah', 350, 'E half-flat', 110, 0.71],
  ['Bruno Mars', 'Uptown Funk', 'WESTERN', null, null, null, 115, 0.84],
  ['Zaffa band', 'Wedding Zaffa', 'ARABIC_MODAL', 'Bayati', 200, 'D', 120, 0.88],
];

const tracks = sample.map(([artist, title, tonality, maqam, tonicCents, tonicNote, bpm, energy], i) => {
  const pc = [9, 0, 2, 7, 6, 2, 5, 2, 9, 4, 5, 2][i];
  const mode = i % 3 ? 'minor' : 'major';
  return {
    id: randomUUID(), title, artist, filename: `${artist} - ${title}.mp3`, status: i === 11 ? 'QUEUED' : 'DONE', error: null,
    createdAt: new Date(Date.now() - i * 3.6e6).toISOString(), analyzedAt: new Date().toISOString(),
    durationSec: 180 + i * 17, bpm, lufs: -9 - (i % 4), energy, tonality,
    keyTonicPc: pc, keyMode: mode, camelot: `${(mode === 'major' ? CAM_MAJ : CAM_MIN)[pc]}${mode === 'major' ? 'B' : 'A'}`, keyConfidence: 0.8,
    maqam, maqamConfidence: maqam ? 0.78 : null, maqamUncertain: i === 7, tonicNote, editedFields: i === 1 ? ['maqam'] : [],
    _tonicCents: tonicCents,
  };
});

function histogram(t) {
  const bins = new Array(120).fill(0);
  const scales = { Rast: [0, 200, 350, 500, 700, 900, 1050], Bayati: [0, 150, 300, 500, 700, 800, 1000], Saba: [0, 150, 300, 400, 700, 800, 1000], Sikah: [0, 150, 350, 550, 700, 850, 1050], Hijaz: [0, 100, 400, 500, 700, 800, 1000], Nahawand: [0, 200, 300, 500, 700, 800, 1100] };
  const scale = t.maqam ? scales[t.maqam] : [0, 200, 400, 500, 700, 900, 1100];
  const tonic = t.maqam ? t._tonicCents : t.keyTonicPc * 100;
  scale.forEach((c, k) => {
    const center = ((tonic + c) % 1200) / 10;
    for (let d = -3; d <= 3; d++) bins[(Math.round(center) + d + 120) % 120] += (k === 0 ? 1.6 : 1) * Math.exp(-(d * d) / 2);
  });
  const s = bins.reduce((a, b) => a + b, 0);
  return bins.map((b) => b / s);
}

// Egyptian tracks tagged with a Western key, where a maqam hides behind the minor scale.
const ARAB_POP = new Set(['Wegz', 'Hassan Shakosh']);
const view = ({ _tonicCents, ...t }) => t;
// An Ajam → Sikah → Ajam journey for Arabic tracks, with a drum break that has no melody.
const journey = (t) => {
  const d = t.durationSec || 200;
  const s = (a, b, maqam, arabic, note, cents) => ({ start: a, end: b, maqam, arabic, tonicNote: note, tonicCents: cents, confidence: maqam ? 0.62 : 0, uncertain: !maqam });
  if (!t.maqam) return [s(0, d, null, null, null, null)];
  return [s(0, 60, t.maqam, '', t.tonicNote, t._tonicCents), s(60, 90, 'Sikah', 'سيكاه', 'E half-flat', 350), s(90, 120, null, null, null, null), s(120, d, t.maqam, '', t.tonicNote, t._tonicCents)];
};
const summary = (t) => ({ id: t.id, title: t.title, artist: t.artist, bpm: t.bpm, energy: t.energy, tonality: t.tonality, camelot: t.camelot, maqam: t.maqam, harmonicLabel: t.maqam ? `${t.maqam} on ${t.tonicNote}` : `${NOTES[t.keyTonicPc]} ${t.keyMode}` });
const transition = (a, b) => {
  const harmony = a.maqam && b.maqam && a.maqam === b.maqam ? 1 : a.camelot === b.camelot ? 1 : 0.6 + ((a.bpm + b.bpm) % 5) / 15;
  const tempo = Math.max(0, 1 - Math.abs(a.bpm - b.bpm) / a.bpm / 0.08);
  const energy = Math.max(0, 1 - Math.abs(b.energy - a.energy) * 1.5);
  const score = Math.round((0.45 * harmony + 0.1 * 0.8 + 0.3 * tempo + 0.15 * energy) * 1000) / 1000;
  const d = b.bpm - a.bpm;
  return { score, components: { harmony, tonic: 0.8, tempo, energy }, reasons: [a.maqam && b.maqam && a.maqam === b.maqam ? { code: 'sameMaqam', args: { label: `${a.maqam} on ${a.tonicNote}` }, text: '' } : { code: 'sharedNotes', args: { from: summary(a).harmonicLabel, to: summary(b).harmonicLabel, shared: Math.round(harmony * 7), of: 7 }, text: '' }, Math.abs(d) < 1 ? { code: 'sameTempo', args: {}, text: '' } : { code: 'bpmChange', args: { diff: d, time: '' }, text: '' }, { code: b.energy > a.energy + 0.08 ? 'energyUp' : b.energy < a.energy - 0.08 ? 'energyDown' : 'energySteady', args: {}, text: '' }], pitchShiftSemitones: 0 };
};

const sets = new Map();
function buildSet(id, name, ids, curve) {
  const pts = { WEDDING: [[0, 0.65], [0.35, 0.8], [0.5, 0.4], [0.6, 0.55], [1, 0.95]], WARM_UP: [[0, 0.3], [1, 0.65]], PEAK_TIME: [[0, 0.6], [0.3, 0.8], [1, 0.9]], WAVE: [[0, 0.5], [0.25, 0.8], [0.5, 0.5], [0.75, 0.85], [1, 0.6]], FLAT: [[0, 0.6], [1, 0.6]] }[curve];
  const at = (x) => { for (let k = 1; k < pts.length; k++) if (x <= pts[k][0]) { const [p, q] = [pts[k - 1], pts[k]]; return p[1] + ((x - p[0]) / (q[0] - p[0])) * (q[1] - p[1]); } return pts.at(-1)[1]; };
  const list = ids.map((i) => tracks.find((t) => t.id === i)).filter(Boolean);
  const n = list.length;
  const entries = list.map((t, i) => ({ position: i, locked: i === 0, targetEnergy: at(n > 1 ? i / (n - 1) : 0), track: summary(t), transitionToNext: i + 1 < n ? transition(t, list[i + 1]) : null }));
  const sum = entries.reduce((a, e) => a + (e.transitionToNext?.score ?? 0), 0);
  const v = { id, name, curve, weights: { harmony: 0.45, tonic: 0.1, tempo: 0.3, energy: 0.15 }, objective: sum, averageTransition: n > 1 ? sum / (n - 1) : 0, entries, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() };
  sets.set(id, v);
  return v;
}
const ready = tracks.filter((t) => t.status === 'DONE');
buildSet('s1', 'Friday wedding, Zamalek', [ready[5], ready[2], ready[9], ready[1], ready[4], ready[7], ready[0], ready[3], ready[8], ready[10], ready[6]].map((t) => t.id), 'WEDDING');

async function body(req) {
  let s = '';
  for await (const c of req) s += c;
  return s ? JSON.parse(s) : {};
}

const json = (res, code, data) => { res.writeHead(code, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(data)); };

createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  const p = url.pathname;
  if (p.startsWith('/api/')) {
    if (p === '/api/auth/login' || p === '/api/auth/register' || p === '/api/auth/refresh') return json(res, 200, { token: 'mock', user });
    if (p === '/api/auth/me') return json(res, 200, user);
    if (p === '/api/tracks') {
      const q = (url.searchParams.get('q') || '').toLowerCase();
      const ton = url.searchParams.get('tonality');
      const st = url.searchParams.get('status');
      const items = tracks.filter((t) => (!q || `${t.title} ${t.artist}`.toLowerCase().includes(q)) && (!ton || t.tonality === ton) && (!st || t.status === st)).map(view);
      return json(res, 200, { items, total: items.length, page: 0, size: 100 });
    }
    let m = p.match(/^\/api\/tracks\/([^/]+)$/);
    if (m) {
      const t = tracks.find((x) => x.id === m[1]);
      const curve = Array.from({ length: 8 }, (_, k) => ({ start: k * 24, end: (k + 1) * 24, energy: Math.max(0.1, Math.min(1, t.energy + Math.sin(k) * 0.15)) }));
      return json(res, 200, { track: view(t), energyCurve: curve, maqamCandidates: t.maqam ? [{ name: t.maqam, arabic: '', confidence: 0.78 }, { name: 'Kurd', arabic: '', confidence: 0.12 }, { name: 'Saba', arabic: '', confidence: 0.06 }] : ARAB_POP.has(t.artist) ? (t.keyMode === 'minor' ? [{ name: 'Nahawand', arabic: '', confidence: 0.41 }, { name: 'Ajam', arabic: '', confidence: 0.3 }] : [{ name: 'Ajam', arabic: '', confidence: 0.44 }, { name: 'Nahawand', arabic: '', confidence: 0.28 }]) : [], tonicHz: 146.8, tonicCents: t.maqam ? t._tonicCents : t.keyTonicPc * 100, quarterToneRatio: 0.2, pitchHistogram: histogram(t), maqamTimeline: journey(t), modelVersion: 'mock' });
    }
    m = p.match(/^\/api\/mix\/next\/([^/]+)$/);
    if (m) {
      const a = tracks.find((x) => x.id === m[1]);
      return json(res, 200, ready.filter((t) => t.id !== a.id).map((b) => ({ track: summary(b), transition: transition(a, b) })).sort((x, y) => y.transition.score - x.transition.score));
    }
    if (p === '/api/sets' && req.method === 'GET') return json(res, 200, [...sets.values()].map((s) => ({ id: s.id, name: s.name, curve: s.curve, trackCount: s.entries.length, updatedAt: s.updatedAt })));
    if (p === '/api/sets' && req.method === 'POST') { const b = await body(req); return json(res, 201, buildSet(randomUUID(), b.name, b.trackIds, b.curve)); }
    m = p.match(/^\/api\/sets\/([^/]+)$/);
    if (m && req.method === 'GET') return json(res, 200, sets.get(m[1]));
    if (m && req.method === 'PUT') { const b = await body(req); return json(res, 200, buildSet(m[1], b.name, b.trackIds, b.curve)); }
    return json(res, 404, { status: 404, title: 'Not Found', detail: `mock has no ${p}` });
  }
  const file = p === '/' || !extname(p) ? 'index.html' : p.slice(1);
  try {
    const data = await readFile(join(DIST, file));
    res.writeHead(200, { 'Content-Type': TYPES[extname(file)] || 'application/octet-stream' });
    res.end(data);
  } catch {
    res.writeHead(404);
    res.end();
  }
}).listen(PORT, () => console.log(`mock UI on http://localhost:${PORT}`));
