import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';
import type { User } from '../types';
import { login as apiLogin, register as apiRegister, logout as apiLogout, getCurrentUser } from '../services/api';
import type { RegisterRequest } from '../types';

interface AuthContextType {
  token: string | null;
  isAuthenticated: boolean;
  user: User | null;
  login: (username: string, password: string) => Promise<string | true>;
  register: (data: RegisterRequest) => Promise<string | true>;
  logout: () => void;
  updateTokens: (accessToken: string, refreshToken: string) => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => sessionStorage.getItem('lblog_access_token'));
  const [user, setUser] = useState<User | null>(null);

  // 页面刷新后，如果 token 还在，重新加载用户信息
  useEffect(() => {
    const t = sessionStorage.getItem('lblog_access_token');
    if (!t) return;
    getCurrentUser()
      .then(res => setUser(res.data))
      .catch(() => {
        sessionStorage.removeItem('lblog_access_token');
        sessionStorage.removeItem('lblog_refresh_token');
        setToken(null);
      });
  }, []);

  const login = useCallback(async (username: string, password: string): Promise<string | true> => {
    try {
      const res = await apiLogin(username, password);
      if (res.data?.accessToken) {
        // 存双 token
        sessionStorage.setItem('lblog_access_token', res.data.accessToken);
        sessionStorage.setItem('lblog_refresh_token', res.data.refreshToken);
        setToken(res.data.accessToken);
        // 保存用户信息
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
        sessionStorage.setItem('lblog_access_token', res.data.accessToken);
        sessionStorage.setItem('lblog_refresh_token', res.data.refreshToken);
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
    // 尝试调后端登出（不 await，不阻塞 UI）
    apiLogout().catch(() => {});
    // 清本地
    sessionStorage.removeItem('lblog_access_token');
    sessionStorage.removeItem('lblog_refresh_token');
    setToken(null);
    setUser(null);
  }, []);

  const updateTokens = useCallback((accessToken: string, refreshToken: string) => {
    sessionStorage.setItem('lblog_access_token', accessToken);
    sessionStorage.setItem('lblog_refresh_token', refreshToken);
    setToken(accessToken);
  }, []);

  return (
    <AuthContext.Provider value={{ token, isAuthenticated: !!token, user, login, register, logout, updateTokens }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
