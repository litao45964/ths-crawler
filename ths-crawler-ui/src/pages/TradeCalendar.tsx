import { useState, useEffect } from 'react';
import { Card, Calendar, Badge, Spin, message, Typography, Grid } from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import axios from 'axios';

const { Title } = Typography;
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 15000,
});

interface TradeDayInfo {
  date: string;
  isTradeDay: boolean;
  reason: string;
}

export default function TradeCalendar() {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [tradeDays, setTradeDays] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [currentYear, setCurrentYear] = useState(dayjs().year());
  const [currentMonth, setCurrentMonth] = useState(dayjs().month() + 1);

  useEffect(() => {
    loadTradeDays(currentYear);
  }, [currentYear]);

  const loadTradeDays = async (year: number) => {
    setLoading(true);
    try {
      const res = await api.get('/api/trade-calendar', { params: { year } });
      if (res.data.success) {
        setTradeDays(new Set(res.data.data));
      } else {
        message.error('加载交易日历失败');
      }
    } catch (err) {
      message.error('加载交易日历失败');
    } finally {
      setLoading(false);
    }
  };

  const dateCellRender = (date: Dayjs) => {
    const dateStr = date.format('YYYY-MM-DD');
    const isTrade = tradeDays.has(dateStr);
    const isWeekend = date.day() === 0 || date.day() === 6;

    if (isTrade) {
      return (
        <div style={{ textAlign: 'center', marginTop: 4 }}>
          <Badge status="success" text={<span style={{ fontSize: 11, color: '#52c41a' }}>交易日</span>} />
        </div>
      );
    }
    if (isWeekend && tradeDays.size > 0) {
      return (
        <div style={{ textAlign: 'center', marginTop: 4 }}>
          <Badge status="default" text={<span style={{ fontSize: 11, color: '#556677' }}>休市</span>} />
        </div>
      );
    }
    return null;
  };

  const onPanelChange = (date: Dayjs) => {
    const newYear = date.year();
    const newMonth = date.month() + 1;
    setCurrentMonth(newMonth);
    if (newYear !== currentYear) {
      setCurrentYear(newYear);
    }
  };

  return (
    <div style={{ padding: isMobile ? 0 : '0 20px' }}>
      <div style={{ marginBottom: 16 }}>
        <Title level={isMobile ? 5 : 4} style={{ color: '#e8eaf0', margin: 0 }}>
          交易日历
        </Title>
        <div style={{ color: '#8899aa', fontSize: 13, marginTop: 4 }}>
          {currentYear}年{currentMonth}月 · 共 {tradeDays.size} 个交易日
        </div>
      </div>

      <Spin spinning={loading}>
        <Card
          style={{ background: '#1a2332', borderColor: '#2a3a4f' }}
          styles={{ body: { padding: isMobile ? 8 : 16 } }}
        >
          <Calendar
            fullscreen={!isMobile}
            dateCellRender={dateCellRender}
            onPanelChange={onPanelChange}
            headerRender={({ value, onChange }) => {
              // 简化头部，只保留月份切换
              return <div />;
            }}
          />
        </Card>
      </Spin>

      {isMobile && <div className="mobile-bottom-spacer" />}
    </div>
  );
}