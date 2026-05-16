package com.kline.training.data

/**
 * 技术指标计算器
 */
object IndicatorCalculator {

    /**
     * 计算移动平均线
     */
    fun calculateMA(prices: List<Float>, period: Int): List<Float?> {
        val result = mutableListOf<Float?>()
        for (i in prices.indices) {
            if (i < period - 1) {
                result.add(null)
            } else {
                var sum = 0f
                for (j in i - period + 1..i) {
                    sum += prices[j]
                }
                result.add(sum / period)
            }
        }
        return result
    }

    /**
     * 计算MACD
     */
    fun calculateMACD(prices: List<Float>): List<MACDData> {
        val result = mutableListOf<MACDData>()
        
        // EMA12, EMA26
        val ema12 = calculateEMA(prices, 12)
        val ema26 = calculateEMA(prices, 26)
        
        // DIF = EMA12 - EMA26
        val difList = mutableListOf<Float>()
        for (i in prices.indices) {
            val dif = (ema12.getOrNull(i) ?: 0f) - (ema26.getOrNull(i) ?: 0f)
            difList.add(dif)
        }
        
        // DEA = EMA9 of DIF
        val dea = calculateEMA(difList, 9)
        
        // MACD柱状图 = 2 * (DIF - DEA)
        for (i in prices.indices) {
            val difVal = difList.getOrNull(i) ?: 0f
            val deaVal = dea.getOrNull(i) ?: 0f
            val macdVal = 2 * (difVal - deaVal)
            result.add(MACDData(difVal, deaVal, macdVal))
        }
        
        return result
    }

    /**
     * 计算指数移动平均
     */
    private fun calculateEMA(prices: List<Float>, period: Int): List<Float> {
        val result = mutableListOf<Float>()
        val multiplier = 2f / (period + 1)
        
        for (i in prices.indices) {
            if (i == 0) {
                result.add(prices[i])
            } else {
                val ema = prices[i] * multiplier + result[i - 1] * (1 - multiplier)
                result.add(ema)
            }
        }
        
        return result
    }

    /**
     * 计算KDJ指标
     */
    fun calculateKDJ(
        highs: List<Float>,
        lows: List<Float>,
        closes: List<Float>,
        n: Int = 9,
        m1: Int = 3,
        m2: Int = 3
    ): List<KDJData> {
        val result = mutableListOf<KDJData>()
        val rsvList = mutableListOf<Float>()
        
        // 计算RSV
        for (i in closes.indices) {
            if (i < n - 1) {
                rsvList.add(50f)
            } else {
                var high = Float.MIN_VALUE
                var low = Float.MAX_VALUE
                for (j in i - n + 1..i) {
                    high = maxOf(high, highs[j])
                    low = minOf(low, lows[j])
                }
                val rsv = if (high != low) {
                    (closes[i] - low) / (high - low) * 100
                } else {
                    50f
                }
                rsvList.add(rsv)
            }
        }
        
        // 计算K
        val kList = mutableListOf<Float>()
        for (i in rsvList.indices) {
            if (i == 0) {
                kList.add(50f)
            } else {
                val k = (kList[i - 1] * (m1 - 1) + rsvList[i]) / m1
                kList.add(k)
            }
        }
        
        // 计算D
        val dList = mutableListOf<Float>()
        for (i in kList.indices) {
            if (i == 0) {
                dList.add(50f)
            } else {
                val d = (dList[i - 1] * (m2 - 1) + kList[i]) / m2
                dList.add(d)
            }
        }
        
        // 计算J = 3K - 2D
        for (i in kList.indices) {
            val j = 3 * kList[i] - 2 * dList[i]
            result.add(KDJData(kList[i], dList[i], j))
        }
        
        return result
    }

    /**
     * 计算RSI指标
     */
    fun calculateRSI(prices: List<Float>, period: Int = 14): List<Float?> {
        val result = mutableListOf<Float?>()
        
        val changes = mutableListOf<Float>()
        for (i in 1 until prices.size) {
            changes.add(prices[i] - prices[i - 1])
        }
        
        for (i in prices.indices) {
            if (i < period) {
                result.add(null)
            } else {
                var gainSum = 0f
                var lossSum = 0f
                
                for (j in i - period until i) {
                    val change = changes[j]
                    if (change > 0) {
                        gainSum += change
                    } else {
                        lossSum += -change
                    }
                }
                
                val avgGain = gainSum / period
                val avgLoss = lossSum / period
                
                val rsi = if (avgLoss == 0f) {
                    100f
                } else {
                    val rs = avgGain / avgLoss
                    100 - (100 / (1 + rs))
                }
                
                result.add(rsi)
            }
        }
        
        return result
    }

    /**
     * 计算所有技术指标
     */
    fun calculateAllIndicators(klines: List<KlineData>): TechnicalIndicators {
        val closes = klines.map { it.close }
        val highs = klines.map { it.high }
        val lows = klines.map { it.low }
        
        return TechnicalIndicators(
            ma5 = calculateMA(closes, 5),
            ma10 = calculateMA(closes, 10),
            ma20 = calculateMA(closes, 20),
            macd = calculateMACD(closes),
            kdj = calculateKDJ(highs, lows, closes),
            rsi = calculateRSI(closes, 14)
        )
    }
}
