import { Card, Typography } from 'antd';
import { ToolOutlined } from '@ant-design/icons';

const { Title, Paragraph } = Typography;

/**
 * 决策工具 - 工具矩阵入口
 * Phase 1 占位页面，Phase 3 上线选股/估值/回测等工具
 */
export default function Tools() {
  return (
    <div style={{ maxWidth: 800, margin: '0 auto', padding: '40px 20px' }}>
      <Card>
        <Title level={3} style={{ color: '#e8eaf0' }}>
          <ToolOutlined style={{ marginRight: 8 }} />
          决策工具
        </Title>
        <Paragraph type="secondary">
          选股、估值、回测等决策工具即将上线。敬请期待 Phase 3。
        </Paragraph>
      </Card>
    </div>
  );
}
