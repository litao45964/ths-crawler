import { useState, useEffect, useCallback } from 'react';
import { Select, Button, Table, DatePicker, message, Spin, Space, Grid, Switch } from 'antd';
import { SyncOutlined, DownloadOutlined, HistoryOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import ReactECharts from 'echarts-for-react';
import { fetchLatestFlow, triggerCollect, triggerCollectByDate, wanToYi, formatAmount, formatPct } from '../api';
import type { IndustryFlowItem } from '../api';
import darkTheme from '../theme/echarts';

const topNOptions = [
  { label: 'Top 10', value: 10 },
  { label: 'Top 20', value: 20 },
  { label: 'Top 40', value: 40 },
];

const orderByOptions = [
  { label: '净额', value: 'net_amount' },
  { label: '流入额', value: 'inflow_amount' },
  { label: '流出额', value: 'outflow_amount' },
  { label: '涨跌幅', value: 'industry_change_pct' },
];

/** 移动端卡片列表项 */
function MobileFlowCard({ item, index }: { item: IndustryFlowItem; index: number }) {
  const netFmt = formatAmount(item.netAmount);
  const isTop3 = index < 3;
  return (
    <div className="mobile-flow-card">
      <div className="card-header">
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <span className={`rank-badge ${isTop3 ? 'top3' : 'normal'}`}>
            {index + 1}
          </span>
          <span className="industry-name">{item.industryName}</span>
        </div>
        <span style={{ fontSize: 16, fontWeight: 700, color: netFmt.color }}>
          {netFmt.text}亿
        </span>
      </div>
      <div className="card-body">
        <div className="stat-item">
          <span className="stat-label">流入额</span>
          <span className="stat-value" style={{ color: '#f5222d' }}>{wanToYi(item.inflowAmount)}亿</span>
        </div>
        <div className="stat-item">
          <span className="stat-label">流出额</span>
          <span className="stat-value" style={{ color: '#52c41a' }}>{wanToYi(item.outflowAmount)}亿</span>
        </div>
        <div className="stat-item">
          <span className="stat-label">涨跌幅</span>
          <span className="stat-value" style={{ color: formatPct(item.industryChangePct).color }}>
            {formatPct(item.industryChangePct).text}
          </span>
        </div>
        <div className="stat-item">
          <span className="stat-label">领涨股</span>
          <span className="stat-value" style={{ color: '#e8eaf0', fontSize: 13 }}>
            {item.leadingStock}
            {item.leadingStockPct !== 0 && (
              <span style={{ color: formatPct(item.leadingStockPct).color, marginLeft: 4, fontSize: 12 }}>
                {formatPct(item.leadingStockPct).text}
              </span>
            )}
          </span>
        </div>
      </div>
      <div className="card-footer">
        <span>{item.tradeDate}</span>
      </div>
    </div>
  );
}

export default function FlowRanking() {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;

  const [topN, setTopN] = useState(40);
  const [orderBy, setOrderBy] = useState('net_amount');
  const [sortAsc, setSortAsc] = useState(false); // false=流入最多, true=流出最多
  const [data, setData] = useState<IndustryFlowItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [collecting, setCollecting] = useState(false);
  const [backfillDate, setBackfillDate] = useState<string | null>(null);
  const [backfilling, setBackfilling] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const actualOrderBy = sortAsc && orderBy === 'net_amount' ? 'net_amount_asc' : orderBy;
      const res = await fetchLatestFlow(topN, actualOrderBy);
      if (res.success) {
        setData(res.data);
      }
    } catch (err) {
      message.error('数据加载失败：' + (err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [topN, orderBy, sortAsc]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleCollect = async () => {
    setCollecting(true);
    try {
      const res = await triggerCollect();
      if (res.success) {
        message.success(`采集成功！共 ${res.data.totalRows} 条，耗时 ${res.data.costMs}ms`);
        loadData();
      }
    } catch (err) {
      message.error('采集失败：' + (err as Error).message);
    } finally {
      setCollecting(false);
    }
  };

  const handleBackfill = async () => {
    if (!backfillDate) {
      message.warning('请选择补采日期');
      return;
    }
    setBackfilling(true);
    try {
      const res = await triggerCollectByDate(backfillDate);
      if (res.success) {
        message.success(`补采成功！日期 ${backfillDate}，共 ${res.data.totalRows} 条`);
        loadData();
      } else {
        message.error('补采失败：' + (res.message || '未知错误'));
      }
    } catch (err) {
      message.error('补采失败：' + (err as Error).message);
    } finally {
      setBackfilling(false);
    }
  };

  // ECharts 横向柱状图（移动端缩窄左边距）
  const chartOption = {
    ...darkTheme,
    tooltip: {
      ...darkTheme.tooltip,
      trigger: 'axis' as const,
      axisPointer: { type: 'shadow' as const },
      formatter: (params: any) => {
        const item = params[0];
        const val = Number(item.value);
        const yi = (val / 10000).toFixed(2);
        return `${item.name}<br/>净额：<span style="color:${val >= 0 ? '#f5222d' : '#52c41a'}">${val >= 0 ? '+' : ''}${yi} 亿</span>`;
      },
    },
    grid: {
      left: isMobile ? 80 : 120,
      right: 30,
      top: 10,
      bottom: 30,
    },
    xAxis: {
      ...(darkTheme.xAxis as Record<string, unknown>),
      type: 'value' as const,
      axisLabel: {
        ...((darkTheme.xAxis as Record<string, unknown>)?.axisLabel as Record<string, unknown>),
        formatter: (v: number) => (v / 10000).toFixed(0) + '亿',
      },
    },
    yAxis: {
      ...darkTheme.yAxis,
      type: 'category' as const,
      data: [...data].reverse().map((d) => d.industryName),
      axisLabel: { color: '#b0b8c8', fontSize: isMobile ? 11 : 12 },
    },
    series: [
      {
        type: 'bar' as const,
        data: [...data].reverse().map((d) => ({
          value: d.netAmount,
          itemStyle: {
            color: d.netAmount >= 0 ? '#f5222d' : '#52c41a',
            borderRadius: d.netAmount >= 0 ? [0, 4, 4, 0] : [4, 0, 0, 4],
          },
        })),
        barWidth: '60%',
      },
    ],
    // 移动端支持触摸缩放
    ...(isMobile
      ? {
          dataZoom: [
            {
              type: 'inside' as const,
              yAxisIndex: 0,
              startValue: Math.max(0, data.length - 10),
              endValue: data.length,
            },
          ],
        }
      : {}),
  };

  const columns = [
    {
      title: '排名',
      dataIndex: 'rank',
      key: 'rank',
      width: 60,
      render: (_: any, __: IndustryFlowItem, index: number) => index + 1,
    },
    {
      title: '行业名称',
      dataIndex: 'industryName',
      key: 'industryName',
      width: 120,
      render: (v: string, record: IndustryFlowItem) => (
        <a
          href={record.industryLink}
          target="_blank"
          rel="noopener noreferrer"
          style={{ color: '#1677ff' }}
        >
          {v}
        </a>
      ),
    },
    {
      title: '净额(亿)',
      dataIndex: 'netAmount',
      key: 'netAmount',
      width: 100,
      sorter: (a: IndustryFlowItem, b: IndustryFlowItem) => a.netAmount - b.netAmount,
      render: (v: number) => {
        const f = formatAmount(v);
        return <span style={{ color: f.color, fontWeight: 600 }}>{f.text}</span>;
      },
    },
    {
      title: '流入额(亿)',
      dataIndex: 'inflowAmount',
      key: 'inflowAmount',
      width: 100,
      render: (v: number) => <span style={{ color: '#f5222d' }}>{wanToYi(v)}</span>,
    },
    {
      title: '流出额(亿)',
      dataIndex: 'outflowAmount',
      key: 'outflowAmount',
      width: 100,
      render: (v: number) => <span style={{ color: '#52c41a' }}>{wanToYi(v)}</span>,
    },
    {
      title: '涨跌幅(%)',
      dataIndex: 'industryChangePct',
      key: 'industryChangePct',
      width: 100,
      sorter: (a: IndustryFlowItem, b: IndustryFlowItem) => a.industryChangePct - b.industryChangePct,
      render: (v: number) => {
        const f = formatPct(v);
        return <span style={{ color: f.color }}>{f.text}</span>;
      },
    },
    {
      title: '领涨股',
      dataIndex: 'leadingStock',
      key: 'leadingStock',
      width: 100,
      render: (v: string, record: IndustryFlowItem) => (
        <a
          href={record.leadingStockLink}
          target="_blank"
          rel="noopener noreferrer"
          style={{ color: '#1677ff' }}
        >
          {v}
        </a>
      ),
    },
    {
      title: '领涨股涨幅(%)',
      dataIndex: 'leadingStockPct',
      key: 'leadingStockPct',
      width: 120,
      render: (v: number) => {
        const f = formatPct(v);
        return <span style={{ color: f.color }}>{f.text}</span>;
      },
    },
  ];

  // ============ 移动端渲染 ============
  if (isMobile) {
    return (
      <div style={{ padding: 0 }}>
        {/* 移动端筛选器 */}
        <div className="mobile-filter-bar">
          <div className="filter-item">
            <span className="filter-label">数量</span>
            <Select value={topN} options={topNOptions} onChange={setTopN} style={{ width: 90 }} />
          </div>
          <div className="filter-item">
            <span className="filter-label">排序</span>
            <Select value={orderBy} options={orderByOptions} onChange={setOrderBy} style={{ width: 90 }} />
          </div>
          {orderBy === 'net_amount' && (
            <div className="filter-item">
              <Switch
                size="small"
                checked={sortAsc}
                onChange={setSortAsc}
                checkedChildren="流出"
                unCheckedChildren="流入"
              />
            </div>
          )}
          <Button
            type="primary"
            icon={<DownloadOutlined />}
            onClick={handleCollect}
            loading={collecting}
            size="small"
            style={{ background: '#1677ff', flexShrink: 0 }}
          >
            采集
          </Button>
        </div>

        <Spin spinning={loading}>
          {/* 移动端：只显示柱状图（可触摸滑动） */}
          <div style={{ marginBottom: 12 }}>
            <ReactECharts
              option={chartOption}
              style={{ height: Math.min(350, data.length * 28 + 40) }}
              theme="dark"
              opts={{ renderer: 'canvas' }}
            />
          </div>

          {/* 移动端：卡片列表替代表格 */}
          {data.map((item, idx) => (
            <MobileFlowCard key={item.industryCode} item={item} index={idx} />
          ))}
        </Spin>

        {data.length > 0 && (
          <div style={{ color: '#556677', fontSize: 12, marginTop: 8, marginBottom: 8 }}>
            数据日期：{data[0].tradeDate}
          </div>
        )}

        {/* 底部Tab间距补偿 */}
        <div className="mobile-bottom-spacer" />
      </div>
    );
  }

  // ============ 桌面端渲染（保持原样） ============
  return (
    <div style={{ padding: 0 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Space>
          <span style={{ color: '#8899aa' }}>显示数量</span>
          <Select value={topN} options={topNOptions} onChange={setTopN} style={{ width: 110 }} />
          <span style={{ color: '#8899aa' }}>排序方式</span>
          <Select value={orderBy} options={orderByOptions} onChange={setOrderBy} style={{ width: 110 }} />
          {orderBy === 'net_amount' && (
            <span style={{ color: '#8899aa', fontSize: 12 }}>
              <Switch
                size="small"
                checked={sortAsc}
                onChange={setSortAsc}
                checkedChildren="流出最多"
                unCheckedChildren="流入最多"
              />
            </span>
          )}
        </Space>
        <Space>
          <DatePicker
            value={backfillDate ? dayjs(backfillDate) : null}
            onChange={(d: dayjs.Dayjs | null) => setBackfillDate(d ? d.format('YYYY-MM-DD') : null)}
            placeholder="选择补采日期"
            style={{ width: 160 }}
            allowClear
          />
          <Button
            type="default"
            icon={<HistoryOutlined />}
            onClick={handleBackfill}
            loading={backfilling}
          >
            补采
          </Button>
          <Button
            type="primary"
            icon={<DownloadOutlined />}
            onClick={handleCollect}
            loading={collecting}
            style={{ background: '#1677ff' }}
          >
            手动采集
          </Button>
        </Space>
      </div>

      <Spin spinning={loading}>
        <div style={{ display: 'flex', gap: 16, minHeight: 400 }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <ReactECharts
              option={chartOption}
              style={{ height: Math.max(400, data.length * 32) }}
              theme="dark"
              opts={{ renderer: 'canvas' }}
            />
          </div>
          <div style={{ flex: 1.2, minWidth: 0 }}>
            <Table
              columns={columns}
              dataSource={data}
              rowKey="industryCode"
              size="small"
              pagination={false}
              scroll={{ y: Math.max(400, data.length * 40) }}
              style={{ background: 'transparent' }}
            />
          </div>
        </div>
      </Spin>

      {data.length > 0 && (
        <div style={{ color: '#556677', fontSize: 12, marginTop: 8 }}>
          数据日期：{data[0].tradeDate}
        </div>
      )}
    </div>
  );
}