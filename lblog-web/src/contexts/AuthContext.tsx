import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';
import type { User } from '../types';
import { login as apiLogin, register as apiRegister, logout as apiLogout, getCurrentUser } from '../services/api';
import { getAccessToken, setTokens, clearTokens } from '../services/tokenStore';
import type { RegisterRequest } from '../types';

interface AuthContextType {
  token: string | null;
  isAuthenticated: boolean;
  user: User | null;
  login: (username: string, password: string) => Promise<string | true>;
  register: (data: RegisterRequest) => Promise<string | true>;
  logout: () => void;
  updateTokens: (accessToken: string, refreshToken: string) => void;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => getAccessToken());
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    const t = getAccessToken();
    if (!t) return;
    getCurrentUser()
      .then(res => setUser(res.data))
      .catch(() => {
        clearTokens();
        setToken(null);
      });
  }, []);

  const login = useCallback(async (username: string, password: string): Promise<string | true> => {
    try {
      const res = await apiLogin(username, password);
      if (res.data?.accessToken) {
        setTokens(res.data.accessToken, res.data.refreshToken);
        setToken(res.data.accessToken);
        setUser(res.data.user);
        return true;
      }
      return res.message || '登录失败';
    } catch (e) {
      return e instanceof Error ? e.message : '登录失败';
    }
  }, []);

  const register = useCallback(async (data: RegisterRequest): Promise<string | true> => {
    try {
      const res = await apiRegister(data);
      if (res.data?.accessToken) {
        setTokens(res.data.accessToken, res.data.refreshToken);
        setToken(res.data.accessToken);
        setUser(res.data.user);
        return true;
      }
      return res.message || '注册失败';
    } catch (e) {
      return e instanceof Error ? e.message : '注册失败';
    }
  }, []);

  const logout = useCallback(() => {
    apiLogout().catch(() => {});
    clearTokens();
    setToken(null);
    setUser(null);
  }, []);

  const updateTokens = useCallback((accessToken: string, refreshToken: string) => {
    setTokens(accessToken, refreshToken);
    setToken(accessToken);
  }, []);

  const refreshUser = useCallback(async () => {
    try {
      const res = await getCurrentUser();
      setUser(res.data);
    } catch {
      // ignore
    }
  }, []);

  return (
    <AuthContext.Provider value={{ token, isAuthenticated: !!token, user, login, register, logout, updateTokens, refreshUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
