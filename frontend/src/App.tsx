import { BrowserRouter, Routes, Route, useNavigate, useLocation } from 'react-router-dom';
import { ConfigProvider, theme, Layout, Menu, Grid } from 'antd';
import {
  BarChartOutlined,
  LineChartOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import FlowRanking from './pages/FlowRanking';
import TrendAnalysis from './pages/TrendAnalysis';
import ResonanceSignal from './pages/ResonanceSignal';
import './App.css';

const { Header, Content, Footer } = Layout;

const menuItems = [
  { key: '/', icon: <BarChartOutlined />, label: '排行' },
  { key: '/trend', icon: <LineChartOutlined />, label: '趋势' },
  { key: '/resonance', icon: <ThunderboltOutlined />, label: '共振' },
];

/** 桌面端：顶部Header导航 */
function DesktopLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  return (
    <Layout style={{ minHeight: '100vh', background: '#0d1520' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          background: '#111a27',
          borderBottom: '1px solid #1e2d45',
          padding: '0 24px',
          height: 56,
        }}
      >
        <div
          style={{
            color: '#e8eaf0',
            fontSize: 18,
            fontWeight: 700,
            marginRight: 40,
            whiteSpace: 'nowrap',
            letterSpacing: 1,
          }}
        >
          同花顺行业资金流向分析
        </div>
        <Menu
          mode="horizontal"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{
            flex: 1,
            background: 'transparent',
            borderBottom: 'none',
          }}
          theme="dark"
        />
      </Header>

      <Content
        style={{
          padding: '20px 28px',
          background: '#0d1520',
          minHeight: 'calc(100vh - 56px)',
        }}
      >
        <Routes>
          <Route path="/" element={<FlowRanking />} />
          <Route path="/trend" element={<TrendAnalysis />} />
          <Route path="/resonance" element={<ResonanceSignal />} />
        </Routes>
      </Content>
    </Layout>
  );
}

/** 移动端：底部Tab导航 */
function MobileLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  return (
    <Layout style={{ minHeight: '100vh', background: '#0d1520' }}>
      {/* 移动端顶部标题栏 */}
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: '#111a27',
          borderBottom: '1px solid #1e2d45',
          padding: '0 16px',
          height: 48,
        }}
      >
        <div
          style={{
            color: '#e8eaf0',
            fontSize: 16,
            fontWeight: 700,
            letterSpacing: 1,
          }}
        >
          行业资金流向
        </div>
      </Header>

      <Content
        style={{
          padding: '12px',
          background: '#0d1520',
          minHeight: 'calc(100vh - 48px - 56px)',
          overflowY: 'auto',
          WebkitOverflowScrolling: 'touch',
        }}
      >
        <Routes>
          <Route path="/" element={<FlowRanking />} />
          <Route path="/trend" element={<TrendAnalysis />} />
          <Route path="/resonance" element={<ResonanceSignal />} />
        </Routes>
      </Content>

      {/* 底部Tab栏 */}
      <Footer
        style={{
          position: 'fixed',
          bottom: 0,
          left: 0,
          right: 0,
          height: 56,
          padding: 0,
          background: '#111a27',
          borderTop: '1px solid #1e2d45',
          zIndex: 1000,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-around',
          // 适配iPhone底部安全区
          paddingBottom: 'env(safe-area-inset-bottom, 0px)',
        }}
      >
        {menuItems.map((item) => {
          const isActive = location.pathname === item.key;
          return (
            <div
              key={item.key}
              onClick={() => navigate(item.key)}
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                flex: 1,
                height: '100%',
                cursor: 'pointer',
                color: isActive ? '#1677ff' : '#8899aa',
                transition: 'color 0.2s',
                userSelect: 'none',
                WebkitTapHighlightColor: 'transparent',
              }}
            >
              <span style={{ fontSize: 20, marginBottom: 2 }}>{item.icon}</span>
              <span style={{ fontSize: 11, fontWeight: isActive ? 600 : 400 }}>
                {item.label}
              </span>
            </div>
          );
        })}
      </Footer>
    </Layout>
  );
}

/** 根断点切换布局 */
function AppLayout() {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md; // md=768px，小于768视为移动端

  return isMobile ? <MobileLayout /> : <DesktopLayout />;
}

export default function App() {
  return (
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: '#1677ff',
          colorBgContainer: '#1a2332',
          colorBgElevated: '#1a2332',
          colorBorder: '#2a3a4f',
          colorText: '#e8eaf0',
          colorTextSecondary: '#8899aa',
          borderRadius: 6,
        },
        components: {
          Table: {
            headerBg: '#141e2e',
            rowHoverBg: '#1e2d45',
            borderColor: '#2a3a4f',
          },
          Card: {
            colorBorderSecondary: '#2a3a4f',
          },
          Select: {
            optionActiveBg: '#1e2d45',
            optionSelectedBg: '#1677ff',
          },
          Tabs: {
            inkBarColor: '#1677ff',
            itemActiveColor: '#1677ff',
            itemSelectedColor: '#1677ff',
          },
        },
      }}
    >
      <BrowserRouter>
        <AppLayout />
      </BrowserRouter>
    </ConfigProvider>
  );
}
