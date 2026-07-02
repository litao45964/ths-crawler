import { Drawer, Typography, Divider } from 'antd';
import {
  UserOutlined,
  MessageOutlined,
  DatabaseOutlined,
  BulbOutlined,
  SettingOutlined,
  QuestionCircleOutlined,
  FormOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';

const { Text } = Typography;

interface HamburgerDrawerProps {
  open: boolean;
  onClose: () => void;
  isMobile: boolean;
}

/**
 * 汉堡抽屉 - 左侧滑出菜单
 * 包含用户信息、功能入口、系统设置，Phase 1 大部分为占位
 */
export default function HamburgerDrawer({ open, onClose, isMobile }: HamburgerDrawerProps) {
  const menuItemStyle: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
    padding: '12px 16px',
    cursor: 'pointer',
    borderRadius: 6,
    color: '#e8eaf0',
    fontSize: 15,
    transition: 'background 0.2s',
  };

  const iconStyle: React.CSSProperties = {
    fontSize: 18,
    color: '#8899aa',
    width: 20,
    textAlign: 'center',
  };

  return (
    <Drawer
      open={open}
      onClose={onClose}
      placement="left"
      width={isMobile ? '75%' : 280}
      styles={{
        body: {
          background: '#111a27',
          padding: 0,
        },
        header: {
          background: '#111a27',
          borderBottom: '1px solid #1e2d45',
          color: '#e8eaf0',
        },
      }}
      closeIcon={null}
    >
      {/* 用户头像区 */}
      <div
        style={{
          padding: '24px 20px 16px',
          borderBottom: '1px solid #1e2d45',
        }}
      >
        <div
          style={{
            width: 48,
            height: 48,
            borderRadius: '50%',
            background: '#1677ff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            marginBottom: 10,
          }}
        >
          <UserOutlined style={{ fontSize: 22, color: '#fff' }} />
        </div>
        <Text strong style={{ color: '#e8eaf0', fontSize: 16 }}>
          春风
        </Text>
      </div>

      {/* 功能菜单 */}
      <div style={{ padding: '8px 10px' }}>
        <div style={menuItemStyle}>
          <MessageOutlined style={iconStyle} />
          <span>消息中心</span>
        </div>
        <div style={menuItemStyle}>
          <DatabaseOutlined style={iconStyle} />
          <span>数据源说明</span>
        </div>
        <div style={menuItemStyle}>
          <BulbOutlined style={iconStyle} />
          <span>主题切换</span>
        </div>
      </div>

      <Divider style={{ margin: '8px 0', borderColor: '#1e2d45' }} />

      {/* 系统菜单 */}
      <div style={{ padding: '8px 10px' }}>
        <div style={menuItemStyle}>
          <SettingOutlined style={iconStyle} />
          <span>系统设置</span>
        </div>
        <div style={menuItemStyle}>
          <QuestionCircleOutlined style={iconStyle} />
          <span>使用帮助</span>
        </div>
        <div style={menuItemStyle}>
          <FormOutlined style={iconStyle} />
          <span>意见反馈</span>
        </div>
        <div style={menuItemStyle}>
          <InfoCircleOutlined style={iconStyle} />
          <span>关于</span>
        </div>
      </div>
    </Drawer>
  );
}
