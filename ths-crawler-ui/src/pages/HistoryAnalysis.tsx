import { useState, useEffect, useCallback, useMemo } from 'react';
import { Select, Button, Table, message, Spin, Grid, Card, Tag, Empty } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import type { ColumnsType, FilterValue } from 'antd/es/table/interface';
import {
  fetchIndustries,
  fetchHistory,
  formatAmount,
  formatPct,
} from '../api';
import type { IndustryFlowItem } from '../api';
import darkTheme from '../theme/echarts';

// 10 色色板，用于区分多条折线
const COLORS = [
  '#1677ff', '#f5222d', '#52c41a', '#faad14', '#722ed1',
  '#eb2f96', '#13c2c2', '#fa8c16', '#2f54eb', '#a0d911',
];

// 天数选项
const DAYS_OPTIONS = [
  { value: 30, label: '近30天' },
  { value: 60, label: '近60天' },
  { value: 90, label: '近90天' },
  { value: 120, label: '近120天' },
];

export default function HistoryAnalysis() {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;

  const [industries, setIndustries] = useState<string[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const [days, setDays] = useState(60);
  const [loading, setLoading] = useState(false);
  // allData: 行业名称 → 该行业的历史数据数组
  const [allData, setAllData] = useState<Map<string, IndustryFlowItem[]>>(new Map());

  // 加载行业列表
  useEffect(() => {
    (async () => {
      try {
        const res = await fetchIndustries();
        if (res.success && res.data.length > 0) {
          setIndustries(res.data);
          setSelected([res.data[0]]); // 默认选中第一个行业
        }
      } catch (err) {
        message.error('获取行业列表失败：' + (err as Error).message);
      }
    })();
  }, []);

  // 查询历史数据
  const handleQuery = useCallback(async () => {
    if (selected.length === 0) {
      message.warning('请至少选择一个行业');
      return;
    }
    setLoading(true);
    try {
      const results = await Promise.all(
        selected.map((ind) => fetchHistory(ind, days)),
      );
      const map = new Map<string, IndustryFlowItem[]>();
      results.forEach((res, i) => {
        if (res.success && res.data) {
          // 按日期升序排列
          const sorted = [...res.data].sort(
            (a, b) => a.tradeDate.localeCompare(b.tradeDate),
          );
          map.set(selected[i], sorted);
        }
      });
      setAllData(map);
      if (map.size === 0) {
        message.info('所选行业暂无历史数据');
      }
    } catch (err) {
      message.error('查询失败：' + (err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [selected, days]);

  // 自动查询：行业或天数变化时触发
  useEffect(() => {
    if (selected.length > 0) {
      handleQuery();
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps
  // 注意：handleQuery 已依赖 selected 和 days，此处仅首次自动触发
  useEffect(() => {
    if (selected.length > 0) {
      handleQuery();
    }
  }, [selected.length, days]); // eslint-disable-line react-hooks/exhaustive-deps

  // ---- ECharts 配置 ----
  const chartOption = useMemo(() => {
    const industryNames = Array.from(allData.keys());
    if (industryNames.length === 0) return null;

    // 提取所有日期（取第一个行业的日期序列作为X轴）
    const firstData = allData.get(industryNames[0]) || [];
    const dates = firstData.map((d) => d.tradeDate);

    // 为每个行业构建一条 series
    const series = industryNames.map((name, idx) => {
      const raw = allData.get(name) || [];
      // 将数据对齐到日期序列
      const dateMap = new Map(raw.map((d) => [d.tradeDate, d.netAmount]));
      const values = dates.map((date) => dateMap.get(date) ?? null);

      return {
        name,
        type: 'line' as const,
        data: values,
        smooth: true,
        symbol: 'none',
        lineStyle: { color: COLORS[idx % COLORS.length], width: 2 },
        itemStyle: { color: COLORS[idx % COLORS.length] },
        emphasis: { focus: 'series' as const },
      };
    });

    return {
      ...darkTheme,
      legend: {
        ...darkTheme.legend,
        top: 0,
        data: industryNames,
      },
      tooltip: {
        ...darkTheme.tooltip,
        trigger: 'axis' as const,
        formatter: (params: any) => {
          if (!Array.isArray(params) || params.length === 0) return '';
          const date = params[0].axisValue;
          let html = `<div style="font-weight:600;margin-bottom:4px">${date}</div>`;
          params.forEach((p: any) => {
            if (p.value !== null && p.value !== undefined) {
              const color = p.value >= 0 ? '#f5222d' : '#52c41a';
              const val = (p.value / 10000).toFixed(2);
              html += `<div style="display:flex;align-items:center;gap:6px">
                <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${p.color}"></span>
                <span>${p.seriesName}</span>
                <span style="color:${color};font-weight:600;margin-left:auto">${val} 亿</span>
              </div>`;
            }
          });
          return html;
        },
      },
      xAxis: {
        ...darkTheme.xAxis,
        type: 'category',
        data: dates,
        axisLabel: {
          ...darkTheme.xAxis?.axisLabel,
          rotate: dates.length > 30 ? 45 : 0,
        },
      },
      yAxis: {
        ...darkTheme.yAxis,
        type: 'value',
        name: '净额（万元）',
        axisLabel: {
          ...darkTheme.yAxis?.axisLabel,
          formatter: (v: number) => {
            if (Math.abs(v) >= 10000) return (v / 10000).toFixed(1) + '亿';
            if (Math.abs(v) >= 1000) return (v / 1000).toFixed(1) + '千万';
            return v.toString();
          },
        },
      },
      grid: { left: 80, right: 30, top: 40, bottom: dates.length > 30 ? 70 : 40 },
      series,
    } as any;
  }, [allData]);

  // ---- 表格列定义 ----
  const columns: ColumnsType<IndustryFlowItem> = useMemo(
    () => [
      {
        title: '行业',
        dataIndex: 'industryName',
        width: 100,
        fixed: isMobile ? undefined : 'left',
        render: (v: string) => <Tag color="blue">{v}</Tag>,
      },
      {
        title: '日期',
        dataIndex: 'tradeDate',
        width: 110,
        sorter: (a, b) => a.tradeDate.localeCompare(b.tradeDate),
        defaultSortOrder: 'descend',
      },
      {
        title: '净额(亿)',
        dataIndex: 'netAmount',
        width: 100,
        align: 'right',
        sorter: (a, b) => a.netAmount - b.netAmount,
        render: (v: number) => {
          const { text, color } = formatAmount(v);
          return <span style={{ color, fontWeight: 600 }}>{text}</span>;
        },
      },
      {
        title: '流入(亿)',
        dataIndex: 'inflowAmount',
        width: 100,
        align: 'right',
        render: (v: number) => (v / 10000).toFixed(2),
        responsive: ['md'],
      },
      {
        title: '流出(亿)',
        dataIndex: 'outflowAmount',
        width: 100,
        align: 'right',
        render: (v: number) => (v / 10000).toFixed(2),
        responsive: ['md'],
      },
      {
        title: '涨跌幅',
        dataIndex: 'industryChangePct',
        width: 90,
        align: 'right',
        sorter: (a, b) => a.industryChangePct - b.industryChangePct,
        render: (v: number) => {
          const { text, color } = formatPct(v);
          return <span style={{ color, fontWeight: 600 }}>{text}</span>;
        },
        responsive: ['md'],
      },
      {
        title: '领涨股',
        dataIndex: 'leadingStock',
        width: 100,
        responsive: ['md'],
      },
      {
        title: '领涨涨幅',
        dataIndex: 'leadingStockPct',
        width: 90,
        align: 'right',
        render: (v: number) => {
          const { text, color } = formatPct(v);
          return <span style={{ color }}>{text}</span>;
        },
        responsive: ['md'],
      },
    ],
    [isMobile],
  );

  // 表格数据：平铺所有行业记录
  const tableData = useMemo(() => {
    const rows: IndustryFlowItem[] = [];
    allData.forEach((items) => rows.push(...items));
    return rows;
  }, [allData]);

  // 行业筛选器（表格内）
  const [filteredIndustry, setFilteredIndustry] = useState<string | null>(null);
  const filteredTableData = useMemo(() => {
    if (!filteredIndustry) return tableData;
    return tableData.filter((r) => r.industryName === filteredIndustry);
  }, [tableData, filteredIndustry]);

  const handleTableChange = (_: any, filters: Record<string, FilterValue | null>) => {
    const val = filters.industryName;
    setFilteredIndustry((val && val.length > 0 ? String(val[0]) : null) as string | null);
  };

  // ---- 移动端控件栏 ----
  const controlBar = (
    <div
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: 8,
        marginBottom: 16,
        alignItems: 'center',
      }}
    >
      <Select
        mode="multiple"
        style={{ minWidth: isMobile ? 160 : 280, maxWidth: isMobile ? '100%' : 400 }}
        placeholder="选择行业（多选）"
        value={selected}
        onChange={(vals) => setSelected(vals)}
        options={industries.map((name) => ({ value: name, label: name }))}
        maxTagCount={isMobile ? 2 : 'responsive'}
        allowClear
        showSearch
        filterOption={(input, option) =>
          (option?.label as string).includes(input)
        }
        notFoundContent="未匹配到行业"
      />
      <Select
        style={{ width: 110 }}
        value={days}
        onChange={(v) => setDays(v)}
        options={DAYS_OPTIONS}
      />
      <Button
        type="primary"
        icon={<SearchOutlined />}
        onClick={handleQuery}
        loading={loading}
      >
        查询
      </Button>
    </div>
  );

  // ---- 图表区域 ----
  const chartArea = chartOption ? (
    <Card
      style={{ marginBottom: 16 }}
      bodyStyle={{ padding: isMobile ? 8 : 16 }}
    >
      <ReactECharts
        option={chartOption}
        style={{ height: isMobile ? 280 : 420 }}
        theme="dark"
        opts={{ renderer: 'canvas' }}
      />
    </Card>
  ) : null;

  // ---- 表格区域 ----
  const tableArea = tableData.length > 0 ? (
    <Card bodyStyle={{ padding: isMobile ? 8 : 16 }}>
      <Table<IndustryFlowItem>
        columns={columns}
        dataSource={filteredTableData}
        rowKey={(r) => `${r.industryName}-${r.tradeDate}`}
        size={isMobile ? 'small' : 'middle'}
        pagination={{
          pageSize: isMobile ? 15 : 20,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条记录`,
        }}
        scroll={{ x: isMobile ? 400 : 700 }}
        loading={loading}
        onChange={handleTableChange}
        locale={{
          filterConfirm: '确定',
          filterReset: '重置',
          emptyText: <Empty description="请选择行业查询历史数据" />,
        }}
      />
    </Card>
  ) : (
    !loading && (
      <Card>
        <Empty description="请选择行业并点击查询" />
      </Card>
    )
  );

  // ---- 页面渲染 ----
  return (
    <Spin spinning={loading}>
      <div style={{ padding: isMobile ? 4 : 0 }}>
        {controlBar}
        {chartArea}
        {tableArea}
      </div>
    </Spin>
  );
}
