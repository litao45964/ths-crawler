#!/usr/bin/env python3
"""
板块资金流向抓取脚本
由Java通过ProcessBuilder调用

【诞生背景 —— 当初为何要写这个脚本】

用户原话：
  "①建接口方便后续扩展OkHttp方案 ②基于接口实现AKShare方案"

场景还原：
  用户要获取A股行业板块和概念板块资金净流入前三名的数据。
  同花顺PC端接口需要 hexin-v 反爬验证头（JS动态生成，逆向难度高且随时失效），
  而手机端API尚未抓包确认。所以采用分两步走策略：
  - 第一步：用 AKShare（Python金融数据开源库）快速跑通全链路，20行代码出活
  - 第二步：后续真机抓包确认手机端API后，迁移到 OkHttp 原生方案
  Java 项目通过 DataFetcher 接口抽象，用 @ConditionalOnProperty 配置切换：
  ths.fetcher.sector-flow=akshare（当前） / okhttp（后续）
  本脚本就是 AKShare 方案的数据获取层，Java 端通过 ProcessBuilder 调用，
  拿到 stdout 的 JSON 后清洗入库。

【降级策略 —— 2026-06-17补充】

用户原话：
  "云环境配置不能升级了么"

场景还原：
  云环境出站代理拦截了东方财富(push2.eastmoney.com)的HTTPS请求，
  导致AKShare主力接口 stock_sector_fund_flow_rank() 无法使用。
  但同花顺自己的板块一览页 q.10jqka.com.cn 在云环境可通，
  stock_board_industry_summary_ths() 走该页面获取数据（含净流入），
  因此新增降级链路：
  - 首选：stock_sector_fund_flow_rank()（东方财富，字段最全）
  - 降级：stock_board_industry_summary_ths()（同花顺，仅行业板块有净流入）
  概念板块暂无可用降级源（concept_summary_ths 无净流入字段）。

用法:
  python3 fetch_sector_flow.py --type industry
  python3 fetch_sector_flow.py --type concept

依赖:
  pip install akshare pandas

返回:
  JSON格式，输出到stdout
  {
    "board_type": "industry",
    "trade_date": "2026-06-17",
    "source": "eastmoney|ths_summary",
    "data": [
      {
        "rank": 1,
        "board_name": "半导体",
        "change_percent": 2.35,
        "main_net_inflow": 1580000000.0,
        ...
      }
    ]
  }
"""

import json
import sys
import argparse
from datetime import datetime

# Python 3.13兼容：py_mini_racer依赖pkgutil.ImpImporter，3.13已移除
import pkgutil
if not hasattr(pkgutil, 'ImpImporter'):
    pkgutil.ImpImporter = type('ImpImporter', (), {})

import akshare as ak
import pandas as pd


def fetch_sector_flow(sector_type: str) -> dict:
    """
    抓取板块资金流向排名（含自动降级）

    Args:
        sector_type: "industry"(行业) 或 "concept"(概念)

    Returns:
        dict: 包含板块资金流向数据
    """
    # 首选：东方财富接口（字段最全）
    result = _fetch_eastmoney(sector_type)
    if result["data"]:
        return result

    # 降级：同花顺板块一览（仅行业板块有净流入）
    if sector_type == "industry":
        result = _fetch_ths_summary()
        if result["data"]:
            return result

    # 概念板块无降级源，返回空数据
    return result


def _fetch_eastmoney(sector_type: str) -> dict:
    """首选：东方财富资金流向排名"""
    try:
        if sector_type == "industry":
            sector_type_param = "行业资金流"
        elif sector_type == "concept":
            sector_type_param = "概念资金流"
        else:
            raise ValueError(f"不支持的板块类型: {sector_type}")

        df = ak.stock_sector_fund_flow_rank(
            indicator="今日",
            sector_type=sector_type_param
        )

        if "主力净流入-净额" in df.columns:
            df = df.sort_values(by="主力净流入-净额", ascending=False).reset_index(drop=True)

        data_list = []
        for idx, row in df.iterrows():
            item = {
                "rank": idx + 1,
                "board_name": safe_str(row.get("名称", row.get("板块", ""))),
                "change_percent": safe_float(row.get("涨跌幅", 0)),
                "main_net_inflow": safe_float(row.get("主力净流入-净额", 0)),
                "main_net_inflow_ratio": safe_float(row.get("主力净流入-净占比", 0)),
                "super_large_net_inflow": safe_float(row.get("超大单净流入-净额", 0)),
                "super_large_net_inflow_ratio": safe_float(row.get("超大单净流入-净占比", 0)),
                "large_net_inflow": safe_float(row.get("大单净流入-净额", 0)),
                "large_net_inflow_ratio": safe_float(row.get("大单净流入-净占比", 0)),
                "medium_net_inflow": safe_float(row.get("中单净流入-净额", 0)),
                "medium_net_inflow_ratio": safe_float(row.get("中单净流入-净占比", 0)),
                "small_net_inflow": safe_float(row.get("小单净流入-净额", 0)),
                "small_net_inflow_ratio": safe_float(row.get("小单净流入-净占比", 0)),
                "lead_stock": safe_str(row.get("领涨股票", row.get("领涨股", ""))),
                "lead_change_percent": safe_float(row.get("领涨股票涨跌幅", row.get("领涨股涨跌幅", 0))),
                "up_count": safe_int(row.get("上涨家数", 0)),
                "down_count": safe_int(row.get("下跌家数", 0)),
            }
            data_list.append(item)

        return {
            "board_type": sector_type,
            "trade_date": datetime.now().strftime("%Y-%m-%d"),
            "source": "eastmoney",
            "data": data_list
        }

    except Exception as e:
        return {
            "board_type": sector_type,
            "trade_date": datetime.now().strftime("%Y-%m-%d"),
            "source": "eastmoney",
            "data": [],
            "error": str(e)
        }


def _fetch_ths_summary() -> dict:
    """降级：同花顺行业板块一览（q.10jqka.com.cn，云环境可通）"""
    try:
        df = ak.stock_board_industry_summary_ths()

        # 按净流入降序排序
        df['净流入'] = pd.to_numeric(df['净流入'], errors='coerce')
        df = df.sort_values(by='净流入', ascending=False).reset_index(drop=True)

        data_list = []
        for idx, row in df.iterrows():
            item = {
                "rank": idx + 1,
                "board_name": safe_str(row.get("板块", "")),
                "change_percent": safe_float(row.get("涨跌幅", 0)),
                "main_net_inflow": safe_float(row.get("净流入", 0)),
                "main_net_inflow_ratio": None,  # THS一览无此字段
                "super_large_net_inflow": None,
                "super_large_net_inflow_ratio": None,
                "large_net_inflow": None,
                "large_net_inflow_ratio": None,
                "medium_net_inflow": None,
                "medium_net_inflow_ratio": None,
                "small_net_inflow": None,
                "small_net_inflow_ratio": None,
                "lead_stock": safe_str(row.get("领涨股", "")),
                "lead_change_percent": safe_float(row.get("领涨股-涨跌幅", 0)),
                "up_count": safe_int(row.get("上涨家数", 0)),
                "down_count": safe_int(row.get("下跌家数", 0)),
            }
            data_list.append(item)

        return {
            "board_type": "industry",
            "trade_date": datetime.now().strftime("%Y-%m-%d"),
            "source": "ths_summary",
            "data": data_list
        }

    except Exception as e:
        return {
            "board_type": "industry",
            "trade_date": datetime.now().strftime("%Y-%m-%d"),
            "source": "ths_summary",
            "data": [],
            "error": str(e)
        }


def safe_float(value, default=0.0):
    """安全转换为float"""
    if value is None or (isinstance(value, float) and pd.isna(value)):
        return default
    try:
        return float(value)
    except (ValueError, TypeError):
        return default


def safe_int(value, default=0):
    """安全转换为int"""
    if value is None or (isinstance(value, float) and pd.isna(value)):
        return default
    try:
        return int(value)
    except (ValueError, TypeError):
        return default


def safe_str(value, default=""):
    """安全转换为str"""
    if value is None or (isinstance(value, float) and pd.isna(value)):
        return default
    try:
        return str(value).strip()
    except (ValueError, TypeError):
        return default


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="同花顺板块资金流向抓取")
    parser.add_argument("--type", choices=["industry", "concept"], default="industry",
                        help="板块类型: industry=行业, concept=概念")
    args = parser.parse_args()

    result = fetch_sector_flow(args.type)
    print(json.dumps(result, ensure_ascii=False, default=str))
