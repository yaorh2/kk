package com.kline.training.data

/**
 * K线数据模型
 */
data class KlineData(
    val date: String,
    val open: Float,
    val close: Float,
    val high: Float,
    val low: Float,
    val volume: Long
) {
    /**
     * 是否为阳线
     */
    val isPositive: Boolean get() = close >= open
    
    /**
     * 涨跌幅
     */
    val changePercent: Float get() = (close - open) / open * 100
}

/**
 * 股票数据封装
 */
data class StockData(
    val code: String,
    val name: String,
    val market: String,
    val totalDays: Int,
    val startDate: String,
    val endDate: String,
    val data: List<KlineData>
)

/**
 * 交易类型
 */
enum class TradeType {
    BUY,    // 买入
    SELL    // 卖出
}

/**
 * 交易记录
 */
data class TradeRecord(
    val id: Long = System.currentTimeMillis(),
    val date: String,
    val type: TradeType,
    val price: Float,
    val quantity: Int,
    val amount: Float,
    val klineIndex: Int,
    val profit: Float = 0f  // 卖出时的盈亏
)

/**
 * 技术指标数据
 */
data class TechnicalIndicators(
    val ma5: List<Float?>,
    val ma10: List<Float?>,
    val ma20: List<Float?>,
    val macd: List<MACDData>,
    val kdj: List<KDJData>,
    val rsi: List<Float?>
)

data class MACDData(
    val dif: Float,
    val dea: Float,
    val macd: Float
)

data class KDJData(
    val k: Float,
    val d: Float,
    val j: Float
)

/**
 * 训练会话数据
 */
data class TrainingSession(
    val sessionId: String,
    val stockCode: String,
    val stockName: String,
    val startTime: Long,
    var endTime: Long? = null,
    var initialCapital: Float = 100000f,
    var currentCapital: Float = 100000f,
    var position: Int = 0,
    var avgCost: Float = 0f,
    var currentKlineIndex: Int = 0,
    val tradeRecords: MutableList<TradeRecord> = mutableListOf(),
    var totalTrades: Int = 0,
    var winningTrades: Int = 0,
    var maxDrawdown: Float = 0f,
    var peakCapital: Float = 100000f,
    var finalProfit: Float = 0f
)

/**
 * 统计数据
 */
data class StatisticsData(
    val totalSessions: Int,
    val totalTrades: Int,
    val winningTrades: Int,
    val winRate: Float,
    val totalProfitLoss: Float,
    val averageReturn: Float,
    val maxDrawdown: Float,
    val bestReturn: Float,
    val worstReturn: Float,
    val averageHoldingDays: Float
)
