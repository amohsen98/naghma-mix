import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './lib/auth';
import { useI18n } from './lib/i18n';
import { AuthPage } from './pages/AuthPage';
import { LibraryPage } from './pages/LibraryPage';
import { NextPage } from './pages/NextPage';
import { PlansPage } from './pages/PlansPage';
import { SetPage } from './pages/SetPage';
import { NewSetPage, SetsPage } from './pages/SetsPage';
import { Wordmark } from './components/Wordmark';

export function App() {
  const { user, ready, signOut } = useAuth();
  const { t, lang, setLang } = useI18n();

  if (!ready) return <p className="boot muted">{t('loading')}</p>;
  if (!user) return <AuthPage />;

  return (
    <div className="shell">
      <a className="skip" href="#main">Skip to content</a>
      <nav className="nav" aria-label="Main">
        <Wordmark />
        <NavLink to="/" end>{t('library')}</NavLink>
        <NavLink to="/next">{t('next')}</NavLink>
        <NavLink to="/sets">{t('sets')}</NavLink>
        <NavLink to="/plans">{t('plans')}</NavLink>
        <div className="nav-foot">
          <span className="who">
            <span className="ellipsis">{user.displayName}</span>
            <span className="muted small">{t(user.tier)}</span>
          </span>
          <button className="btn-ghost" onClick={() => setLang(lang === 'ar' ? 'en' : 'ar')}>{t('language')}</button>
          <button className="btn-ghost" onClick={signOut}>{t('signOut')}</button>
        </div>
      </nav>
      <main id="main" className="main">
        <Routes>
          <Route path="/" element={<LibraryPage />} />
          <Route path="/next" element={<NextPage />} />
          <Route path="/next/:trackId" element={<NextPage />} />
          <Route path="/sets" element={<SetsPage />} />
          <Route path="/sets/new" element={<NewSetPage />} />
          <Route path="/sets/:setId" element={<SetPage />} />
          <Route path="/plans" element={<PlansPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}
