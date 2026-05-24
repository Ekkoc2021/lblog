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

// 检测 HttpOnly Cookie 是否可用（发请求不带 Authorization，看 Cookie 能否通过验证）
async function cookieAuthWorks(): Promise<User | null> {
  try {
    const res = await fetch('/api/v1/auth/me');
    if (res.ok) {
      const json = await res.json();
      return json.data ?? null;
    }
  } catch { /* 网络不可用 */ }
  return null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<User | null>(null);

  // 页面刷新：Cookie 优先 → localStorage 兜底
  useEffect(() => {
    cookieAuthWorks().then(u => {
      if (u) {
        setToken('*cookie*');
        setUser(u);
      } else {
        // Cookie 不可用，降级到 localStorage
        const t = getAccessToken();
        if (t) {
          setToken(t);
          getCurrentUser()
            .then(res => setUser(res.data))
            .catch(() => { clearTokens(); setToken(null); });
        }
      }
    });
  }, []);

  const login = useCallback(async (username: string, password: string): Promise<string | true> => {
    try {
      const res = await apiLogin(username, password);
      if (!res.data?.accessToken) return res.message || '登录失败';

      // 检测 Cookie 是否生效
      const u = await cookieAuthWorks();
      if (u) {
        // Cookie 可用：只用 Cookie，不写 localStorage
        setToken('*cookie*');
        setUser(u);
      } else {
        // Cookie 禁用：降级到 localStorage
        setTokens(res.data.accessToken, res.data.refreshToken);
        setToken(res.data.accessToken);
        setUser(res.data.user);
      }
      return true;
    } catch (e) {
      return e instanceof Error ? e.message : '登录失败';
    }
  }, []);

  const register = useCallback(async (data: RegisterRequest): Promise<string | true> => {
    try {
      const res = await apiRegister(data);
      if (!res.data?.accessToken) return res.message || '注册失败';

      const u = await cookieAuthWorks();
      if (u) {
        setToken('*cookie*');
        setUser(u);
      } else {
        setTokens(res.data.accessToken, res.data.refreshToken);
        setToken(res.data.accessToken);
        setUser(res.data.user);
      }
      return true;
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
