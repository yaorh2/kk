#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
K线训练APP - A股历史数据获取脚本
使用AKShare开源库获取股票历史日K数据
"""

import akshare as ak
import pandas as pd
import json
import os
import time
from datetime import datetime, timedelta
from tqdm import tqdm
import logging

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# 数据保存目录
DATA_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'data')
os.makedirs(DATA_DIR, exist_ok=True)

# 配置文件路径
CONFIG_FILE = os.path.join(DATA_DIR, 'config.json')


def load_config():
    """加载配置文件"""
    if os.path.exists(CONFIG_FILE):
        with open(CONFIG_FILE, 'r', encoding='utf-8') as f:
            return json.load(f)
    return {
        'last_update': None,
        'stock_list': [],
        'update_frequency': 'monthly'
    }


def save_config(config):
    """保存配置文件"""
    with open(CONFIG_FILE, 'w', encoding='utf-8') as f:
        json.dump(config, f, ensure_ascii=False, indent=2)


def get_stock_list():
    """获取A股股票列表"""
    logger.info("正在获取A股股票列表...")
    try:
        # 获取沪市A股
        stock_sh = ak.stock_info_sh_name_code(symbol="主板A股")
        # 获取深市A股
        stock_sz = ak.stock_info_sz_name_code(symbol="A股列表")
        
        # 合并股票列表
        stock_list = []
        
        # 处理沪市股票
        for _, row in stock_sh.iterrows():
            stock_list.append({
                'code': row['证券代码'],
                'name': row['证券简称'],
                'market': 'SH'
            })
        
        # 处理深市股票
        for _, row in stock_sz.iterrows():
            stock_list.append({
                'code': row['证券代码'],
                'name': row['证券简称'],
                'market': 'SZ'
            })
        
        logger.info(f"共获取到 {len(stock_list)} 只A股")
        return stock_list
    except Exception as e:
        logger.error(f"获取股票列表失败: {e}")
        return []


def get_stock_history(stock_code, market='SH', start_date='20100101', end_date=None):
    """获取单只股票的历史日K数据"""
    if end_date is None:
        end_date = datetime.now().strftime('%Y%m%d')
    
    try:
        # 使用AKShare获取股票历史数据
        if market == 'SH':
            df = ak.stock_zh_a_hist(symbol=stock_code, period="daily", 
                                     start_date=start_date, end_date=end_date, adjust="qfq")
        else:  # SZ
            df = ak.stock_zh_a_hist(symbol=stock_code, period="daily",
                                     start_date=start_date, end_date=end_date, adjust="qfq")
        
        # 数据清洗和重命名
        df = df.rename(columns={
            '日期': 'date',
            '开盘': 'open',
            '收盘': 'close',
            '最高': 'high',
            '最低': 'low',
            '成交量': 'volume',
            '成交额': 'amount',
            '振幅': 'amplitude',
            '涨跌幅': 'change_pct',
            '涨跌额': 'change_amount',
            '换手率': 'turnover'
        })
        
        # 选择需要的列
        df = df[['date', 'open', 'close', 'high', 'low', 'volume']].copy()
        
        # 数据类型转换
        df['date'] = pd.to_datetime(df['date']).dt.strftime('%Y-%m-%d')
        for col in ['open', 'close', 'high', 'low', 'volume']:
            df[col] = pd.to_numeric(df[col], errors='coerce')
        
        # 去除空值
        df = df.dropna()
        
        # 按日期排序
        df = df.sort_values('date').reset_index(drop=True)
        
        return df
    except Exception as e:
        logger.error(f"获取股票 {stock_code} 历史数据失败: {e}")
        return None


def save_stock_data(stock_code, stock_name, df, market='SH'):
    """保存股票数据为JSON格式"""
    if df is None or len(df) == 0:
        return False
    
    # 转换为字典列表
    data_list = df.to_dict('records')
    
    # 构建完整数据结构
    stock_data = {
        'code': stock_code,
        'name': stock_name,
        'market': market,
        'total_days': len(data_list),
        'start_date': data_list[0]['date'] if data_list else None,
        'end_date': data_list[-1]['date'] if data_list else None,
        'data': data_list
    }
    
    # 保存文件
    filename = f"{stock_code}_{market}.json"
    filepath = os.path.join(DATA_DIR, filename)
    
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(stock_data, f, ensure_ascii=False, indent=2)
    
    return True


def load_existing_data(stock_code, market='SH'):
    """加载已有的股票数据"""
    filename = f"{stock_code}_{market}.json"
    filepath = os.path.join(DATA_DIR, filename)
    
    if os.path.exists(filepath):
        with open(filepath, 'r', encoding='utf-8') as f:
            return json.load(f)
    return None


def incremental_update(stock_code, stock_name, market='SH'):
    """增量更新股票数据"""
    existing_data = load_existing_data(stock_code, market)
    
    if existing_data is None:
        # 没有现有数据，全量获取
        df = get_stock_history(stock_code, market)
        return save_stock_data(stock_code, stock_name, df, market)
    else:
        # 增量更新
        last_date = existing_data['end_date']
        if last_date:
            start_date = datetime.strptime(last_date, '%Y-%m-%d') + timedelta(days=1)
            start_date_str = start_date.strftime('%Y%m%d')
            end_date_str = datetime.now().strftime('%Y%m%d')
            
            if start_date_str <= end_date_str:
                df_new = get_stock_history(stock_code, market, start_date=start_date_str)
                if df_new is not None and len(df_new) > 0:
                    # 合并数据
                    existing_df = pd.DataFrame(existing_data['data'])
                    combined_df = pd.concat([existing_df, df_new], ignore_index=True)
                    combined_df = combined_df.drop_duplicates(subset=['date'], keep='last')
                    combined_df = combined_df.sort_values('date').reset_index(drop=True)
                    
                    # 保存更新后的数据
                    return save_stock_data(stock_code, stock_name, combined_df, market)
        return True


def fetch_all_stocks(limit=100):
    """获取所有股票数据（限制数量）"""
    config = load_config()
    stock_list = get_stock_list()
    
    if not stock_list:
        logger.error("未获取到股票列表")
        return
    
    # 限制股票数量（默认100只）
    if limit and limit < len(stock_list):
        stock_list = stock_list[:limit]
    
    logger.info(f"开始获取 {len(stock_list)} 只股票的数据...")
    
    success_count = 0
    failed_stocks = []
    
    for stock in tqdm(stock_list, desc="获取股票数据"):
        code = stock['code']
        name = stock['name']
        market = stock['market']
        
        try:
            if incremental_update(code, name, market):
                success_count += 1
            else:
                failed_stocks.append(f"{code} - {name}")
            
            # 避免请求过快
            time.sleep(0.1)
        except Exception as e:
            logger.error(f"处理股票 {code} 时出错: {e}")
            failed_stocks.append(f"{code} - {name}")
    
    # 更新配置
    config['last_update'] = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    config['stock_list'] = [s['code'] for s in stock_list]
    save_config(config)
    
    logger.info(f"数据获取完成！成功: {success_count}, 失败: {len(failed_stocks)}")
    
    if failed_stocks:
        logger.info(f"失败的股票: {failed_stocks[:10]}")
    
    return success_count


def create_sample_dataset():
    """创建示例数据集（包含一些知名股票）"""
    sample_stocks = [
        {'code': '000001', 'name': '平安银行', 'market': 'SZ'},
        {'code': '000002', 'name': '万科A', 'market': 'SZ'},
        {'code': '600519', 'name': '贵州茅台', 'market': 'SH'},
        {'code': '601318', 'name': '中国平安', 'market': 'SH'},
        {'code': '600036', 'name': '招商银行', 'market': 'SH'},
        {'code': '000858', 'name': '五粮液', 'market': 'SZ'},
        {'code': '601398', 'name': '工商银行', 'market': 'SH'},
        {'code': '600900', 'name': '长江电力', 'market': 'SH'},
        {'code': '002594', 'name': '比亚迪', 'market': 'SZ'},
        {'code': '300750', 'name': '宁德时代', 'market': 'SZ'},
    ]
    
    logger.info("开始创建示例数据集...")
    success_count = 0
    
    for stock in tqdm(sample_stocks, desc="获取示例股票数据"):
        code = stock['code']
        name = stock['name']
        market = stock['market']
        
        try:
            df = get_stock_history(code, market)
            if save_stock_data(code, name, df, market):
                success_count += 1
            time.sleep(0.5)
        except Exception as e:
            logger.error(f"获取示例股票 {code} 失败: {e}")
    
    logger.info(f"示例数据集创建完成！成功获取 {success_count} 只股票")
    return success_count


def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(description='A股历史数据获取工具')
    parser.add_argument('--mode', type=str, default='sample', 
                       choices=['sample', 'all', 'update'],
                       help='运行模式: sample(示例数据), all(全部数据), update(增量更新)')
    parser.add_argument('--limit', type=int, default=100,
                       help='获取股票数量限制（仅all模式有效）')
    
    args = parser.parse_args()
    
    if args.mode == 'sample':
        create_sample_dataset()
    elif args.mode == 'all':
        fetch_all_stocks(limit=args.limit)
    elif args.mode == 'update':
        config = load_config()
        if config['stock_list']:
            logger.info("开始增量更新...")
            # 这里简化处理，实际需要遍历已有的股票代码进行更新
            create_sample_dataset()  # 演示用
        else:
            logger.info("没有已保存的股票列表，先获取示例数据...")
            create_sample_dataset()


if __name__ == '__main__':
    main()
