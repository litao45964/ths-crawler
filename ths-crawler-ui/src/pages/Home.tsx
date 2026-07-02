import { Card, Typography } from 'antd';
import { HomeOutlined } from '@ant-design/icons';

const { Title, Paragraph } = Typography;

/**
 * 首页 - 市场概览
 * Phase 1 占位页面，Phase 2 接入市场指数、涨跌概览等真实数据
 */
export default function Home() {
  return (
    <div style={{ maxWidth: 800, margin: '0 auto', padding: '40px 20px' }}>
      <Card>
        <Title level={3} style={{ color: '#e8eaf0' }}>
          <HomeOutlined style={{ marginRight: 8 }} />
          市场概览
        </Title>
        <Paragraph type="secondary">
          市场指数、涨跌概览等数据即将上线。敬请期待 Phase 2。
        </Paragraph>
      </Card>
    </div>
  );
}
