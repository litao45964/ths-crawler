import type { EChartsOption } from 'echarts';

const darkTheme: EChartsOption = {
  backgroundColor: 'transparent',
  textStyle: {
    color: '#b0b8c8',
  },
  title: {
    textStyle: { color: '#e8eaf0', fontSize: 16 },
  },
  legend: {
    textStyle: { color: '#b0b8c8' },
  },
  tooltip: {
    backgroundColor: 'rgba(20, 30, 50, 0.95)',
    borderColor: '#304060',
    textStyle: { color: '#e8eaf0', fontSize: 13 },
  },
  grid: {
    left: 100,
    right: 40,
    top: 20,
    bottom: 40,
  },
  xAxis: {
    axisLine: { lineStyle: { color: '#304060' } },
    axisLabel: { color: '#8899aa' },
    splitLine: { lineStyle: { color: '#1e2d45' } },
  },
  yAxis: {
    axisLine: { lineStyle: { color: '#304060' } },
    axisLabel: { color: '#8899aa' },
    splitLine: { lineStyle: { color: '#1e2d45' } },
  },
};

export default darkTheme;
