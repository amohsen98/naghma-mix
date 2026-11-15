import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, getToken, setToken, setUnauthorizedHandler } from './api';
import type { AuthResponse, User } from './types';

interface Auth {
  user: User | null;
  ready: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string, locale: string) => Promise<void>;
  signOut: () => void;
  refresh: () => Promise<User | null>;
}

const Ctx = createContext<Auth | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);

  const accept = useCallback((r: AuthResponse) => {
    setToken(r.token);
    setUser(r.user);
  }, []);

  const signOut = useCallback(() => {
    setToken(null);
    setUser(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(signOut);
    if (!getToken()) {
      setReady(true);
      return;
    }
    api.me().then(setUser).catch(signOut).finally(() => setReady(true));
  }, [signOut]);

  const value = useMemo<Auth>(() => ({
    user,
    ready,
    signOut,
    signIn: async (email, password) => accept(await api.login({ email, password })),
    register: async (email, password, displayName, locale) =>
      accept(await api.register({ email, password, displayName, locale })),
    refresh: async () => {
      try {
        const r = await api.refresh();
        accept(r);
        return r.user;
      } catch {
        return null;
      }
    },
  }), [user, ready, signOut, accept]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth() {
  const v = useContext(Ctx);
  if (!v) throw new Error('useAuth outside provider');
  return v;
}
