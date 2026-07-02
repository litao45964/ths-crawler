import { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useNavigate, useLocation } from 'react-router-dom';
import { ConfigProvider, theme, Layout, Menu, Grid } from 'antd';
import {
  HomeOutlined,
  BarChartOutlined,
  ToolOutlined,
  UserOutlined,
  MenuOutlined,
} from '@ant-design/icons';
import Home from './pages/Home';
import FundFlowLayout from './components/FundFlowLayout';
import Tools from './pages/Tools';
import Mine from './pages/Mine';
import HamburgerDrawer from './components/HamburgerDrawer';
import './App.css';

const { Header, Content, Footer } = Layout;

/** 一级导航：4个核心Tab */
const primaryTabs = [
  { key: '/', icon: <HomeOutlined />, label: '首页' },
  { key: '/fund-flow', icon: <BarChartOutlined />, label: '资金分析' },
  { key: '/tools', icon: <ToolOutlined />, label: '工具' },
  { key: '/mine', icon: <UserOutlined />, label: '我的' },
];

/** 移动端 Header 标题映射 */
const headerTitles: Record<string, string> = {
  '/': '市场概览',
  '/fund-flow': '行业资金流向',
  '/tools': '决策工具',
  '/mine': '个人中心',
};

/** 根据当前路径获取匹配的一级Tab key */
function getActivePrimaryKey(pathname: string): string {
  if (pathname.startsWith('/fund-flow')) return '/fund-flow';
  if (pathname.startsWith('/tools')) return '/tools';
  if (pathname.startsWith('/mine')) return '/mine';
  return '/';
}

/** ==================== 桌面端布局 ==================== */
function DesktopLayout({ onOpenDrawer }: { onOpenDrawer: () => void }) {
  const navigate = useNavigate();
  const location = useLocation();
  const activeKey = getActivePrimaryKey(location.pathname);

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
        {/* Logo */}
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

        {/* 一级导航菜单 */}
        <Menu
          mode="horizontal"
          selectedKeys={[activeKey]}
          items={primaryTabs}
          onClick={({ key }) => navigate(key)}
          style={{
            flex: 1,
            background: 'transparent',
            borderBottom: 'none',
          }}
          theme="dark"
        />

        {/* 汉堡按钮 */}
        <div
          onClick={onOpenDrawer}
          className="hamburger-btn"
          style={{
            cursor: 'pointer',
            fontSize: 20,
            color: '#8899aa',
            padding: '4px 8px',
          }}
        >
          <MenuOutlined />
        </div>
      </Header>

      {/* 页面内容 */}
      <Content
        style={{
          padding: '20px 28px',
          background: '#0d1520',
          minHeight: 'calc(100vh - 56px - 32px)',
        }}
      >
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/fund-flow/*" element={<FundFlowLayout />} />
          <Route path="/tools" element={<Tools />} />
          <Route path="/mine" element={<Mine />} />
          {/* 旧路由重定向，不破坏书签 */}
          <Route path="/trend" element={<Navigate to="/fund-flow/trend" replace />} />
          <Route path="/resonance" element={<Navigate to="/fund-flow/resonance" replace />} />
          <Route path="/history" element={<Navigate to="/fund-flow/history" replace />} />
          <Route path="/calendar" element={<Navigate to="/fund-flow/calendar" replace />} />
        </Routes>
      </Content>

      {/* 底部版权 */}
      <Footer
        style={{
          textAlign: 'center',
          background: '#0d1520',
          color: '#556677',
          fontSize: 12,
          padding: '8px 0',
          borderTop: '1px solid #1e2d45',
        }}
      >
        © 2026 ths-crawler · 数据来源：同花顺
      </Footer>
    </Layout>
  );
}

/** ==================== 移动端布局 ==================== */
function MobileLayout({ onOpenDrawer }: { onOpenDrawer: () => void }) {
  const navigate = useNavigate();
  const location = useLocation();
  const activeKey = getActivePrimaryKey(location.pathname);
  const headerTitle = headerTitles[activeKey] || 'ths-crawler';

  return (
    <Layout style={{ minHeight: '100vh', background: '#0d1520' }}>
      {/* 顶部标题栏 + 汉堡按钮 */}
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
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
          {headerTitle}
        </div>

        <div
          onClick={onOpenDrawer}
          className="hamburger-btn"
          style={{
            cursor: 'pointer',
            fontSize: 20,
            color: '#8899aa',
            padding: '4px 8px',
          }}
        >
          <MenuOutlined />
        </div>
      </Header>

      {/* 页面内容 */}
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
          <Route path="/" element={<Home />} />
          <Route path="/fund-flow/*" element={<FundFlowLayout />} />
          <Route path="/tools" element={<Tools />} />
          <Route path="/mine" element={<Mine />} />
          {/* 旧路由重定向 */}
          <Route path="/trend" element={<Navigate to="/fund-flow/trend" replace />} />
          <Route path="/resonance" element={<Navigate to="/fund-flow/resonance" replace />} />
          <Route path="/history" element={<Navigate to="/fund-flow/history" replace />} />
          <Route path="/calendar" element={<Navigate to="/fund-flow/calendar" replace />} />
        </Routes>
      </Content>

      {/* 底部固定一级Tab */}
      <Footer
        className="primary-tab-bar"
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
          paddingBottom: 'env(safe-area-inset-bottom, 0px)',
        }}
      >
        {primaryTabs.map((tab) => {
          const isActive = activeKey === tab.key;
          return (
            <div
              key={tab.key}
              onClick={() => navigate(tab.key)}
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
              <span style={{ fontSize: 20, marginBottom: 2 }}>{tab.icon}</span>
              <span style={{ fontSize: 11, fontWeight: isActive ? 600 : 400 }}>
                {tab.label}
              </span>
            </div>
          );
        })}
      </Footer>
    </Layout>
  );
}

/** ==================== 根断点切换 + Drawer 状态管理 ==================== */
function AppLayout() {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [drawerOpen, setDrawerOpen] = useState(false);

  return (
    <>
      {isMobile ? (
        <MobileLayout onOpenDrawer={() => setDrawerOpen(true)} />
      ) : (
        <DesktopLayout onOpenDrawer={() => setDrawerOpen(true)} />
      )}
      <HamburgerDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        isMobile={isMobile}
      />
    </>
  );
}

/** ==================== 应用入口 ==================== */
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
