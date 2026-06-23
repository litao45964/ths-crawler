#!/usr/bin/env python3
"""
板块下钻数据抓取脚本
由Java通过ProcessBuilder调用

【诞生背景 —— 当初为何要写这个脚本】

用户原话：
  "比如我获取到行业第一，电池流入 44.26 亿后，我要点击进去，
   并点击，板块统计，进一步查看里面的数据"

场景还原：
  板块资金流Top3已经有了，但用户不满足于只看板块层面的净流入数据，
  还想"点进去"看板块内部的成分股表现，特别是"板块统计"里的区间领涨股票。
  对应同花顺APP的操作：板块列表 → 点击某板块 → 点击"板块统计" → 看区间领涨排名。
  本脚本实现：板块代码查询 → 成分股获取 → 区间涨幅计算 → 领涨排名输出。

【诞生背景 —— interval 多日区间功能的由来】

用户原话：
  "当日的数据获取后，我还想获取多日数据，多日特点，系统默认查板块的
   过去 2 周左右，也可以自己选，这样能获取区间的涨跌数据，方便采集后清洗标注"

场景还原：
  top action 拿到的是当日数据，用户还需要"多日区间回顾"——对齐同花顺APP的
  板块详情 → 板块统计 → 区间回顾 → 切换到"多日"标签页。
  APP默认展示过去约2周的区间涨跌排名，也可手动选择日期范围。
  页面上有3个排序Tab：区间涨幅 / 成交额 / 换手率。
  用户采集这些数据是为了后续清洗标注，所以输出要结构化、字段完整。
  本脚本 interval action 专为这个场景设计：默认10个交易日（≈2周），
  支持自定义起止日期，支持按3种指标排序，输出对齐APP截面的6列数据。

用法:
  # 【最常用】板块统计-当日个股涨跌Top15（对齐APP板块统计→区间回顾-单日）
  python3 fetch_sector_detail.py --action top --name 电池 --top 15

  # 【高频】板块统计-多日区间回顾Top15（对齐APP板块统计→区间回顾-多日）
  python3 fetch_sector_detail.py --action interval --name 电池 --top 15

  # 多日区间 - 指定2周（10个交易日）
  python3 fetch_sector_detail.py --action interval --name 电池 --period 10 --top 15

  # 多日区间 - 自定义起止日期
  python3 fetch_sector_detail.py --action interval --name 电池 --start-date 2026-06-01 --end-date 2026-06-15 --top 15

  # 多日区间 - 按成交额排序（APP的第二个Tab）
  python3 fetch_sector_detail.py --action interval --name 电池 --sort amount --top 15

  # 多日区间 - 按换手率排序（APP的第三个Tab）
  python3 fetch_sector_detail.py --action interval --name 电池 --sort turnover --top 15

  # 也可以用板块代码
  python3 fetch_sector_detail.py --action top --code BK0479 --top 15
  python3 fetch_sector_detail.py --action interval --code BK0479 --period 10 --top 15

  # 查询板块代码（板块名 → BK代码）
  python3 fetch_sector_detail.py --action code --name 电池

  # 获取板块成分股列表（全量）
  python3 fetch_sector_detail.py --action cons --code BK0479

  # 获取板块统计（多日区间领涨排名，默认5日，旧版接口保留兼容）
  python3 fetch_sector_detail.py --action stats --code BK0479 --period 5

  # 一键下钻：从板块名直接拿到多日区间领涨数据
  python3 fetch_sector_detail.py --action drill --name 电池 --period 5 --top 10

依赖:
  pip install akshare pandas

返回:
  JSON格式，输出到stdout
"""

import json
import sys
import argparse
import time
from datetime import datetime, timedelta

# ===== Python 3.13 兼容修复 =====
# py_mini_racer 依赖 pkg_resources，而 Python 3.13 移除了 pkgutil.ImpImporter
# monkey-patch 一个空类，让 import 不报错
import pkgutil
if not hasattr(pkgutil, 'ImpImporter'):
    pkgutil.ImpImporter = type('ImpImporter', (), {})

import akshare as ak
import pandas as pd


# ========== 缓存：板块名→代码映射（进程级） ==========
_sector_code_cache = None


def get_sector_code_mapping(sector_type="industry"):
    """获取板块名→代码的映射表，进程级缓存"""
    global _sector_code_cache
    if _sector_code_cache is not None:
        return _sector_code_cache

    try:
        if sector_type == "industry":
            df = ak.stock_board_industry_name_ths()
        else:
            df = ak.stock_board_concept_name_ths()

        # 列名可能是"名称"/"板块名称"等，动态适配
        name_col = None
        code_col = None
        for col in df.columns:
            if "名称" in col or "板块" in col:
                name_col = col
            if "代码" in col:
                code_col = col

        if name_col is None or code_col is None:
            # 降级：用前两列
            name_col = df.columns[0]
            code_col = df.columns[1] if len(df.columns) > 1 else df.columns[0]

        _sector_code_cache = {
            sector_type: dict(zip(df[name_col], df[code_col]))
        }
        return _sector_code_cache

    except Exception as e:
        return {"error": str(e)}


def lookup_sector_code(name, sector_type="industry"):
    """根据板块名查找BK代码"""
    mapping = get_sector_code_mapping(sector_type)
    if "error" in mapping:
        return None, mapping["error"]

    type_mapping = mapping.get(sector_type, {})
    code = type_mapping.get(name)
    if code is None:
        # 模糊匹配
        for k, v in type_mapping.items():
            if name in k or k in name:
                code = v
                break
    return code, None


def fetch_constituents(code, sector_type="industry"):
    """获取板块成分股列表（含当日行情字段）"""
    try:
        if sector_type == "industry":
            df = ak.stock_board_industry_cons_ths(symbol=code)
        else:
            df = ak.stock_board_concept_cons_ths(symbol=code)

        result = []
        for _, row in df.iterrows():
            item = {}
            for col in df.columns:
                val = row[col]
                if pd.isna(val) if isinstance(val, float) else False:
                    item[col] = None
                else:
                    item[col] = str(val) if not isinstance(val, (int, float)) else val
            result.append(item)

        return {
            "sector_code": code,
            "sector_type": sector_type,
            "total_count": len(result),
            "constituents": result
        }

    except Exception as e:
        return {"sector_code": code, "constituents": [], "error": str(e)}


def calc_period_change(symbol, period_days=None, end_date=None, start_date=None):
    """
    计算个股区间涨幅（前复权）

    两种模式:
      1. period_days 模式: 取最近N个交易日的涨跌幅
      2. start_date + end_date 模式: 取指定日期区间的涨跌幅
    """
    try:
        if end_date is None:
            end_date = datetime.now().strftime("%Y%m%d")
        else:
            end_date = end_date.replace("-", "")

        # 确定历史数据拉取的起始日期
        if start_date:
            # 自定义日期模式：从start_date再往前多取10天确保有数据
            fetch_start = (datetime.strptime(start_date.replace("-", ""), "%Y%m%d") - timedelta(days=15)).strftime("%Y%m%d")
            actual_start = start_date.replace("-", "")
        elif period_days:
            # 周期模式：多取几天确保有交易日数据
            end_dt = datetime.strptime(end_date, "%Y%m%d")
            fetch_start_dt = end_dt - timedelta(days=period_days * 2 + 10)
            fetch_start = fetch_start_dt.strftime("%Y%m%d")
            actual_start = None
        else:
            return None

        df = ak.stock_zh_a_hist(
            symbol=symbol,
            period="daily",
            start_date=fetch_start,
            end_date=end_date,
            adjust="qfq"
        )

        if df.empty or len(df) < 2:
            return None

        # 确定实际的起止行
        if actual_start:
            # 自定义日期模式：找 >= actual_start 的第一行作为起始
            start_rows = df[df["日期"] >= actual_start]
            if start_rows.empty:
                return None
            start_idx = start_rows.index[0]
            period_data = df.loc[start_idx:]
        else:
            # 周期模式：取最后period_days+1个交易日
            period_data = df.tail(period_days + 1)

        if len(period_data) < 2:
            return None

        start_price = float(period_data.iloc[0]["收盘"])
        end_price = float(period_data.iloc[-1]["收盘"])
        change_pct = round((end_price - start_price) / start_price * 100, 2)

        # 区间内最高最低
        high = float(period_data["最高"].max())
        low = float(period_data["最低"].min())

        # 区间累计成交额
        amount = float(period_data["成交额"].sum())

        result = {
            "symbol": symbol,
            "period_days": len(period_data) - 1,  # 实际交易日数
            "start_price": start_price,
            "end_price": end_price,
            "change_pct": change_pct,
            "period_high": high,
            "period_low": low,
            "period_amount": amount,
        }
        if actual_start:
            result["start_date"] = actual_start
        result["end_date"] = end_date

        return result

    except Exception:
        return None


def fetch_sector_top(code_or_name, top_n=15, sector_type="industry"):
    """
    板块统计-当日个股涨跌Top N
    对齐同花顺APP: 板块详情 → 板块统计 → 区间回顾（当日/单日）
    一次API调用即出，零额外计算，最轻量的下钻方式

    返回字段对齐APP截图: 排名, 股票名称, 股票代码, 区间涨幅, 成交额, 换手率
    """
    # 如果传的是板块名而非代码，先查代码
    code = code_or_name
    if not code_or_name.startswith("BK"):
        resolved_code, err = lookup_sector_code(code_or_name, sector_type)
        if err:
            return {"error": f"板块代码查询失败: {err}"}
        if resolved_code is None:
            return {"error": f"未找到板块: {code_or_name}"}
        code = resolved_code

    try:
        # 一次调用拿全量成分股
        if sector_type == "industry":
            df = ak.stock_board_industry_cons_ths(symbol=code)
        else:
            df = ak.stock_board_concept_cons_ths(symbol=code)

        if df.empty:
            return {"sector_code": code, "top_stocks": [], "error": "无成分股数据"}

        # 动态识别列名（AKShare版本间列名可能变化）
        col_map = _detect_cons_columns(df)

        # 按涨跌幅降序排序
        sort_col = col_map.get("change_pct")
        if sort_col and sort_col in df.columns:
            df = df.sort_values(by=sort_col, ascending=False).reset_index(drop=True)

        # 取Top N
        top_df = df.head(top_n)

        top_stocks = []
        for _, row in top_df.iterrows():
            item = {
                "rank": len(top_stocks) + 1,
                "name": safe_str(row.get(col_map.get("name", "名称"), "")),
                "code": safe_str(row.get(col_map.get("code", "代码"), "")),
                "change_pct": safe_float(row.get(col_map.get("change_pct", "涨跌幅"), 0)),
                "amount": safe_float(row.get(col_map.get("amount", "成交额"), 0)),
                "turnover_rate": safe_float(row.get(col_map.get("turnover_rate", "换手率"), 0)),
                "latest_price": safe_float(row.get(col_map.get("latest_price", "最新价"), 0)),
            }
            top_stocks.append(item)

        return {
            "sector_code": code,
            "sector_type": sector_type,
            "view": "板块统计-区间回顾(当日)",
            "total_constituents": len(df),
            "top_stocks": top_stocks,
            "trade_date": datetime.now().strftime("%Y-%m-%d")
        }

    except Exception as e:
        return {"sector_code": code, "top_stocks": [], "error": str(e)}


def fetch_sector_interval(code_or_name, top_n=15, period_days=10,
                          start_date=None, end_date=None,
                          sort_by="change", sector_type="industry"):
    """
    板块统计-多日区间回顾 Top N
    对齐同花顺APP: 板块详情 → 板块统计 → 区间回顾 → 多日标签页
    默认10个交易日（≈2周），支持自定义起止日期

    APP截图对应3个排序Tab:
      - 区间涨幅 (sort_by="change")  ← 默认
      - 成交额   (sort_by="amount")
      - 换手率   (sort_by="turnover")

    实现策略:
      1. 一次API拿全量成分股（含当日行情）
      2. 按当日涨跌幅预排序，取Top N*3只计算区间涨幅（降级策略，减少API调用）
      3. 按用户指定维度排序输出

    返回字段对齐APP截图: 排名, 股票名称, 股票代码, 区间涨幅, 成交额, 换手率
    """
    # 如果传的是板块名而非代码，先查代码
    code = code_or_name
    if not code_or_name.startswith("BK"):
        resolved_code, err = lookup_sector_code(code_or_name, sector_type)
        if err:
            return {"error": f"板块代码查询失败: {err}"}
        if resolved_code is None:
            return {"error": f"未找到板块: {code_or_name}"}
        code = resolved_code

    try:
        # Step 1: 拿全量成分股（含当日行情）
        if sector_type == "industry":
            df = ak.stock_board_industry_cons_ths(symbol=code)
        else:
            df = ak.stock_board_concept_cons_ths(symbol=code)

        if df.empty:
            return {"sector_code": code, "interval_stocks": [], "error": "无成分股数据"}

        # 动态识别列名
        col_map = _detect_cons_columns(df)

        # Step 2: 按当日涨跌幅预排序，取候选集（Top N * 3）
        # 策略：当日涨幅高的更可能是区间涨幅高的，减少不必要的API调用
        daily_sort_col = col_map.get("change_pct")
        if daily_sort_col and daily_sort_col in df.columns:
            df = df.sort_values(by=daily_sort_col, ascending=False).reset_index(drop=True)

        candidate_count = min(top_n * 3, len(df))
        candidates = df.head(candidate_count)

        # Step 3: 对候选集逐只计算区间涨幅
        interval_list = []
        for i, (_, row) in enumerate(candidates.iterrows()):
            raw_code = safe_str(row.get(col_map.get("code", "代码"), ""))
            # 提取纯数字代码（去掉市场前缀）
            symbol = raw_code.replace("SZ", "").replace("SH", "").replace(".SZ", "").replace(".SH", "")
            if not symbol.isdigit():
                continue

            # 频率控制：每5只休息1秒
            if i > 0 and i % 5 == 0:
                time.sleep(1)

            # 计算区间涨幅
            period_result = calc_period_change(
                symbol,
                period_days=period_days if not start_date else None,
                end_date=end_date,
                start_date=start_date
            )

            if period_result is not None:
                item = {
                    "name": safe_str(row.get(col_map.get("name", "名称"), "")),
                    "code": raw_code,
                    "interval_change_pct": period_result["change_pct"],
                    "interval_days": period_result["period_days"],
                    "start_price": period_result["start_price"],
                    "end_price": period_result["end_price"],
                    "latest_price": safe_float(row.get(col_map.get("latest_price", "最新价"), 0)),
                    "daily_change_pct": safe_float(row.get(col_map.get("change_pct", "涨跌幅"), 0)),
                    "amount": safe_float(row.get(col_map.get("amount", "成交额"), 0)),
                    "turnover_rate": safe_float(row.get(col_map.get("turnover_rate", "换手率"), 0)),
                    "period_high": period_result["period_high"],
                    "period_low": period_result["period_low"],
                    "period_amount": period_result["period_amount"],
                }
                interval_list.append(item)

        # Step 4: 按用户指定维度排序
        sort_key_map = {
            "change": "interval_change_pct",
            "amount": "amount",
            "turnover": "turnover_rate",
        }
        sort_key = sort_key_map.get(sort_by, "interval_change_pct")
        interval_list.sort(key=lambda x: x.get(sort_key, 0), reverse=True)

        # Step 5: 取Top N，加排名
        top_list = interval_list[:top_n]
        for idx, item in enumerate(top_list):
            item["rank"] = idx + 1

        # 构造描述信息
        if start_date:
            date_desc = f"{start_date} ~ {end_date or '今天'}"
        else:
            date_desc = f"近{period_days}个交易日"

        sort_desc_map = {
            "change": "区间涨幅",
            "amount": "成交额",
            "turnover": "换手率",
        }

        return {
            "sector_code": code,
            "sector_type": sector_type,
            "view": "板块统计-区间回顾(多日)",
            "date_range": date_desc,
            "interval_days": top_list[0]["interval_days"] if top_list else 0,
            "sort_by": sort_desc_map.get(sort_by, sort_by),
            "total_constituents": len(df),
            "candidates_calculated": len(interval_list),
            "interval_stocks": top_list,
            "trade_date": datetime.now().strftime("%Y-%m-%d"),
            "note": f"共计算{len(interval_list)}只候选股的区间数据，按{sort_desc_map.get(sort_by, sort_by)}取Top{top_n}"
        }

    except Exception as e:
        return {"sector_code": code, "interval_stocks": [], "error": str(e)}


def fetch_sector_stats(code, period_days=5, top_n=10, sector_type="industry"):
    """获取板块统计：区间领涨股票排名（旧版接口，保留兼容）"""
    try:
        # Step 1: 获取成分股
        cons_data = fetch_constituents(code, sector_type)
        if "error" in cons_data:
            return cons_data

        constituents = cons_data["constituents"]
        if not constituents:
            return {"sector_code": code, "stats": [], "error": "无成分股数据"}

        # Step 2: 先按日涨幅排序，只对Top N*3只算区间涨幅（降级策略，减少API调用）
        sorted_cons = sorted(
            constituents,
            key=lambda x: safe_float(x.get("涨跌幅", 0)),
            reverse=True
        )
        # 取日涨幅前 top_n*3 只来算区间涨幅（覆盖面更广）
        candidates = sorted_cons[:min(top_n * 3, len(sorted_cons))]

        # Step 3: 批量计算区间涨幅
        stats_list = []
        for i, stock in enumerate(candidates):
            # 提取股票代码（去掉市场前缀）
            raw_code = str(stock.get("代码", ""))
            symbol = raw_code.replace("SZ", "").replace("SH", "").replace(".SZ", "").replace(".SH", "")
            if not symbol.isdigit():
                continue

            # 频率控制
            if i > 0 and i % 5 == 0:
                time.sleep(1)

            result = calc_period_change(symbol, period_days)
            if result is not None:
                # 合并成分股信息和区间涨幅
                merged = {
                    "code": raw_code,
                    "name": stock.get("名称", ""),
                    "latest_price": safe_float(stock.get("最新价", 0)),
                    "daily_change_pct": safe_float(stock.get("涨跌幅", 0)),
                    "period_change_pct": result["change_pct"],
                    "period_days": period_days,
                    "volume": safe_float(stock.get("成交量", 0)),
                    "amount": safe_float(stock.get("成交额", 0)),
                    "turnover_rate": safe_float(stock.get("换手率", 0))
                }
                stats_list.append(merged)

        # Step 4: 按区间涨幅排序
        stats_list.sort(key=lambda x: x["period_change_pct"], reverse=True)

        return {
            "sector_code": code,
            "sector_type": sector_type,
            "period_days": period_days,
            "total_candidates": len(candidates),
            "calculated_count": len(stats_list),
            "top_leading_stocks": stats_list[:top_n],
            "trade_date": datetime.now().strftime("%Y-%m-%d")
        }

    except Exception as e:
        return {"sector_code": code, "stats": [], "error": str(e)}


def drill_down(name, period_days=5, top_n=10, sector_type="industry"):
    """一键下钻：板块名 → 区间领涨数据"""
    # Step 1: 查板块代码
    code, err = lookup_sector_code(name, sector_type)
    if err:
        return {"error": f"板块代码查询失败: {err}"}
    if code is None:
        return {"error": f"未找到板块: {name}"}

    # Step 2: 获取板块统计
    return fetch_sector_stats(code, period_days, top_n, sector_type)


def _detect_cons_columns(df):
    """
    动态识别成分股DataFrame的列名映射
    AKShare版本间列名可能变化，统一映射为内部key
    """
    col_map = {}
    for col in df.columns:
        if "涨跌幅" in col:
            col_map["change_pct"] = col
        elif "成交额" in col:
            col_map["amount"] = col
        elif "换手率" in col:
            col_map["turnover_rate"] = col
        elif "最新价" in col:
            col_map["latest_price"] = col
        elif col == "代码":
            col_map["code"] = col
        elif col == "名称":
            col_map["name"] = col
    return col_map


def safe_float(value, default=0.0):
    if value is None or (isinstance(value, float) and pd.isna(value)):
        return default
    try:
        # 处理字符串中的%号
        if isinstance(value, str):
            value = value.replace("%", "").replace("亿", "").strip()
        return float(value)
    except (ValueError, TypeError):
        return default


def safe_str(value, default=""):
    if value is None or (isinstance(value, float) and pd.isna(value)):
        return default
    try:
        return str(value).strip()
    except (ValueError, TypeError):
        return default


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="同花顺板块下钻数据抓取")
    parser.add_argument("--action",
                        choices=["code", "cons", "top", "interval", "stats", "drill"],
                        default="top",
                        help="操作: code=查板块代码, cons=成分股(全量), "
                             "top=板块统计TopN-当日(最常用), "
                             "interval=板块统计TopN-多日区间(对齐APP区间回顾-多日), "
                             "stats=多日区间领涨(旧版), drill=一键下钻")
    parser.add_argument("--name", type=str, help="板块名称（如：电池）")
    parser.add_argument("--code", type=str, help="板块代码（如：BK0479）")
    parser.add_argument("--type", choices=["industry", "concept"], default="industry",
                        help="板块类型: industry=行业, concept=概念")
    parser.add_argument("--period", type=int, default=10,
                        help="区间交易日数（默认10日≈2周，仅interval/stats/drill生效）")
    parser.add_argument("--top", type=int, default=15, help="返回Top N（默认15）")
    parser.add_argument("--start-date", type=str, default=None,
                        help="区间起始日期 YYYY-MM-DD（仅interval生效，设置后--period失效）")
    parser.add_argument("--end-date", type=str, default=None,
                        help="区间结束日期 YYYY-MM-DD（仅interval生效，默认今天）")
    parser.add_argument("--sort", choices=["change", "amount", "turnover"], default="change",
                        help="排序维度: change=区间涨幅(默认), amount=成交额, turnover=换手率（仅interval生效）")

    args = parser.parse_args()

    if args.action == "code":
        if not args.name:
            result = {"error": "请提供板块名称 --name"}
        else:
            code, err = lookup_sector_code(args.name, args.type)
            if err:
                result = {"error": err}
            elif code is None:
                result = {"error": f"未找到板块: {args.name}"}
            else:
                result = {"name": args.name, "code": code, "sector_type": args.type}

    elif args.action == "cons":
        if not args.code:
            result = {"error": "请提供板块代码 --code"}
        else:
            result = fetch_constituents(args.code, args.type)

    elif args.action == "top":
        # 板块统计-当日涨跌Top N（对齐APP板块统计→区间回顾-单日）
        target = args.name or args.code
        if not target:
            result = {"error": "请提供板块名称 --name 或板块代码 --code"}
        else:
            result = fetch_sector_top(target, args.top, args.type)

    elif args.action == "interval":
        # 板块统计-多日区间Top N（对齐APP板块统计→区间回顾-多日）
        target = args.name or args.code
        if not target:
            result = {"error": "请提供板块名称 --name 或板块代码 --code"}
        else:
            result = fetch_sector_interval(
                target,
                top_n=args.top,
                period_days=args.period,
                start_date=args.start_date,
                end_date=args.end_date,
                sort_by=args.sort,
                sector_type=args.type
            )

    elif args.action == "stats":
        if not args.code:
            result = {"error": "请提供板块代码 --code"}
        else:
            result = fetch_sector_stats(args.code, args.period, args.top, args.type)

    elif args.action == "drill":
        if not args.name:
            result = {"error": "一键下钻需提供板块名称 --name"}
        else:
            result = drill_down(args.name, args.period, args.top, args.type)

    print(json.dumps(result, ensure_ascii=False, default=str))
