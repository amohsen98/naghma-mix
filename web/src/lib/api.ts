import type {
  AuthResponse, CompleteResult, Curve, Direction, Page, SetListItem, SetView, Suggestion,
  Tier, TrackDetail, TrackView, UploadResponse, User, Weights,
} from './types';

const TOKEN_KEY = 'naghma.token';

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

let token: string | null = readToken();
let onUnauthorized: () => void = () => {};

function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(t: string | null) {
  token = t;
  try {
    if (t) localStorage.setItem(TOKEN_KEY, t);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* storage unavailable: keep the token in memory only */
  }
}

export function getToken() {
  return token;
}

export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;
  let res: Response;
  try {
    res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, 'network');
  }
  if (res.status === 401 && token) onUnauthorized();
  if (!res.ok) {
    let detail = res.statusText;
    try {
      const problem = await res.json();
      detail = problem.detail || problem.title || detail;
    } catch {
      /* body was not JSON */
    }
    throw new ApiError(res.status, detail);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

function qs(params: Record<string, string | number | undefined | null>) {
  const s = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== null && v !== '') s.set(k, String(v));
  const str = s.toString();
  return str ? `?${str}` : '';
}

export interface FileRequest {
  filename: string;
  sizeBytes: number;
  sha256: string;
}

export interface SetRequest {
  name: string;
  trackIds: string[];
  curve: Curve;
  locked?: Record<string, number>;
  weights?: Partial<Weights>;
  keepOrder?: boolean;
}

export const api = {
  register: (b: { email: string; password: string; displayName: string; locale: string }) =>
    request<AuthResponse>('POST', '/api/auth/register', b),
  login: (b: { email: string; password: string }) => request<AuthResponse>('POST', '/api/auth/login', b),
  refresh: () => request<AuthResponse>('POST', '/api/auth/refresh'),
  me: () => request<User>('GET', '/api/auth/me'),
  updateMe: (b: { displayName?: string; locale?: string }) => request<AuthResponse>('PUT', '/api/auth/me', b),
  checkout: (tier: Tier, returnUrl: string) =>
    request<{ checkoutUrl: string; reference: string }>('POST', '/api/billing/checkout', { tier, returnUrl }),

  startUpload: (files: FileRequest[], keepAudio = false) =>
    request<UploadResponse>('POST', '/api/uploads', { files, keepAudio }),
  completeUpload: (trackIds: string[]) => request<CompleteResult[]>('POST', '/api/uploads/complete', { trackIds }),

  tracks: (p: { q?: string; status?: string; tonality?: string; page?: number; size?: number }) =>
    request<Page<TrackView>>('GET', `/api/tracks${qs(p)}`),
  track: (id: string) => request<TrackDetail>('GET', `/api/tracks/${id}`),
  editTags: (id: string, b: Record<string, string | number | undefined>) =>
    request<TrackView>('PATCH', `/api/tracks/${id}/tags`, b),
  reanalyze: (id: string) => request<TrackView>('POST', `/api/tracks/${id}/reanalyze`),
  deleteTrack: (id: string) => request<void>('DELETE', `/api/tracks/${id}`),

  next: (id: string, o: { direction: Direction; limit?: number } & Partial<Weights>) =>
    request<Suggestion[]>('GET', `/api/mix/next/${id}${qs(o)}`),

  sets: () => request<SetListItem[]>('GET', '/api/sets'),
  set: (id: string) => request<SetView>('GET', `/api/sets/${id}`),
  createSet: (b: SetRequest) => request<SetView>('POST', '/api/sets', b),
  updateSet: (id: string, b: SetRequest) => request<SetView>('PUT', `/api/sets/${id}`, b),
  deleteSet: (id: string) => request<void>('DELETE', `/api/sets/${id}`),

  /** Exports need the auth header, so they are fetched and saved as a blob. */
  async downloadExport(id: string, format: 'm3u' | 'rekordbox', fallbackName: string) {
    const res = await fetch(`/api/sets/${id}/export?format=${format}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) throw new ApiError(res.status, res.statusText);
    const blob = await res.blob();
    const disposition = res.headers.get('Content-Disposition') || '';
    const match = /filename\*=UTF-8''([^;]+)/.exec(disposition);
    const name = match ? decodeURIComponent(match[1]) : `${fallbackName}.${format === 'm3u' ? 'm3u' : 'xml'}`;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    a.click();
    URL.revokeObjectURL(url);
  },
};
