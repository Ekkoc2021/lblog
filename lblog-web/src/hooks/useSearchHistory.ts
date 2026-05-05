import { useState } from 'react';

const STORAGE_KEY = 'lblog_search_history';
const MAX_ITEMS = 10;

export function useSearchHistory() {
  const [history, setHistory] = useState<string[]>(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch {
      return [];
    }
  });

  const addToHistory = (keyword: string) => {
    if (!keyword.trim()) return;
    setHistory(prev => {
      const filtered = prev.filter(k => k !== keyword);
      const updated = [keyword, ...filtered].slice(0, MAX_ITEMS);
      localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
      return updated;
    });
  };

  const removeFromHistory = (keyword: string) => {
    setHistory(prev => {
      const updated = prev.filter(k => k !== keyword);
      localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
      return updated;
    });
  };

  const clearHistory = () => {
    localStorage.removeItem(STORAGE_KEY);
    setHistory([]);
  };

  return { history, addToHistory, removeFromHistory, clearHistory };
}
