import { useState, type FormEvent } from 'react';
import { ApiError } from '../lib/api';
import { useAuth } from '../lib/auth';
import { useI18n } from '../lib/i18n';
import { Wordmark } from '../components/Wordmark';

export function AuthPage() {
  const { t, lang, setLang } = useI18n();
  const { signIn, register } = useAuth();
  const [mode, setMode] = useState<'in' | 'up'>('in');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mode === 'in') await signIn(email, password);
      else await register(email, password, name, lang);
    } catch (err) {
      setError(err instanceof ApiError && err.status === 0 ? t('errorNetwork') : (err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth">
      <div className="auth-art" aria-hidden="true">
        <Wordmark large />
        <p className="auth-pitch">{t('authPitch')}</p>
      </div>
      <form className="auth-form stack" onSubmit={submit}>
        <h1>{mode === 'in' ? t('signIn') : t('createAccount')}</h1>
        {mode === 'up' && (
          <label>{t('displayName')}<input required maxLength={80} value={name} onChange={(e) => setName(e.target.value)} autoComplete="nickname" /></label>
        )}
        <label>{t('email')}<input required type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" dir="ltr" /></label>
        <label>{t('password')}<input required type="password" minLength={8} value={password} onChange={(e) => setPassword(e.target.value)} autoComplete={mode === 'in' ? 'current-password' : 'new-password'} dir="ltr" /></label>
        {error && <p className="err" role="alert">{error}</p>}
        <button className="btn" disabled={busy}>{mode === 'in' ? t('signIn') : t('createAccount')}</button>
        <button type="button" className="btn-ghost" onClick={() => setMode(mode === 'in' ? 'up' : 'in')}>
          {mode === 'in' ? t('needAccount') : t('haveAccount')}
        </button>
        <button type="button" className="btn-ghost lang" onClick={() => setLang(lang === 'ar' ? 'en' : 'ar')}>{t('language')}</button>
      </form>
    </main>
  );
}
