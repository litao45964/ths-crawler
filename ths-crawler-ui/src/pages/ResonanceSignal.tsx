import { useState, useEffect, useCallback } from 'react';
import { Select, Tabs, Card, Row, Col, Tag, Spin, message, Empty, Grid } from 'antd';
import {
  ArrowUpOutlined,
  ArrowDownOutlined,
  RiseOutlined,
  FallOutlined,
  WarningOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { fetchResonance, wanToYi } from '../api';
import type { ResonanceItem } from '../api';

const shortPeriodOptions = [
  { label: '5天', value: 5 },
  { label: '10天', value: 10 },
];

const longPeriodOptions = [
  { label: '14天', value: 14 },
  { label: '22天', value: 22 },
  { label: '30天', value: 30 },
  { label: '60天', value: 60 },
];

const SIGNAL_TYPES = {
  BULLISH_RESONANCE: {
    label: '长短共振向上',
    desc: '强势做多信号',
    color: '#52c41a',
    bgColor: '#0d2818',
    borderColor: '#1a4028',
    icon: <RiseOutlined />,
    tagColor: 'green',
  },
  LONG_BULLISH_SHORT_BEARISH: {
    label: '长多短空',
    desc: '回踩低吸',
    color: '#faad14',
    bgColor: '#1a1a0d',
    borderColor: '#3d3a1a',
    icon: <SafetyCertificateOutlined />,
    tagColor: 'gold',
  },
  LONG_BEARISH_SHORT_BULLISH: {
    label: '长空短多',
    desc: '反弹谨慎',
    color: '#fa8c16',
    bgColor: '#1a120d',
    borderColor: '#3d2a1a',
    icon: <WarningOutlined />,
    tagColor: 'orange',
  },
  BEARISH_RESONANCE: {
    label: '长短共振向下',
    desc: '规避',
    color: '#f5222d',
    bgColor: '#1a0d0d',
    borderColor: '#3d1a1a',
    icon: <FallOutlined />,
    tagColor: 'red',
  },
};

type SignalType = keyof typeof SIGNAL_TYPES;

function slopeColor(v: number): string {
  return v >= 0 ? '#f5222d' : '#52c41a';
}

/** 移动端共振卡片 */
function MobileResonanceCard({ item, cfg }: { item: ResonanceItem; cfg: (typeof SIGNAL_TYPES)[SignalType] }) {
  return (
    <div className="mobile-resonance-card" style={{ background: cfg.bgColor, border: `1px solid ${cfg.borderColor}` }}>
      <div className="signal-header">
        <span style={{ color: cfg.color, fontSize: 15, fontWeight: 700 }}>{item.industryName}</span>
        <Tag color={cfg.tagColor} style={{ margin: 0, fontSize: 11 }}>{cfg.desc}</Tag>
      </div>
      <div className="signal-stats">
        <div className="stat-item">
          <span className="stat-label">短周期斜率(亿/天)</span>
          <span className="stat-value" style={{ color: slopeColor(item.shortSlope) }}>
            {item.shortSlope >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />} {(item.shortSlope / 10000).toFixed(4)}
          </span>
        </div>
        <div className="stat-item">
          <span className="stat-label">长周期斜率(亿/天)</span>
          <span className="stat-value" style={{ color: slopeColor(item.longSlope) }}>
            {item.longSlope >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />} {(item.longSlope / 10000).toFixed(4)}
          </span>
        </div>
        <div className="stat-item">
          <span className="stat-label">短R²</span>
          <span className="stat-value" style={{ color: '#b0b8c8' }}>{item.shortRSquared.toFixed(2)}</span>
        </div>
        <div className="stat-item">
          <span className="stat-label">长R²</span>
          <span className="stat-value" style={{ color: '#b0b8c8' }}>{item.longRSquared.toFixed(2)}</span>
        </div>
        <div className="stat-item">
          <span className="stat-label">短日均净额(亿)</span>
          <span className="stat-value" style={{ color: item.shortAvgNet >= 0 ? '#f5222d' : '#52c41a' }}>
            {wanToYi(item.shortAvgNet)}
          </span>
        </div>
        <div className="stat-item">
          <span className="stat-label">长日均净额(亿)</span>
          <span className="stat-value" style={{ color: item.longAvgNet >= 0 ? '#f5222d' : '#52c41a' }}>
            {wanToYi(item.longAvgNet)}
          </span>
        </div>
      </div>
      <div style={{ color: '#556677', fontSize: 11, marginTop: 8 }}>{item.tradeDate}</div>
    </div>
  );
}

export default function ResonanceSignal() {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;

  const [shortPeriod, setShortPeriod] = useState(5);
  const [longPeriod, setLongPeriod] = useState(22);
  const [data, setData] = useState<ResonanceItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('BULLISH_RESONANCE');

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetchResonance(shortPeriod, longPeriod);
      if (res.success) {
        setData(res.data);
      }
    } catch (err) {
      message.error('共振数据加载失败：' + (err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [shortPeriod, longPeriod]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const grouped = (Object.keys(SIGNAL_TYPES) as SignalType[]).reduce(
    (acc, type) => {
      acc[type] = data.filter((d) => d.signalType === type);
      return acc;
    },
    {} as Record<SignalType, ResonanceItem[]>,
  );

  const renderCard = (item: ResonanceItem, cfg: (typeof SIGNAL_TYPES)[SignalType]) => (
    <Col xs={24} sm={12} md={8} key={item.industryName}>
      <Card
        style={{
          background: cfg.bgColor,
          borderColor: cfg.borderColor,
          borderWidth: 1,
        }}
        styles={{ body: { padding: isMobile ? 12 : 16 } }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
          <span style={{ color: cfg.color, fontSize: 16, fontWeight: 700 }}>{item.industryName}</span>
          <Tag color={cfg.tagColor} style={{ margin: 0 }}>
            {cfg.desc}
          </Tag>
        </div>

        <Row gutter={[8, 8]}>
          <Col span={12}>
            <div style={{ color: '#8899aa', fontSize: 12, marginBottom: 4 }}>短周期斜率（亿/天）</div>
            <div style={{ color: slopeColor(item.shortSlope), fontWeight: 600, fontSize: 14 }}>
              {item.shortSlope >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />}{' '}
              {(item.shortSlope / 10000).toFixed(4)}
            </div>
          </Col>
          <Col span={12}>
            <div style={{ color: '#8899aa', fontSize: 12, marginBottom: 4 }}>长周期斜率（亿/天）</div>
            <div style={{ color: slopeColor(item.longSlope), fontWeight: 600, fontSize: 14 }}>
              {item.longSlope >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />}{' '}
              {(item.longSlope / 10000).toFixed(4)}
            </div>
          </Col>
          <Col span={12}>
            <div style={{ color: '#8899aa', fontSize: 12, marginBottom: 4 }}>短周期 R²</div>
            <div style={{ color: '#b0b8c8', fontWeight: 600 }}>{item.shortRSquared.toFixed(2)}</div>
          </Col>
          <Col span={12}>
            <div style={{ color: '#8899aa', fontSize: 12, marginBottom: 4 }}>长周期 R²</div>
            <div style={{ color: '#b0b8c8', fontWeight: 600 }}>{item.longRSquared.toFixed(2)}</div>
          </Col>
          <Col span={12}>
            <div style={{ color: '#8899aa', fontSize: 12, marginBottom: 4 }}>短周期日均净额（亿）</div>
            <div style={{ color: item.shortAvgNet >= 0 ? '#f5222d' : '#52c41a', fontWeight: 600 }}>
              {wanToYi(item.shortAvgNet)}
            </div>
          </Col>
          <Col span={12}>
            <div style={{ color: '#8899aa', fontSize: 12, marginBottom: 4 }}>长周期日均净额（亿）</div>
            <div style={{ color: item.longAvgNet >= 0 ? '#f5222d' : '#52c41a', fontWeight: 600 }}>
              {wanToYi(item.longAvgNet)}
            </div>
          </Col>
        </Row>

        <div style={{ color: '#556677', fontSize: 11, marginTop: 8 }}>日期：{item.tradeDate}</div>
      </Card>
    </Col>
  );

  const tabItems = (Object.keys(SIGNAL_TYPES) as SignalType[]).map((type) => {
    const cfg = SIGNAL_TYPES[type];
    const items = grouped[type] || [];
    return {
      key: type,
      label: (
        <span>
          {cfg.icon} {isMobile ? cfg.label.slice(0, 4) : cfg.label}
          <Tag color={cfg.tagColor} style={{ marginLeft: 6, fontSize: 11 }}>
            {items.length}
          </Tag>
        </span>
      ),
      children: (
        <div>
          {items.length > 0 ? (
            isMobile ? (
              // 移动端：纵向卡片列表
              items.map((item) => (
                <MobileResonanceCard key={item.industryName} item={item} cfg={cfg} />
              ))
            ) : (
              <Row gutter={[16, 16]}>{items.map((item) => renderCard(item, cfg))}</Row>
            )
          ) : (
            <Empty
              description={<span style={{ color: '#556677' }}>暂无此信号行业</span>}
              image={Empty.PRESENTED_IMAGE_SIMPLE}
            />
          )}
        </div>
      ),
    };
  });

  return (
    <div style={{ padding: 0 }}>
      {/* 选择器 */}
      <div className={isMobile ? 'mobile-selector-row' : ''} style={isMobile ? {} : { display: 'flex', gap: 16, alignItems: 'center', marginBottom: 20 }}>
        <span style={{ color: '#8899aa', fontSize: isMobile ? 13 : undefined }}>短周期</span>
        <Select value={shortPeriod} options={shortPeriodOptions} onChange={setShortPeriod} style={{ width: isMobile ? 80 : 120 }} />
        <span style={{ color: '#8899aa', fontSize: isMobile ? 13 : undefined }}>长周期</span>
        <Select value={longPeriod} options={longPeriodOptions} onChange={setLongPeriod} style={{ width: isMobile ? 80 : 120 }} />
      </div>

      <Spin spinning={loading}>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={tabItems}
          type="card"
          style={{ color: '#b0b8c8' }}
        />
      </Spin>

      {/* 说明 */}
      <div style={{ marginTop: 16, padding: isMobile ? '10px 14px' : '12px 16px', background: '#141e2e', borderRadius: 6, borderLeft: '3px solid #1677ff' }}>
        <div style={{ color: '#8899aa', fontSize: isMobile ? 12 : 13, lineHeight: isMobile ? 1.8 : 2 }}>
          <WarningOutlined style={{ marginRight: 6 }} />
          <strong style={{ color: '#b0b8c8' }}>信号说明：</strong>
          长短共振向上 = 强势做多信号；长多短空 = 回踩低吸；长空短多 = 反弹谨慎；长短共振向下 = 规避风险。
          斜率正=流入趋势，负=流出趋势。R²越大趋势越可靠。
        </div>
      </div>

      {isMobile && <div className="mobile-bottom-spacer" />}
    </div>
  );
}
