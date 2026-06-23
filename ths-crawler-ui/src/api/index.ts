import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

// ============ 类型定义 ============

export interface IndustryFlowItem {
  tradeDate: string;
  industryCode: string;
  industryName: string;
  netAmount: number;
  inflowAmount: number;
  outflowAmount: number;
  industryChangePct: number;
  leadingStock: string;
  leadingStockPct: number;
  rank: number | null;
}

export interface TrendData {
  id: number;
  tradeDate: string;
  industryName: string;
  statPeriod: number;
  sampleCount: number;
  trendSlope: number;
  intercept: number;
  rSquared: number;
  totalNetAmount: number;
  avgNetAmount: number;
  stdNetAmount: number;
  minNetAmount: number;
  maxNetAmount: number;
}

export interface ResonanceItem {
  industryName: string;
  tradeDate: string;
  shortPeriod: number;
  longPeriod: number;
  shortSlope: number;
  shortRSquared: number;
  longSlope: number;
  longRSquared: number;
  signalType: string;
  signalDesc: string;
  shortAvgNet: number;
  longAvgNet: number;
}

export interface CollectResult {
  success: boolean;
  totalRows: number;
  insertedRows: number;
  tradeDate: string;
  costMs: number;
}

// ============ API 调用 ============

/** 查询最新日度行业资金流向排行 */
export async function fetchLatestFlow(topN = 10, orderBy = 'net_amount') {
  const res = await api.get<{ success: boolean; data: IndustryFlowItem[]; count: number }>(
    '/api/industry-flow/latest',
    { params: { topN, orderBy } },
  );
  return res.data;
}

/** 查询单行业趋势 */
export async function fetchTrend(industry: string, period = 22) {
  const res = await api.get<{
    success: boolean;
    industry: string;
    period: number;
    tradeDate: string;
    data: TrendData;
  }>('/api/industry-flow/trend', { params: { industry, period } });
  return res.data;
}

/** 长短周期共振信号 */
export async function fetchResonance(shortPeriod = 5, longPeriod = 22) {
  const res = await api.get<{
    success: boolean;
    shortPeriod: number;
    longPeriod: number;
    data: ResonanceItem[];
    count: number;
  }>('/api/industry-flow/resonance', { params: { shortPeriod, longPeriod } });
  return res.data;
}

/** 手动触发日度采集 */
export async function triggerCollect() {
  const res = await api.post<CollectResult>('/api/industry-flow/collect');
  return res.data;
}

/** 查询行业名称列表 */
export async function fetchIndustries() {
  const res = await api.get<{ success: boolean; data: string[]; count: number }>(
    '/api/industry-flow/industries',
  );
  return res.data;
}

/** 查询单行业历史净额序列 */
export async function fetchHistory(industry: string, days = 60) {
  const res = await api.get<{
    success: boolean;
    industry: string;
    days: number;
    data: IndustryFlowItem[];
    count: number;
  }>('/api/industry-flow/history', { params: { industry, days } });
  return res.data;
}

/** 手动触发趋势计算 */
export async function triggerTrendCalculate() {
  const res = await api.post<{ success: boolean }>('/api/industry-flow/trend/calculate');
  return res.data;
}

// ============ 工具函数 ============

/** 万元转亿元，保留2位小数 */
export function wanToYi(value: number): string {
  return (value / 10000).toFixed(2);
}

/** 格式化金额（亿元），正数红色、负数绿色（A股惯例） */
export function formatAmount(value: number): { text: string; color: string } {
  const yi = wanToYi(value);
  const color = value >= 0 ? '#f5222d' : '#52c41a';
  return { text: yi, color };
}

/** 格式化百分比，正数红色、负数绿色 */
export function formatPct(value: number): { text: string; color: string } {
  const text = value >= 0 ? `+${value.toFixed(2)}%` : `${value.toFixed(2)}%`;
  const color = value >= 0 ? '#f5222d' : '#52c41a';
  return { text, color };
}

export default api;
