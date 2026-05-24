import { createContext, useContext, useState, useEffect, useCallback } from 'react';

type Theme = 'light' | 'dark' | 'warm';

interface ThemeContextType {
  theme: Theme;
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextType>({ theme: 'light', toggleTheme: () => {} });

export const useTheme = () => useContext(ThemeContext);

const themeOrder: Theme[] = ['light', 'dark', 'warm'];

export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [theme, setTheme] = useState<Theme>(() => {
    try {
      const saved = localStorage.getItem('lblog_theme');
      if (saved === 'dark' || saved === 'warm') return saved;
    } catch { /* localStorage 不可用 */ }
    return 'light';
  });

  useEffect(() => {
    try { localStorage.setItem('lblog_theme', theme); } catch { /* */ }
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  const toggleTheme = useCallback(() => {
    setTheme(prev => {
      const idx = themeOrder.indexOf(prev);
      return themeOrder[(idx + 1) % themeOrder.length];
    });
  }, []);

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  );
};
