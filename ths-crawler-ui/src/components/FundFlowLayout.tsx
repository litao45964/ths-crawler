import { useNavigate, useLocation } from 'react-router-dom';
import { Tabs } from 'antd';
import {
  BarChartOutlined,
  LineChartOutlined,
  ThunderboltOutlined,
  HistoryOutlined,
  CalendarOutlined,
} from '@ant-design/icons';
import FlowRanking from '../pages/FlowRanking';
import TrendAnalysis from '../pages/TrendAnalysis';
import ResonanceSignal from '../pages/ResonanceSignal';
import HistoryAnalysis from '../pages/HistoryAnalysis';
import TradeCalendar from '../pages/TradeCalendar';

/**
 * 资金分析二级标签容器
 *
 * 使用 Ant Design Tabs 包裹 5 个现有页面组件。
 * destroyInactiveTabPane=false 保留页面状态，切换 Tab 不丢失数据。
 * Tab 切换同步 URL，支持书签和直接访问。
 */
export default function FundFlowLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  // 从 URL 解析当前活跃的二级 Tab
  const pathSegments = location.pathname.replace(/\/$/, '').split('/');
  const activeKey = pathSegments[2] || 'ranking';

  const tabItems = [
    { key: 'ranking', label: '排行', icon: <BarChartOutlined />, children: <FlowRanking /> },
    { key: 'trend', label: '趋势', icon: <LineChartOutlined />, children: <TrendAnalysis /> },
    { key: 'resonance', label: '共振', icon: <ThunderboltOutlined />, children: <ResonanceSignal /> },
    { key: 'history', label: '历史对比', icon: <HistoryOutlined />, children: <HistoryAnalysis /> },
    { key: 'calendar', label: '交易日历', icon: <CalendarOutlined />, children: <TradeCalendar /> },
  ];

  const handleTabChange = (key: string) => {
    navigate(`/fund-flow/${key}`, { replace: true });
    // Tab 切换后延迟触发 resize，修复 ECharts 容器尺寸问题
    setTimeout(() => window.dispatchEvent(new Event('resize')), 100);
  };

  return (
    <Tabs
      activeKey={activeKey}
      onChange={handleTabChange}
      items={tabItems}
      destroyInactiveTabPane={false}
      style={{ minHeight: '100%' }}
    />
  );
}
