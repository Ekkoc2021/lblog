import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import { message } from 'antd';
import type { Category, Tag as TagType, Series, Post } from '../types';
import { getCategories, getTags, getSeries, getHotPosts } from '../services/api';

interface SiteData {
  categories: Category[];
  tags: TagType[];
  seriesList: Series[];
  hotPosts: Post[];
}

const SiteDataContext = createContext<SiteData | null>(null);

export function SiteDataProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<SiteData>({ categories: [], tags: [], seriesList: [], hotPosts: [] });

  useEffect(() => {
    Promise.all([
      getCategories(10).then(r => r.data),
      getTags(20).then(r => r.data),
      getSeries(5).then(r => r.data),
      getHotPosts(5).then(r => r.data),
    ]).then(([categories, tags, seriesList, hotPosts]) => {
      setData({ categories, tags, seriesList, hotPosts });
    }).catch((e: Error) => message.error(e.message));
  }, []);

  return <SiteDataContext.Provider value={data}>{children}</SiteDataContext.Provider>;
}

export function useSiteData(): SiteData {
  const ctx = useContext(SiteDataContext);
  if (!ctx) throw new Error('useSiteData must be used within SiteDataProvider');
  return ctx;
}
