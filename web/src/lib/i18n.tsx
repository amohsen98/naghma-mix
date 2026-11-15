import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

export type Lang = 'en' | 'ar';

const en = {
  appName: 'Naghma Mix',
  library: 'Library',
  next: 'What next',
  sets: 'Sets',
  plans: 'Plans',
  signOut: 'Sign out',
  language: 'العربية',

  signIn: 'Sign in',
  createAccount: 'Create account',
  email: 'Email',
  password: 'Password',
  displayName: 'Your DJ name',
  haveAccount: 'Already have an account? Sign in',
  needAccount: 'New here? Create an account',
  authPitch: 'Tag your library by key and maqam, then build sets that flow for any crowd.',

  dropTitle: 'Drop tracks here or choose files',
  dropHint: 'MP3, WAV, FLAC or M4A, up to 30 MB each. Audio is deleted after analysis.',
  chooseFiles: 'Choose files',
  hashing: 'Checking',
  uploading: 'Uploading',
  queuedForAnalysis: 'Queued for analysis',
  duplicate: 'Already in your library',
  quotaLeft: '{n} analyses left this month',
  quotaReached: 'Monthly limit reached. Upgrade to analyze more tracks.',
  rejectFormat: 'Unsupported format',
  rejectSize: 'Larger than 30 MB',

  search: 'Search title or artist',
  allTracks: 'All tracks',
  westernOnly: 'Western',
  arabicOnly: 'Arabic (maqam)',
  title: 'Title',
  bpm: 'BPM',
  key: 'Key',
  maqam: 'Maqam',
  energy: 'Energy',
  status: 'Status',
  AWAITING_UPLOAD: 'Waiting for upload',
  QUEUED: 'Analyzing',
  DONE: 'Ready',
  FAILED: 'Failed',
  emptyLibrary: 'Your library is empty. Upload a few tracks to see their key, maqam, tempo and energy.',
  noMatches: 'No tracks match this search.',
  selected: '{n} selected',
  buildSet: 'Build a set',
  clearSelection: 'Clear',
  uncertain: 'uncertain',
  edited: 'edited by you',
  loadMore: 'Show more',

  details: 'Track details',
  close: 'Close',
  candidates: 'Maqam candidates',
  pitchProfile: 'Pitch profile',
  pitchProfileHint: 'How often each pitch is sung or played. Bars between the semitone lines are quarter tones.',
  energyCurve: 'Energy through the track',
  correctTags: 'Correct the tags',
  correctHint: 'Your corrections are kept when the track is re-analyzed and help improve detection.',
  tonality: 'Tonality',
  WESTERN: 'Western',
  ARABIC_MODAL: 'Arabic (maqam)',
  keyTonic: 'Key tonic',
  mode: 'Mode',
  major: 'major',
  minor: 'minor',
  maqamTonic: 'Maqam tonic',
  artist: 'Artist',
  saveTags: 'Save tags',
  tagsSaved: 'Tags saved',
  suggestNext: 'Suggest what to play next',
  reanalyze: 'Re-analyze',
  deleteTrack: 'Delete track',
  confirmDelete: 'Delete this track and its analysis?',
  duration: 'Length',
  loudness: 'Loudness',
  noAnalysis: 'Analysis is not ready yet.',

  nowPlaying: 'Playing now',
  pickTrack: 'Pick the track you are playing',
  direction: 'Where should the energy go?',
  up: 'Build up',
  steady: 'Hold',
  down: 'Bring down',
  weights: 'What matters most',
  harmony: 'Harmony',
  tonic: 'Tonic',
  tempo: 'Tempo',
  match: 'match',
  noSuggestions: 'No other analyzed tracks yet.',
  pitchShift: 'pitch {n} semitone',

  newSetHint: 'Select tracks in your library, then choose Build a set.',
  setName: 'Set name',
  curve: 'Energy shape',
  WARM_UP: 'Warm-up',
  PEAK_TIME: 'Peak time',
  WEDDING: 'Wedding night',
  WAVE: 'Waves',
  FLAT: 'Steady',
  createSet: 'Create set',
  rebuild: 'Reorder for best flow',
  saveOrder: 'Save this order',
  exportM3u: 'Export M3U',
  exportRekordbox: 'Export rekordbox XML',
  deleteSet: 'Delete set',
  confirmDeleteSet: 'Delete this set? Your tracks stay in the library.',
  lock: 'Keep in this position',
  unlock: 'Let the builder move it',
  moveUp: 'Move up',
  moveDown: 'Move down',
  avgFlow: 'Average transition',
  transitionQuality: 'Transition quality',
  tracksCount: '{n} tracks',
  targetVsActual: 'Target energy and each track’s energy',
  target: 'Target',
  actual: 'Track',
  noSets: 'No sets yet.',
  setBuilderPaid: 'The set builder is part of the DJ and Pro plans.',
  seePlans: 'See plans',
  updated: 'Updated {d}',

  plansTitle: 'Choose a plan',
  perMonth: 'analyses per month',
  unlimited: 'Unlimited',
  currentPlan: 'Your plan',
  upgrade: 'Upgrade',
  FREE: 'Free',
  DJ: 'DJ',
  PRO: 'Pro',
  AGENCY: 'Agency',
  planFree: 'Tags for 50 tracks a month and next-track suggestions.',
  planDj: 'Tags for 1,500 tracks a month, the set builder and exports.',
  planPro: 'Unlimited analysis for busy DJs, in Egypt and the Gulf.',
  planAgency: 'Team seats for DJ companies.',
  upgraded: 'Your plan is now {tier}.',

  errorNetwork: 'Can’t reach the server. Check your connection and try again.',
  retry: 'Try again',
  loading: 'Loading',
};

type Dict = typeof en;

const ar: Dict = {
  appName: 'نغمة ميكس',
  library: 'المكتبة',
  next: 'التالي',
  sets: 'السِتّات',
  plans: 'الباقات',
  signOut: 'تسجيل الخروج',
  language: 'English',

  signIn: 'تسجيل الدخول',
  createAccount: 'إنشاء حساب',
  email: 'البريد الإلكتروني',
  password: 'كلمة المرور',
  displayName: 'اسمك كدي جي',
  haveAccount: 'عندك حساب؟ سجّل الدخول',
  needAccount: 'جديد؟ أنشئ حسابًا',
  authPitch: 'صنّف مكتبتك حسب السلّم والمقام، وابنِ سِتّات تنساب مع أي جمهور.',

  dropTitle: 'اسحب المقاطع هنا أو اختر ملفات',
  dropHint: 'MP3 أو WAV أو FLAC أو M4A، حتى 30 ميجابايت لكل ملف. يُحذف الصوت بعد التحليل.',
  chooseFiles: 'اختر ملفات',
  hashing: 'جارٍ الفحص',
  uploading: 'جارٍ الرفع',
  queuedForAnalysis: 'في انتظار التحليل',
  duplicate: 'موجود بالفعل في مكتبتك',
  quotaLeft: 'متبقٍ {n} تحليل هذا الشهر',
  quotaReached: 'وصلت للحد الشهري. رقِّ باقتك لتحليل مقاطع أكثر.',
  rejectFormat: 'صيغة غير مدعومة',
  rejectSize: 'أكبر من 30 ميجابايت',

  search: 'ابحث بالعنوان أو الفنان',
  allTracks: 'كل المقاطع',
  westernOnly: 'غربي',
  arabicOnly: 'عربي (مقام)',
  title: 'العنوان',
  bpm: 'BPM',
  key: 'السلّم',
  maqam: 'المقام',
  energy: 'الطاقة',
  status: 'الحالة',
  AWAITING_UPLOAD: 'في انتظار الرفع',
  QUEUED: 'جارٍ التحليل',
  DONE: 'جاهز',
  FAILED: 'فشل',
  emptyLibrary: 'مكتبتك فارغة. ارفع بعض المقاطع لترى السلّم والمقام والسرعة والطاقة.',
  noMatches: 'لا توجد مقاطع تطابق البحث.',
  selected: 'تم اختيار {n}',
  buildSet: 'ابنِ سِت',
  clearSelection: 'إلغاء',
  uncertain: 'غير مؤكد',
  edited: 'عدّلته أنت',
  loadMore: 'عرض المزيد',

  details: 'تفاصيل المقطع',
  close: 'إغلاق',
  candidates: 'المقامات المحتملة',
  pitchProfile: 'توزيع النغمات',
  pitchProfileHint: 'كم مرة تُغنّى أو تُعزف كل نغمة. الأعمدة بين خطوط أنصاف التون هي أرباع تون.',
  energyCurve: 'الطاقة على امتداد المقطع',
  correctTags: 'صحّح التصنيف',
  correctHint: 'تصحيحاتك تبقى عند إعادة التحليل وتساعد على تحسين الكشف.',
  tonality: 'النوع',
  WESTERN: 'غربي',
  ARABIC_MODAL: 'عربي (مقام)',
  keyTonic: 'قرار السلّم',
  mode: 'النمط',
  major: 'ماجير',
  minor: 'مينير',
  maqamTonic: 'قرار المقام',
  artist: 'الفنان',
  saveTags: 'احفظ التصنيف',
  tagsSaved: 'تم حفظ التصنيف',
  suggestNext: 'اقترح ما يُشغَّل بعده',
  reanalyze: 'أعد التحليل',
  deleteTrack: 'احذف المقطع',
  confirmDelete: 'حذف هذا المقطع وتحليله؟',
  duration: 'المدة',
  loudness: 'الجهارة',
  noAnalysis: 'التحليل لم يكتمل بعد.',

  nowPlaying: 'يُشغَّل الآن',
  pickTrack: 'اختر المقطع الذي تشغّله',
  direction: 'إلى أين تتجه الطاقة؟',
  up: 'ارفعها',
  steady: 'ثبّتها',
  down: 'اهدِّها',
  weights: 'ما الأهم لك',
  harmony: 'الانسجام',
  tonic: 'القرار',
  tempo: 'السرعة',
  match: 'توافق',
  noSuggestions: 'لا توجد مقاطع أخرى محللة بعد.',
  pitchShift: 'تغيير الطبقة {n} نصف تون',

  newSetHint: 'اختر مقاطع من مكتبتك ثم اضغط «ابنِ سِت».',
  setName: 'اسم السِت',
  curve: 'شكل الطاقة',
  WARM_UP: 'تسخين',
  PEAK_TIME: 'ذروة',
  WEDDING: 'ليلة فرح',
  WAVE: 'موجات',
  FLAT: 'ثابت',
  createSet: 'أنشئ السِت',
  rebuild: 'رتّب لأفضل انسياب',
  saveOrder: 'احفظ هذا الترتيب',
  exportM3u: 'تصدير M3U',
  exportRekordbox: 'تصدير rekordbox XML',
  deleteSet: 'احذف السِت',
  confirmDeleteSet: 'حذف هذا السِت؟ ستبقى المقاطع في مكتبتك.',
  lock: 'ثبّته في مكانه',
  unlock: 'اسمح بتحريكه',
  moveUp: 'لأعلى',
  moveDown: 'لأسفل',
  avgFlow: 'متوسط الانتقال',
  transitionQuality: 'جودة الانتقال',
  tracksCount: '{n} مقطع',
  targetVsActual: 'الطاقة المستهدفة وطاقة كل مقطع',
  target: 'المستهدف',
  actual: 'المقطع',
  noSets: 'لا توجد سِتّات بعد.',
  setBuilderPaid: 'منشئ السِتّات متاح في باقتي دي جي وبرو.',
  seePlans: 'شاهد الباقات',
  updated: 'آخر تحديث {d}',

  plansTitle: 'اختر باقة',
  perMonth: 'تحليل في الشهر',
  unlimited: 'بلا حدود',
  currentPlan: 'باقتك',
  upgrade: 'رقِّ الباقة',
  FREE: 'مجانية',
  DJ: 'دي جي',
  PRO: 'برو',
  AGENCY: 'شركات',
  planFree: 'تصنيف 50 مقطعًا شهريًا واقتراحات المقطع التالي.',
  planDj: 'تصنيف 1500 مقطع شهريًا ومنشئ السِتّات والتصدير.',
  planPro: 'تحليل بلا حدود للدي جي المشغول في مصر والخليج.',
  planAgency: 'مقاعد لفريق شركات الدي جي.',
  upgraded: 'باقتك الآن {tier}.',

  errorNetwork: 'تعذّر الوصول للخادم. تحقق من الاتصال وحاول مجددًا.',
  retry: 'حاول مجددًا',
  loading: 'جارٍ التحميل',
};

/** Maqam names in Arabic script. */
export const MAQAM_AR: Record<string, string> = {
  Rast: 'راست', Bayati: 'بياتي', Saba: 'صبا', Sikah: 'سيكاه',
  Hijaz: 'حجاز', Nahawand: 'نهاوند', Kurd: 'كرد', Ajam: 'عجم',
};

export type Key = keyof Dict;

interface I18n {
  lang: Lang;
  t: (key: Key, vars?: Record<string, string | number>) => string;
  setLang: (l: Lang) => void;
  maqamName: (m: string | null | undefined) => string;
}

const Ctx = createContext<I18n | null>(null);

function initialLang(): Lang {
  try {
    const saved = localStorage.getItem('naghma.lang');
    if (saved === 'ar' || saved === 'en') return saved;
  } catch {
    /* ignore */
  }
  return navigator.language?.startsWith('ar') ? 'ar' : 'en';
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(initialLang);

  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
  }, [lang]);

  const setLang = useCallback((l: Lang) => {
    setLangState(l);
    try {
      localStorage.setItem('naghma.lang', l);
    } catch {
      /* ignore */
    }
  }, []);

  const value = useMemo<I18n>(() => {
    const dict = lang === 'ar' ? ar : en;
    return {
      lang,
      setLang,
      t: (key, vars) => {
        let s = dict[key] ?? en[key] ?? key;
        if (vars) for (const [k, v] of Object.entries(vars)) s = s.replace(`{${k}}`, String(v));
        return s;
      },
      maqamName: (m) => (!m ? '' : lang === 'ar' ? MAQAM_AR[m] ?? m : m),
    };
  }, [lang, setLang]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useI18n() {
  const v = useContext(Ctx);
  if (!v) throw new Error('useI18n outside provider');
  return v;
}
