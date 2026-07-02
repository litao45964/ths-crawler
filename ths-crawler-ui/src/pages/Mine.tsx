import { Card, Typography } from 'antd';
import { UserOutlined } from '@ant-design/icons';

const { Title, Paragraph } = Typography;

/**
 * 个人中心
 * Phase 1 占位页面，后续上线个人设置、收藏、自选等功能
 */
export default function Mine() {
  return (
    <div style={{ maxWidth: 800, margin: '0 auto', padding: '40px 20px' }}>
      <Card>
        <Title level={3} style={{ color: '#e8eaf0' }}>
          <UserOutlined style={{ marginRight: 8 }} />
          个人中心
        </Title>
        <Paragraph type="secondary">
          个人设置、收藏、自选等即将上线。敬请期待。
        </Paragraph>
      </Card>
    </div>
  );
}
