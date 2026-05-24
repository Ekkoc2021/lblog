import { createContext, useContext, useState, useEffect, useCallback, type ReactNode } from 'react';
import type { Category, Tag as TagType, Series, Post } from '../types';
import { getCategories, getTags, getSeries, getHotPosts, getSiteConfig } from '../services/api';

interface SiteData {
  categories: Category[];
  tags: TagType[];
  seriesList: Series[];
  hotPosts: Post[];
  imageBaseUrl: string;
  imageMaxSize: number;
  refreshSiteData: () => void;
}

const defaultSiteData: SiteData = {
  categories: [], tags: [], seriesList: [], hotPosts: [],
  imageBaseUrl: '', imageMaxSize: 10485760,
  refreshSiteData: () => {},
};

const SiteDataContext = createContext<SiteData | null>(null);

export function SiteDataProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<SiteData>(defaultSiteData);

  const loadSiteData = useCallback(() => {
    Promise.all([
      getCategories(10).then(r => r.data),
      getTags(20).then(r => r.data),
      getSeries(5).then(r => r.data),
      getHotPosts(5).then(r => r.data),
      getSiteConfig().then(r => r.data),
    ]).then(([categories, tags, seriesList, hotPosts, config]) => {
      setData(prev => ({
        ...prev,
        categories, tags, seriesList, hotPosts,
        imageBaseUrl: config?.imageBaseUrl ?? '',
        imageMaxSize: config?.imageMaxSize ?? 10485760,
        refreshSiteData: loadSiteData,
      }));
    }).catch(() => { /* ignore */ });
  }, []);

  useEffect(() => { loadSiteData(); }, [loadSiteData]);

  return <SiteDataContext.Provider value={data}>{children}</SiteDataContext.Provider>;
}

export function useSiteData(): SiteData {
  const ctx = useContext(SiteDataContext);
  if (!ctx) throw new Error('useSiteData must be used within SiteDataProvider');
  return ctx;
}
