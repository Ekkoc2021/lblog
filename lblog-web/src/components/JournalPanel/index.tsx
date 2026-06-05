import { useState, useEffect, useRef, useCallback } from 'react';
import { Tabs, message, theme } from 'antd';
import { CloseOutlined, BookOutlined } from '@ant-design/icons';
import { getCalendar, getJournals, getJournalByDate, createJournal, updateJournal, deleteJournal } from '../../services/journalApi';
import CalendarView from './CalendarView';
import TimelineView from './TimelineView';
import JournalEditor from './JournalEditor';
import JournalDetail from './JournalDetail';
import type { JournalEntry, CalendarDay, CreateJournalRequest } from '../../types';

interface Props { onClose: () => void; }

const JournalPanel: React.FC<Props> = ({ onClose }) => {
  const { token } = theme.useToken();
  const [tab, setTab] = useState('calendar');
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [calendarDays, setCalendarDays] = useState<CalendarDay[]>([]);
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [timeline, setTimeline] = useState<JournalEntry[]>([]);
  const [timelinePage, setTimelinePage] = useState(1);
  const [timelineHasMore, setTimelineHasMore] = useState(true);
  const [timelineLoading, setTimelineLoading] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingEntry, setEditingEntry] = useState<JournalEntry | null>(null);
  const [viewingEntry, setViewingEntry] = useState<JournalEntry | null>(null);
  const [calendarLoading, setCalendarLoading] = useState(false);

  const [pos, setPos] = useState(() => {
    try {
      const saved = localStorage.getItem('journalPanelPos');
      if (saved) { const p = JSON.parse(saved); if (p && typeof p.left === 'number') return p; }
    } catch { /* ignore */ }
    return { left: Math.round((window.innerWidth - 420) / 2) + 100, top: Math.round(window.innerHeight / 5) + 60 };
  });
  const posRef = useRef(pos);
  posRef.current = pos;
  const dragging = useRef(false);
  const dragStart = useRef({ x: 0, y: 0, left: 0, top: 0 });

  const fetchCalendar = useCallback(async (y: number, m: number) => {
    setCalendarLoading(true);
    try {
      const res = await getCalendar(y, m);
      setCalendarDays(res.data || []);
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
      setCalendarDays([]);
    }
    finally { setCalendarLoading(false); }
  }, []);

  const fetchTimeline = useCallback(async (page: number, append = false) => {
    setTimelineLoading(true);
    try {
      const res = await getJournals(page, 20);
      const list = res.data || [];
      if (append) { setTimeline(prev => [...prev, ...list]); }
      else { setTimeline(list); }
      setTimelineHasMore(list.length === 20);
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
    finally { setTimelineLoading(false); }
  }, []);

  useEffect(() => { fetchCalendar(year, month); }, [year, month, fetchCalendar]);
  useEffect(() => { if (tab === 'timeline') { fetchTimeline(1); setTimelinePage(1); } }, [tab, fetchTimeline]);

  const handleDateClick = async (dateStr: string) => {
    setSelectedDate(dateStr);
    try {
      const res = await getJournalByDate(dateStr);
      if (res.data) { setViewingEntry(res.data); }
      else { setEditingEntry(null); setEditorOpen(true); }
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  };

  const handleSave = async (data: CreateJournalRequest) => {
    try {
      if (editingEntry) {
        await updateJournal(editingEntry.id, {
          title: data.title,
          content: data.content,
          mood: data.mood,
          moodEmoji: data.moodEmoji,
          weather: data.weather,
        });
        message.success('已更新');
      } else {
        await createJournal(data);
        message.success('已保存');
      }
      setEditorOpen(false);
      setEditingEntry(null);
      fetchCalendar(year, month);
      setSelectedDate(null);
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  };

  const handleEdit = () => {
    if (viewingEntry) { setEditingEntry(viewingEntry); setEditorOpen(true); setViewingEntry(null); }
  };

  const handleDelete = async () => {
    if (!viewingEntry) return;
    try {
      await deleteJournal(viewingEntry.id);
      message.success('已删除');
      setViewingEntry(null);
      fetchCalendar(year, month);
    } catch (e: unknown) { if (e instanceof Error) message.error(e.message); }
  };

  const handleLoadMore = () => { const next = timelinePage + 1; setTimelinePage(next); fetchTimeline(next, true); };

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault(); dragging.current = true;
    dragStart.current = { x: e.clientX, y: e.clientY, left: posRef.current.left, top: posRef.current.top };
    let lx = e.clientX, ly = e.clientY;
    const onMove = (ev: MouseEvent) => { if (!dragging.current) return; lx = ev.clientX; ly = ev.clientY;
      setPos({ left: Math.max(0, Math.min(window.innerWidth - 420, dragStart.current.left + ev.clientX - dragStart.current.x)),
        top: Math.max(0, Math.min(window.innerHeight - 100, dragStart.current.top + ev.clientY - dragStart.current.y)) }); };
    const onUp = () => { dragging.current = false; window.removeEventListener('mousemove', onMove); window.removeEventListener('mouseup', onUp);
      const nl = Math.max(0, Math.min(window.innerWidth - 420, dragStart.current.left + lx - dragStart.current.x));
      const nt = Math.max(0, Math.min(window.innerHeight - 100, dragStart.current.top + ly - dragStart.current.y));
      try { localStorage.setItem('journalPanelPos', JSON.stringify({ left: nl, top: nt })); } catch { /* ignore */ } };
    window.addEventListener('mousemove', onMove); window.addEventListener('mouseup', onUp);
  }, []);

  return (
    <div style={{ position: 'fixed', left: pos.left, top: pos.top, width: 420, maxHeight: '80vh', zIndex: 1000,
      background: token.colorBgElevated, borderRadius: 12, boxShadow: token.boxShadowSecondary,
      display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <div onMouseDown={handleMouseDown} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '10px 16px', cursor: 'grab', userSelect: 'none', borderBottom: `1px solid ${token.colorBorderSecondary}`, flexShrink: 0 }}>
        <span style={{ fontWeight: 600, fontSize: 15 }}>
          <BookOutlined style={{ marginRight: 8 }} />日记本
        </span>
        <CloseOutlined style={{ cursor: 'pointer', color: token.colorTextTertiary }} onClick={onClose} />
      </div>
      <Tabs activeKey={tab} onChange={setTab} size="small" style={{ padding: '0 16px', marginBottom: 0, flexShrink: 0 }}
        items={[{ key: 'calendar', label: '日历' }, { key: 'timeline', label: '时间线' }]} />
      <div style={{ flex: 1, overflow: 'auto', padding: '0 16px 16px' }}>
        {tab === 'calendar' && (
          <CalendarView year={year} month={month} calendarDays={calendarDays} selectedDate={selectedDate}
            loading={calendarLoading}
            onMonthChange={(y, m) => { setYear(y); setMonth(m); }} onDateClick={handleDateClick} />)}
        {tab === 'timeline' && (
          <TimelineView entries={timeline} loading={timelineLoading} hasMore={timelineHasMore}
            onLoadMore={handleLoadMore} onEntryClick={e => setViewingEntry(e)}
            onAddClick={() => { const d = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`; setSelectedDate(d); setEditingEntry(null); setEditorOpen(true); }} />)}
      </div>
      {editorOpen && selectedDate && (
        <JournalEditor open={editorOpen} date={selectedDate} entry={editingEntry}
          onClose={() => { setEditorOpen(false); setEditingEntry(null); }} onSave={handleSave} />)}
      {viewingEntry && (
        <JournalDetail entry={viewingEntry} onClose={() => setViewingEntry(null)}
          onEdit={handleEdit} onDelete={handleDelete} />)}
    </div>
  );
};

export default JournalPanel;
