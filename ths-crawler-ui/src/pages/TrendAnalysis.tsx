import { useState, useEffect, useCallback, useMemo } from 'react';
import { Select, Card, Row, Col, Statistic, Space, Spin, Button, message, Grid } from 'antd';
import { ArrowUpOutlined, ArrowDownOutlined, LineChartOutlined, SyncOutlined } from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import { fetchIndustries, fetchTrend, fetchHistory, triggerTrendCalculate, wanToYi, formatAmount } from '../api';
import type { TrendData, IndustryFlowItem } from '../api';
import darkTheme from '../theme/echarts';

const periodOptions = [
  { label: '5天', value: 5 },
  { label: '10天', value: 10 },
  { label: '14天', value: 14 },
  { label: '22天', value: 22 },
  { label: '30天', value: 30 },
  { label: '60天', value: 60 },
];

/** 斜率颜色：正值红色（流入趋势），负值绿色（流出趋势） */
function slopeColor(v: number): string {
  return v >= 0 ? '#f5222d' : '#52c41a';
}

/** R² 解读 */
function rSquaredDesc(v: number): { text: string; color: string } {
  if (v > 0.7) return { text: '强趋势', color: '#f5222d' };
  if (v > 0.4) return { text: '中等趋势', color: '#faad14' };
  return { text: '弱趋势', color: '#556677' };
}

export default function TrendAnalysis() {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;

  const [industries, setIndustries] = useState<{ label: string; value: string }[]>([]);
  const [selected, setSelected] = useState<string>('');
  const [period, setPeriod] = useState(22);
  const [trend, setTrend] = useState<TrendData | null>(null);
  const [loading, setLoading] = useState(false);
  const [calcLoading, setCalcLoading] = useState(false);
  const [historyData, setHistoryData] = useState<IndustryFlowItem[]>([]);

  useEffect(() => {
    fetchIndustries()
      .then((res) => {
        if (res.success) {
          const opts = res.data.map((name: string) => ({
            label: name,
            value: name,
          }));
          setIndustries(opts);
          if (opts.length > 0 && !selected) {
            setSelected(opts[0].value);
          }
        }
      })
      .catch(() => message.error('行业列表加载失败'));
  }, []);

  const loadTrend = useCallback(async () => {
    if (!selected) return;
    setLoading(true);
    try {
      const [trendRes, historyRes] = await Promise.all([
        fetchTrend(selected, period),
        fetchHistory(selected, period * 2),
      ]);
      if (trendRes.success) {
        setTrend(trendRes.data);
      } else {
        setTrend(null);
        message.warning('暂无该行业趋势数据');
      }
      if (historyRes.success && historyRes.data) {
        setHistoryData([...historyRes.data].sort(
          (a, b) => a.tradeDate.localeCompare(b.tradeDate),
        ));
      } else {
        setHistoryData([]);
      }
    } catch (err) {
      message.error('数据加载失败：' + (err as Error).message);
      setTrend(null);
      setHistoryData([]);
    } finally {
      setLoading(false);
    }
  }, [selected, period]);

  const handleCalcTrend = async () => {
    setCalcLoading(true);
    try {
      const res = await triggerTrendCalculate();
      if (res.success) {
        message.success('趋势计算完成！');
        loadTrend();
      } else {
        message.error('趋势计算失败：' + (res.message || '未知错误'));
      }
    } catch (err) {
      message.error('趋势计算失败：' + (err as Error).message);
    } finally {
      setCalcLoading(false);
    }
  };

  useEffect(() => {
    loadTrend();
  }, [loadTrend]);

  const trendSlopeYi = trend ? (trend.trendSlope / 10000).toFixed(4) : '0';
  const slopeVal = trend ? trend.trendSlope : 0;
  const rInfo = trend ? rSquaredDesc(trend.rSquared) : { text: '-', color: '#556677' };

  // 趋势折线图配置
  const chartOption = useMemo(() => {
    if (!trend || historyData.length === 0) return null;
    const dates = historyData.map((d) => d.tradeDate);
    const values = historyData.map((d) => d.netAmount);
    // 回归线端点（x=0 ~ x=sampleCount-1）
    const x0 = 0;
    const y0 = trend.intercept ?? 0;
    const xEnd = (trend.sampleCount ?? 1) - 1;
    const yEnd = (trend.intercept ?? 0) + (trend.trendSlope ?? 0) * xEnd;

    return {
      ...darkTheme,
      legend: { ...darkTheme.legend, top: 0, data: ['实际净额', '回归趋势'] },
      tooltip: {
        ...darkTheme.tooltip,
        trigger: 'axis' as const,
        formatter: (params: any) => {
          if (!Array.isArray(params)) return '';
          const idx = params[0]?.dataIndex ?? 0;
          const date = dates[idx] ?? '';
          let html = `<div style="font-weight:600;margin-bottom:4px">${date}</div>`;
          params.forEach((p: any) => {
            if (p.value == null) return;
            const color = p.value >= 0 ? '#f5222d' : '#52c41a';
            html += `<div style="display:flex;align-items:center;gap:6px">
              <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${p.color}"></span>
              <span>${p.seriesName}</span>
              <span style="color:${color};font-weight:600;margin-left:auto">${(p.value / 10000).toFixed(2)} 亿</span>
            </div>`;
          });
          return html;
        },
      },
      xAxis: {
        ...darkTheme.xAxis,
        type: 'category',
        data: dates,
        axisLabel: { ...darkTheme.xAxis?.axisLabel, rotate: dates.length > 20 ? 45 : 0 },
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
      grid: { left: 80, right: 30, top: 40, bottom: dates.length > 20 ? 60 : 40 },
      series: [
        {
          name: '实际净额',
          type: 'line',
          data: values,
          smooth: true,
          symbol: 'circle',
          symbolSize: 4,
          lineStyle: { color: '#1677ff', width: 2 },
          itemStyle: { color: '#1677ff' },
        },
        {
          name: '回归趋势',
          type: 'line',
          data: [
            [x0, y0],
            [xEnd, yEnd],
          ],
          smooth: false,
          symbol: 'none',
          lineStyle: { color: '#f5222d', width: 2, type: 'dashed' },
          itemStyle: { color: '#f5222d' },
        },
      ],
    } as any;
  }, [trend, historyData]);

  const cardStyle = { background: '#1a2332', borderColor: '#2a3a4f' };
  const cardBodyStyle = { padding: isMobile ? '12px 14px' : '16px 24px' };

  // ============ 移动端渲染 ============
  if (isMobile) {
    return (
      <div style={{ padding: 0 }}>
        {/* 移动端选择器 */}
        <div className="mobile-selector-row">
          <span style={{ color: '#8899aa', fontSize: 13 }}>行业</span>
          <Select
            showSearch
            value={selected || undefined}
            options={industries}
            onChange={setSelected}
            placeholder="选择行业"
            style={{ flex: 1, minWidth: 0 }}
            filterOption={(input, option) => (option?.label as string)?.includes(input)}
          />
          <span style={{ color: '#8899aa', fontSize: 13 }}>周期</span>
          <Select value={period} options={periodOptions} onChange={setPeriod} style={{ width: 80 }} />
          <Button
            type="primary"
            icon={<SyncOutlined />}
            onClick={handleCalcTrend}
            loading={calcLoading}
            size="small"
            style={{ background: '#1677ff', marginLeft: 'auto' }}
          >
            计算
          </Button>
        </div>

        <Spin spinning={loading}>
          {chartOption && (
            <div style={{ marginBottom: 12 }}>
              <ReactECharts
                option={chartOption}
                style={{ height: 260 }}
                theme="dark"
                opts={{ renderer: 'canvas' }}
              />
            </div>
          )}
          {trend ? (
            <>
              {/* 移动端趋势统计：纵向堆叠卡片 */}
              <div className="mobile-trend-stats">
                <div className="trend-stat-card">
                  <div className="stat-title">趋势斜率（亿/天）</div>
                  <div style={{ display: 'flex', alignItems: 'baseline' }}>
                    <span className="stat-value" style={{ color: slopeColor(slopeVal) }}>
                      {slopeVal >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />} {trendSlopeYi}
                    </span>
                    <span className="stat-suffix" style={{ color: slopeColor(slopeVal) }}>
                      {slopeVal >= 0 ? '资金流入' : '资金流出'}
                    </span>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: 10 }}>
                  <div className="trend-stat-card" style={{ flex: 1 }}>
                    <div className="stat-title">拟合优度 R²</div>
                    <div className="stat-value" style={{ color: rInfo.color, fontSize: 18 }}>
                      {trend.rSquared?.toFixed(2) ?? '-'}
                      <span className="stat-suffix" style={{ color: rInfo.color }}>{rInfo.text}</span>
                    </div>
                  </div>
                  <div className="trend-stat-card" style={{ flex: 1 }}>
                    <div className="stat-title">样本数</div>
                    <div className="stat-value" style={{ color: '#e8eaf0', fontSize: 18 }}>
                      {trend.sampleCount ?? '-'}
                      <span className="stat-suffix">天</span>
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: 10 }}>
                  <div className="trend-stat-card" style={{ flex: 1 }}>
                    <div className="stat-title">日均净额（亿）</div>
                    <div className="stat-value" style={{ color: trend.avgNetAmount >= 0 ? '#f5222d' : '#52c41a', fontSize: 18 }}>
                      {wanToYi(trend.avgNetAmount ?? 0)}
                    </div>
                  </div>
                  <div className="trend-stat-card" style={{ flex: 1 }}>
                    <div className="stat-title">标准差（亿）</div>
                    <div className="stat-value" style={{ color: '#faad14', fontSize: 18 }}>
                      {wanToYi(trend.stdNetAmount ?? 0)}
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: 10 }}>
                  <div className="trend-stat-card" style={{ flex: 1 }}>
                    <div className="stat-title">最大值（亿）</div>
                    <div className="stat-value" style={{ color: '#f5222d', fontSize: 18 }}>
                      {wanToYi(trend.maxNetAmount ?? 0)}
                    </div>
                  </div>
                  <div className="trend-stat-card" style={{ flex: 1 }}>
                    <div className="stat-title">最小值（亿）</div>
                    <div className="stat-value" style={{ color: '#52c41a', fontSize: 18 }}>
                      {wanToYi(trend.minNetAmount ?? 0)}
                    </div>
                  </div>
                </div>

                <div className="trend-stat-card">
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <div>
                      <div className="stat-title">区间净额合计（亿）</div>
                      <div className="stat-value" style={{ color: trend.totalNetAmount >= 0 ? '#f5222d' : '#52c41a', fontSize: 18 }}>
                        {wanToYi(trend.totalNetAmount ?? 0)}
                      </div>
                    </div>
                    <div style={{ textAlign: 'right' }}>
                      <div className="stat-title">统计周期 / 日期</div>
                      <div style={{ color: '#e8eaf0', fontSize: 15, fontWeight: 600 }}>
                        {trend.statPeriod ?? '-'}天 / {trend.tradeDate ?? '-'}
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* 解读说明 */}
              <div style={{ marginTop: 8, padding: '10px 14px', background: '#141e2e', borderRadius: 6, borderLeft: '3px solid #1677ff' }}>
                <div style={{ color: '#8899aa', fontSize: 12, lineHeight: 1.8 }}>
                  <LineChartOutlined style={{ marginRight: 6 }} />
                  <strong style={{ color: '#b0b8c8' }}>趋势解读：</strong>
                  斜率正=流入趋势，负=流出趋势；R² &gt; 0.7强趋势，0.4-0.7中等，&lt; 0.4弱趋势。
                </div>
              </div>
            </>
          ) : (
            !loading && (
              <div style={{ textAlign: 'center', color: '#556677', padding: 40 }}>
                请选择行业查看趋势数据
              </div>
            )
          )}
        </Spin>

        <div className="mobile-bottom-spacer" />
      </div>
    );
  }

  // ============ 桌面端渲染（保持原样） ============
  return (
    <div style={{ padding: 0 }}>
      <div style={{ display: 'flex', gap: 16, alignItems: 'center', marginBottom: 20 }}>
        <span style={{ color: '#8899aa' }}>行业</span>
        <Select
          showSearch
          value={selected || undefined}
          options={industries}
          onChange={setSelected}
          placeholder="选择行业"
          style={{ width: 200 }}
          filterOption={(input, option) => (option?.label as string)?.includes(input)}
        />
        <span style={{ color: '#8899aa' }}>周期</span>
        <Select value={period} options={periodOptions} onChange={setPeriod} style={{ width: 120 }} />
        <Button
          type="primary"
          icon={<SyncOutlined />}
          onClick={handleCalcTrend}
          loading={calcLoading}
          style={{ background: '#1677ff', marginLeft: 'auto' }}
        >
          计算趋势
        </Button>
      </div>

      <Spin spinning={loading}>
        {chartOption && (
          <div style={{ marginBottom: 20 }}>
            <ReactECharts
              option={chartOption}
              style={{ height: 380 }}
              theme="dark"
              opts={{ renderer: 'canvas' }}
            />
          </div>
        )}
        {trend ? (
          <>
            <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
              <Col span={8}>
                <Card style={cardStyle} styles={{ body: cardBodyStyle }}>
                  <Statistic
                    title={<span style={{ color: '#8899aa' }}>趋势斜率（亿/天）</span>}
                    value={trendSlopeYi}
                    precision={4}
                    valueStyle={{ color: slopeColor(slopeVal), fontWeight: 700, fontSize: 22 }}
                    prefix={slopeVal >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
                    suffix={
                      <span style={{ fontSize: 12, color: slopeColor(slopeVal), marginLeft: 8 }}>
                        {slopeVal >= 0 ? '资金流入趋势' : '资金流出趋势'}
                      </span>
                    }
                  />
                </Card>
              </Col>
              <Col span={8}>
                <Card style={cardStyle} styles={{ body: cardBodyStyle }}>
                  <Statistic
                    title={<span style={{ color: '#8899aa' }}>拟合优度 R²</span>}
                    value={trend.rSquared}
                    precision={2}
                    valueStyle={{ color: rInfo.color, fontWeight: 700, fontSize: 22 }}
                    suffix={
                      <span style={{ fontSize: 12, color: rInfo.color, marginLeft: 8 }}>
                        {rInfo.text}
                      </span>
                    }
                  />
                </Card>
              </Col>
              <Col span={8}>
                <Card style={cardStyle} styles={{ body: cardBodyStyle }}>
                  <Statistic
                    title={<span style={{ color: '#8899aa' }}>样本数</span>}
                    value={trend.sampleCount}
                    suffix="天"
                    valueStyle={{ color: '#e8eaf0', fontWeight: 700, fontSize: 22 }}
                  />
                </Card>
              </Col>
            </Row>

            <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
              <Col span={8}>
                <Card style={cardStyle} styles={{ body: cardBodyStyle }}>
                  <Statistic
                    title={<span style={{ color: '#8899aa' }}>日均净额（亿）</span>}
                    value={wanToYi(trend.avgNetAmount)}
                    precision={2}
                    valueStyle={{ color: trend.avgNetAmount >= 0 ? '#f5222d' : '#52c41a', fontWeight: 700 }}
                  />
                </Card>
              </Col>
              <Col span={8}>
                <Card style={cardStyle} styles={{ body: cardBodyStyle }}>
                  <Statistic
                    title={<span style={{ color: '#8899aa' }}>标准差（亿）</span>}
                    value={wanToYi(trend.stdNetAmount)}
                    precision={2}
                    valueStyle={{ color: '#faad14', fontWeight: 700 }}
                  />
                </Card>
              </Col>
              <Col span={8}>
                <Card style={cardStyle} styles={{ body: cardBodyStyle }}>
                  <div style={{ marginBottom: 8 }}>
                    <span style={{ color: '#8899aa', fontSize: 14 }}>最值（亿）</span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <div>
                      <div style={{ color: '#8899aa', fontSize: 12 }}>最大值</div>
                      <div style={{ color: '#f5222d', fontWeight: 700, fontSize: 18 }}>
                        {wanToYi(trend.maxNetAmount)}
                      </div>
                    </div>
                    <div>
                      <div style={{ color: '#8899aa', fontSize: 12 }}>最小值</div>
                      <div style={{ color: '#52c41a', fontWeight: 700, fontSize: 18 }}>
                        {wanToYi(trend.minNetAmount)}
                      </div>
                    </div>
                  </div>
                </Card>
              </Col>
            </Row>

            <Card style={cardStyle} styles={{ body: cardBodyStyle }}>
              <Row gutter={16}>
                <Col span={8}>
                  <Statistic
                    title={<span style={{ color: '#8899aa' }}>区间净额合计（亿）</span>}
                    value={wanToYi(trend.totalNetAmount)}
                    precision={2}
                    valueStyle={{ color: trend.totalNetAmount >= 0 ? '#f5222d' : '#52c41a', fontWeight: 700 }}
                  />
                </Col>
                <Col span={8}>
                  <Statistic
                    title={<span style={{ color: '#8899aa' }}>统计周期</span>}
                    value={trend.statPeriod}
                    suffix="天"
                    valueStyle={{ color: '#e8eaf0' }}
                  />
                </Col>
                <Col span={8}>
                  <Statistic
                    title={<span style={{ color: '#8899aa' }}>数据日期</span>}
                    value={trend.tradeDate}
                    valueStyle={{ color: '#e8eaf0', fontSize: 16 }}
                  />
                </Col>
              </Row>
            </Card>

            <div style={{ marginTop: 16, padding: '12px 16px', background: '#141e2e', borderRadius: 6, borderLeft: '3px solid #1677ff' }}>
              <div style={{ color: '#8899aa', fontSize: 13, lineHeight: 2 }}>
                <LineChartOutlined style={{ marginRight: 6 }} />
                <strong style={{ color: '#b0b8c8' }}>趋势解读：</strong>
                斜率正值表示资金流入趋势，负值表示资金流出趋势；
                R² &gt; 0.7 为强趋势，0.4-0.7 为中等趋势，&lt; 0.4 为弱趋势。
              </div>
            </div>
          </>
        ) : (
          !loading && (
            <div style={{ textAlign: 'center', color: '#556677', padding: 60 }}>
              请选择行业查看趋势数据
            </div>
          )
        )}
      </Spin>
    </div>
  );
}