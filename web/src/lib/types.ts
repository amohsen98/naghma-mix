export type Tier = 'FREE' | 'DJ' | 'PRO' | 'AGENCY';
export type Tonality = 'WESTERN' | 'ARABIC_MODAL';
export type TrackStatus = 'AWAITING_UPLOAD' | 'QUEUED' | 'DONE' | 'FAILED';
export type Curve = 'WARM_UP' | 'PEAK_TIME' | 'WEDDING' | 'WAVE' | 'FLAT';
export type Direction = 'up' | 'steady' | 'down';

export interface User {
  id: string;
  email: string;
  displayName: string;
  tier: Tier;
  locale: 'en' | 'ar';
  monthlyTrackQuota: number;
  setBuilder: boolean;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface TrackView {
  id: string;
  title: string;
  artist: string | null;
  filename: string;
  status: TrackStatus;
  error: string | null;
  createdAt: string;
  analyzedAt: string | null;
  durationSec: number | null;
  bpm: number | null;
  lufs: number | null;
  energy: number | null;
  tonality: Tonality | null;
  keyTonicPc: number | null;
  keyMode: 'major' | 'minor' | null;
  camelot: string | null;
  keyConfidence: number | null;
  maqam: string | null;
  maqamConfidence: number | null;
  maqamUncertain: boolean | null;
  tonicNote: string | null;
  editedFields: string[];
}

export interface EnergyPoint {
  start: number;
  end: number;
  energy: number;
}

export interface Candidate {
  name: string;
  arabic: string;
  confidence: number;
}

/** One stretch of the song in a single maqam; maqam is null where there is no clear melody. */
export interface MaqamSection {
  start: number;
  end: number;
  maqam: string | null;
  arabic: string | null;
  tonicNote: string | null;
  tonicCents: number | null;
  confidence: number;
  uncertain: boolean;
}

export interface TrackDetail {
  track: TrackView;
  energyCurve: EnergyPoint[];
  maqamCandidates: Candidate[];
  tonicHz: number | null;
  tonicCents: number | null;
  quarterToneRatio: number | null;
  pitchHistogram: number[];
  maqamTimeline: MaqamSection[];
  modelVersion: string | null;
}

export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface UploadSlot {
  filename: string;
  trackId: string | null;
  duplicate: boolean;
  uploadUrl: string | null;
  contentType: string | null;
}

export interface UploadResponse {
  slots: UploadSlot[];
  quotaRemaining: number;
}

export interface CompleteResult {
  trackId: string;
  status: string;
  error: string | null;
}

export interface TrackSummary {
  id: string;
  title: string;
  artist: string | null;
  bpm: number | null;
  energy: number | null;
  tonality: Tonality | null;
  camelot: string | null;
  maqam: string | null;
  harmonicLabel: string;
}

export interface Reason {
  code: string;
  args: Record<string, string | number>;
  text: string;
}

export interface Transition {
  score: number;
  components: Record<'harmony' | 'tonic' | 'tempo' | 'energy', number>;
  reasons: Reason[];
  pitchShiftSemitones: number;
}

export interface Suggestion {
  track: TrackSummary;
  transition: Transition;
}

export interface SetEntry {
  position: number;
  locked: boolean;
  targetEnergy: number;
  track: TrackSummary;
  transitionToNext: Transition | null;
}

export interface SetView {
  id: string;
  name: string;
  curve: Curve;
  weights: Record<string, number>;
  objective: number;
  averageTransition: number;
  entries: SetEntry[];
  createdAt: string | null;
  updatedAt: string;
}

export interface SetListItem {
  id: string;
  name: string;
  curve: Curve;
  trackCount: number;
  updatedAt: string;
}

export interface Weights {
  harmony: number;
  tonic: number;
  tempo: number;
  energy: number;
}
