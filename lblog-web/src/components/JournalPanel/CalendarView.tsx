import { useMemo } from 'react';
import { Button, Spin, theme } from 'antd';
import { LeftOutlined, RightOutlined } from '@ant-design/icons';
import type { CalendarDay } from '../../types';

interface Props {
  year: number;
  month: number;
  calendarDays: CalendarDay[];
  selectedDate: string | null;
  loading?: boolean;
  onMonthChange: (year: number, month: number) => void;
  onDateClick: (dateStr: string) => void;
}

const WEEKDAY_LABELS = ['一', '二', '三', '四', '五', '六', '日'];

const CalendarView: React.FC<Props> = ({ year, month, calendarDays, selectedDate, loading, onMonthChange, onDateClick }) => {
  const { token } = theme.useToken();

  const emojiMap = useMemo(() => {
    const map: Record<string, string> = {};
    calendarDays.forEach(d => { map[d.journalDate] = d.moodEmoji; });
    return map;
  }, [calendarDays]);

  const daysInMonth = new Date(year, month, 0).getDate();
  const firstDayOfWeek = new Date(year, month - 1, 1).getDay();
  const startOffset = firstDayOfWeek === 0 ? 6 : firstDayOfWeek - 1;

  const cells: (number | null)[] = [];
  for (let i = 0; i < startOffset; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);

  const prevMonth = () => {
    const m = month - 1;
    if (m < 1) onMonthChange(year - 1, 12);
    else onMonthChange(year, m);
  };
  const nextMonth = () => {
    const m = month + 1;
    if (m > 12) onMonthChange(year + 1, 1);
    else onMonthChange(year, m);
  };

  const today = new Date();
  const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;

  return (
    <Spin spinning={!!loading}>
      <div>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8, padding: '0 4px' }}>
          <Button type="text" size="small" icon={<LeftOutlined />} onClick={prevMonth} />
          <span style={{ fontWeight: 600, fontSize: 14 }}>{year}年{month}月</span>
          <Button type="text" size="small" icon={<RightOutlined />} onClick={nextMonth} />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 2, textAlign: 'center' }}>
          {WEEKDAY_LABELS.map(w => (
            <div key={w} style={{ fontSize: 12, color: token.colorTextTertiary, padding: '4px 0' }}>{w}</div>
          ))}
          {cells.map((day, i) => {
            if (day === null) return <div key={`e${i}`} />;
            const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
            const emoji = emojiMap[dateStr];
            const isSelected = dateStr === selectedDate;
            const isToday = dateStr === todayStr;
            return (
              <div key={dateStr} onClick={() => onDateClick(dateStr)} style={{
                padding: '4px 2px', borderRadius: 6, cursor: 'pointer', fontSize: 12,
                background: isSelected ? token.colorPrimaryBg : isToday ? token.colorFillAlter : 'transparent',
                border: isToday ? `1px solid ${token.colorPrimary}` : '1px solid transparent',
                transition: 'background 0.15s',
              }}>
                <div style={{ fontWeight: isToday ? 600 : 400, color: isToday ? token.colorPrimary : token.colorText }}>{day}</div>
                <div style={{ fontSize: 16, minHeight: 22 }}>{emoji || ''}</div>
              </div>
            );
          })}
        </div>
      </div>
    </Spin>
  );
};

export default CalendarView;
