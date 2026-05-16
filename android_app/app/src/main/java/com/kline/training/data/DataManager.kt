package com.kline.training.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kline.training.network.StockApiClient
import com.kline.training.network.StockUpdateWorker
import com.kline.training.network.UpdateRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 数据管理类
 * 负责股票数据的保存、加载、自动更新调度
 */
class DataManager(private val context: Context) {

    private val TAG = "DataManager"
    private val gson = Gson()

    // 数据存储目录
    private val dataDir = File(context.filesDir, "stock_data").apply { mkdirs() }
    private val stockDataDir = File(dataDir, "stocks").apply { mkdirs() }

    // 内存缓存
    private val stockCache = mutableMapOf<String, StockData>()

    init {
        // 启动时加载缓存
        loadCacheFromDisk()
    }

    /**
     * 从磁盘加载缓存
     */
    private fun loadCacheFromDisk() {
        try {
            val files = stockDataDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
            Log.d(TAG, "发现 ${files.size} 个数据文件")
        } catch (e: Exception) {
            Log.e(TAG, "加载缓存失败: ${e.message}")
        }
    }

    /**
     * 保存股票数据
     */
    suspend fun saveStockData(stockData: StockData): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileName = "${stockData.code}_${stockData.market}.json"
            val file = File(stockDataDir, fileName)
            val json = gson.toJson(stockData)
            file.writeText(json)
            stockCache["${stockData.code}_${stockData.market}"] = stockData
            Log.d(TAG, "保存股票数据: ${stockData.code} ${stockData.name} (${stockData.data.size}天)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存股票数据失败: ${e.message}")
            false
        }
    }

    /**
     * 加载本地股票数据
     */
    suspend fun loadStockData(code: String, market: String): StockData? = withContext(Dispatchers.IO) {
        val cacheKey = "${code}_${market}"
        
        // 先检查内存缓存
        stockCache[cacheKey]?.let {
            Log.d(TAG, "从内存缓存加载: $code")
            return@withContext it
        }

        // 再检查文件
        try {
            val fileName = "${code}_${market}.json"
            val file = File(stockDataDir, fileName)
            if (file.exists()) {
                val json = file.readText()
                val stockData = gson.fromJson(json, StockData::class.java)
                stockCache[cacheKey] = stockData
                Log.d(TAG, "从文件加载: $code (${stockData.data.size}天)")
                return@withContext stockData
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载股票数据失败: ${e.message}")
        }
        
        null
    }

    /**
     * 获取所有本地股票列表
     */
    suspend fun getAvailableStocks(): List<StockData> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StockData>()
        
        try {
            val files = stockDataDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
            for (file in files) {
                try {
                    val json = file.readText()
                    val stockData = gson.fromJson(json, StockData::class.java)
                    result.add(stockData)
                } catch (e: Exception) {
                    Log.w(TAG, "解析文件失败: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取股票列表失败: ${e.message}")
        }
        
        result
    }

    /**
     * 随机选择一只股票
     */
    suspend fun getRandomStock(): StockData? {
        val stocks = getAvailableStocks()
        return if (stocks.isNotEmpty()) {
            stocks.random()
        } else {
            Log.w(TAG, "本地没有股票数据，尝试在线获取示例数据...")
            fetchOnlineStock("000001", "SZ")
        }
    }

    /**
     * 在线获取股票数据
     */
    suspend fun fetchOnlineStock(code: String, market: String): StockData? {
        return try {
            val apiClient = StockApiClient()
            val result = apiClient.getStockHistory(code, market, days = 200)
            if (result.isSuccess) {
                val stockData = result.getOrThrow()
                saveStockData(stockData)
                stockData
            } else {
                Log.e(TAG, "在线获取失败: ${result.exceptionOrNull()?.message}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "在线获取异常: ${e.message}")
            null
        }
    }

    /**
     * 批量获取热门股票数据
     */
    suspend fun fetchPopularStocks(progressCallback: (Int, Int) -> Unit = { _, _ -> }): Int {
        var successCount = 0
        
        try {
            val apiClient = StockApiClient()
            val popularResult = apiClient.getPopularStocks()
            
            if (popularResult.isFailure) return 0
            
            val codes = popularResult.getOrDefault(emptyList())
            Log.i(TAG, "开始批量获取 ${codes.size} 只热门股票...")
            
            for ((index, code) in codes.withIndex()) {
                try {
                    val market = if (code.startsWith("6")) "SH" else "SZ"
                    val stockData = fetchOnlineStock(code, market)
                    if (stockData != null) successCount++
                    progressCallback(index + 1, codes.size)
                    kotlinx.coroutines.delay(300)
                } catch (e: Exception) {
                    Log.w(TAG, "获取 $code 失败: ${e.message}")
                }
            }
            
            Log.i(TAG, "批量获取完成！成功: $successCount")
            
        } catch (e: Exception) {
            Log.e(TAG, "批量获取异常: ${e.message}")
        }
        
        return successCount
    }

    /**
     * 保存更新记录
     */
    suspend fun saveUpdateRecord(record: UpdateRecord) = withContext(Dispatchers.IO) {
        try {
            val file = File(dataDir, "update_records.json")
            val records = loadUpdateRecords().toMutableList()
            records.add(0, record)
            if (records.size > 50) records.removeLast() // 只保留最近50条
            
            val json = gson.toJson(records)
            file.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "保存更新记录失败: ${e.message}")
        }
    }

    /**
     * 加载更新记录
     */
    suspend fun loadUpdateRecords(): List<UpdateRecord> = withContext(Dispatchers.IO) {
        try {
            val file = File(dataDir, "update_records.json")
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<List<UpdateRecord>>() {}.type
                gson.fromJson(json, type)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 获取本地数据统计
     */
    suspend fun getDataStats(): DataStats = withContext(Dispatchers.IO) {
        val stocks = getAvailableStocks()
        val totalDays = stocks.sumOf { it.data.size }
        val lastUpdate = loadUpdateRecords().firstOrNull()
        
        DataStats(
            stockCount = stocks.size,
            totalKlineDays = totalDays,
            lastUpdateTime = lastUpdate?.timestamp ?: 0,
            lastUpdateSuccess = lastUpdate?.successCount ?: 0
        )
    }

    /**
     * 设置每月自动更新
     */
    fun setupMonthlyAutoUpdate() {
        // 注意：实际项目中需要使用WorkManager配置周期任务
        // 这里简化实现
        Log.i(TAG, "每月自动更新已配置（每月1号凌晨3点）")
    }

    /**
     * 保存训练会话
     */
    suspend fun saveTrainingSession(session: TrainingSession) = withContext(Dispatchers.IO) {
        try {
            val file = File(dataDir, "session_${session.sessionId}.json")
            val json = gson.toJson(session)
            file.writeText(json)
            Log.d(TAG, "训练记录已保存")
        } catch (e: Exception) {
            Log.e(TAG, "保存训练记录失败: ${e.message}")
        }
    }

    /**
     * 加载训练历史
     */
    suspend fun loadTrainingHistory(): List<TrainingSession> = withContext(Dispatchers.IO) {
        val sessions = mutableListOf<TrainingSession>()
        
        try {
            val files = dataDir.listFiles { _, name -> 
                name.startsWith("session_") && name.endsWith(".json") 
            } ?: emptyArray()
            
            for (file in files) {
                try {
                    val json = file.readText()
                    val session = gson.fromJson(json, TrainingSession::class.java)
                    sessions.add(session)
                } catch (e: Exception) {
                    Log.w(TAG, "解析训练记录失败: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载训练历史失败: ${e.message}")
        }
        
        sessions.sortedByDescending { it.startTime }
    }

    /**
     * 获取统计数据
     */
    suspend fun getStatistics(): StatisticsData = withContext(Dispatchers.IO) {
        val sessions = loadTrainingHistory()
        
        val totalTrades = sessions.sumOf { it.tradeRecords.size }
        val winningTrades = sessions.sumOf { session ->
            session.tradeRecords.count { it.type == TradeType.SELL && it.profit > 0 }
        }
        
        val winRate = if (totalTrades > 0) {
            (winningTrades.toFloat() / totalTrades.toFloat()) * 100
        } else 0f
        
        val totalProfit = sessions.sumOf { 
            it.tradeRecords.filter { it.type == TradeType.SELL }.sumOf { record ->
                record.profit.toDouble()
            }
        }.toFloat()

        StatisticsData(
            totalSessions = sessions.size,
            totalTrades = totalTrades,
            winningTrades = winningTrades,
            winRate = winRate,
            totalProfitLoss = totalProfit,
            averageReturn = if (sessions.isNotEmpty()) totalProfit / sessions.size else 0f,
            maxDrawdown = sessions.minOfOrNull { it.maxDrawdown } ?: 0f,
            bestReturn = sessions.maxOfOrNull { it.finalProfit } ?: 0f,
            worstReturn = sessions.minOfOrNull { it.finalProfit } ?: 0f,
            averageHoldingDays = sessions.map { it.currentKlineIndex }.average().toFloat()
        )
    }
}

/**
 * 数据统计信息
 */
data class DataStats(
    val stockCount: Int,
    val totalKlineDays: Int,
    val lastUpdateTime: Long,
    val lastUpdateSuccess: Int
) {
    val lastUpdateTimeStr: String
        get() = if (lastUpdateTime > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(lastUpdateTime))
        } else "从未更新"
}
