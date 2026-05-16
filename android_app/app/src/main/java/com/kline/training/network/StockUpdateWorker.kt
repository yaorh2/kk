package com.kline.training.network

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kline.training.KlineTrainingApp

/**
 * 每月自动更新股票数据的后台任务
 */
class StockUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "StockUpdateWorker"

    override suspend fun doWork(): Result {
        Log.i(TAG, "开始自动更新股票数据...")

        return try {
            val dataManager = KlineTrainingApp.instance.dataManager
            val apiClient = StockApiClient()

            // 获取热门股票列表
            val popularResult = apiClient.getPopularStocks()
            if (popularResult.isFailure) {
                Log.e(TAG, "获取热门股票失败: ${popularResult.exceptionOrNull()?.message}")
                return Result.retry()
            }

            val stockCodes = popularResult.getOrDefault(emptyList())
            Log.i(TAG, "待更新股票数量: ${stockCodes.size}")

            var successCount = 0
            var failCount = 0

            // 逐个更新股票数据
            for ((index, code) in stockCodes.withIndex()) {
                try {
                    // 判断市场
                    val market = if (code.startsWith("6")) "SH" else "SZ"
                    
                    Log.i(TAG, "正在更新 ($index/${stockCodes.size}) $code...")
                    
                    val result = apiClient.getStockHistory(code, market, days = 365)
                    
                    if (result.isSuccess) {
                        val stockData = result.getOrThrow()
                        if (stockData.data.isNotEmpty()) {
                            // 保存数据
                            dataManager.saveStockData(stockData)
                            successCount++
                            Log.i(TAG, "✓ $code 更新成功，${stockData.data.size}条数据")
                        } else {
                            failCount++
                            Log.w(TAG, "✗ $code 数据为空")
                        }
                    } else {
                        failCount++
                        Log.w(TAG, "✗ $code 获取失败: ${result.exceptionOrNull()?.message}")
                    }

                    // 避免请求过快
                    kotlinx.coroutines.delay(500)

                } catch (e: Exception) {
                    failCount++
                    Log.e(TAG, "更新 $code 异常: ${e.message}")
                }
            }

            // 保存更新记录
            dataManager.saveUpdateRecord(
                UpdateRecord(
                    timestamp = System.currentTimeMillis(),
                    totalStocks = stockCodes.size,
                    successCount = successCount,
                    failCount = failCount
                )
            )

            Log.i(TAG, "自动更新完成！成功: $successCount, 失败: $failCount")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "自动更新异常: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "StockMonthlyUpdate"
    }
}

/**
 * 更新记录
 */
data class UpdateRecord(
    val timestamp: Long,
    val totalStocks: Int,
    val successCount: Int,
    val failCount: Int
)
