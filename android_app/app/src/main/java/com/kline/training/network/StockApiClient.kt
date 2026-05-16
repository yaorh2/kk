package com.kline.training.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kline.training.data.KlineData
import com.kline.training.data.StockData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 股票数据API客户端
 * 内置多种免费数据源，自动切换保证可用性
 */
class StockApiClient {

    private val TAG = "StockApiClient"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * 获取股票历史K线数据
     * @param code 股票代码（如 000001）
     * @param market 市场（SZ-深市, SH-沪市）
     * @param days 获取天数
     */
    suspend fun getStockHistory(
        code: String,
        market: String = "SZ",
        days: Int = 365
    ): Result<StockData> = withContext(Dispatchers.IO) {
        // 尝试多个数据源，确保可用性
        val sources = listOf(
            { fetchFromSina(code, market, days) },
            { fetchFromTencent(code, market, days) },
            { fetchFromEastMoney(code, market, days) }
        )

        for ((index, source) in sources.withIndex()) {
            try {
                Log.d(TAG, "尝试数据源 ${index + 1}...")
                val result = source.invoke()
                if (result.isSuccess) {
                    Log.d(TAG, "数据源 ${index + 1} 获取成功！")
                    return@withContext result
                }
            } catch (e: Exception) {
                Log.w(TAG, "数据源 ${index + 1} 失败: ${e.message}")
            }
        }

        Result.failure(Exception("所有数据源均获取失败，请检查网络连接"))
    }

    /**
     * 数据源1: 新浪财经API
     */
    private suspend fun fetchFromSina(
        code: String,
        market: String,
        days: Int
    ): Result<StockData> {
        val marketCode = if (market == "SH") "sh$code" else "sz$code"
        val url = "https://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData?" +
                "symbol=$marketCode&scale=240&ma=no&datalen=$days"

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return Result.failure(Exception("HTTP ${response.code}"))

            val json = response.body?.string() ?: return Result.failure(Exception("空响应"))
            
            val type = object : TypeToken<List<SinaKlineItem>>() {}.type
            val items: List<SinaKlineItem> = gson.fromJson(json, type)

            if (items.isEmpty()) return Result.failure(Exception("数据为空"))

            val klines = items.map {
                KlineData(
                    date = it.day,
                    open = it.open.toFloatOrNull() ?: 0f,
                    close = it.close.toFloatOrNull() ?: 0f,
                    high = it.high.toFloatOrNull() ?: 0f,
                    low = it.low.toFloatOrNull() ?: 0f,
                    volume = (it.volume.toDoubleOrNull() ?: 0.0).toLong()
                )
            }.reversed()

            val stockName = getStockNameFromSina(code, market) ?: "$code"

            Result.success(StockData(
                code = code,
                name = stockName,
                market = market,
                totalDays = klines.size,
                startDate = klines.firstOrNull()?.date ?: "",
                endDate = klines.lastOrNull()?.date ?: "",
                data = klines
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 数据源2: 腾讯财经API
     */
    private suspend fun fetchFromTencent(
        code: String,
        market: String,
        days: Int
    ): Result<StockData> {
        val marketCode = if (market == "SH") "sh$code" else "sz$code"
        val url = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?_var=kline_dayqfq&param=$marketCode,day,,,$days,qfq"
        
        return try {
            // 简化实现，实际数据格式处理
            Result.failure(Exception("暂不支持"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 数据源3: 东方财富API
     */
    private suspend fun fetchFromEastMoney(code: String, market: String): Result<StockData> {
        return try {
            val secid = if (market == "SH") "1.${code.padStart(6, '0')}" else "0.${code.padStart(6, '0')}"
            val url = "https://push2his.eastmoney.com/api/qt/stock/kline/get?" +
                    "fields1=f1%2Cf2%2Cf3%2Cf4%2Cf5%2Cf6&fields2=f51%2Cf52%2Cf53%2Cf54%2Cf55%2Cf56%2Cf57&" +
                    "ut=fa5fd1943c7b386f172d6893dbfba10b&klt=101&fqt=1&end=20500101&lmt=${minOf(days, 1000)}&secid=$secid"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return Result.failure(Exception("HTTP ${response.code}"))

            val json = response.body?.string() ?: return Result.failure(Exception("空响应"))
            parseEastMoneyData(code, days, json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseEastMoneyData(code: String, days: Int, json: String): Result<StockData> {
        return try {
            // 简单的字符串提取解析（东方财富API格式）
            val pattern = Regex("\"klines\":\\[(.*?)\\]")
            val matchResult = pattern.find(json) ?: return Result.failure(Exception("数据格式错误"))
            
            val klinesJson = matchResult.groupValues[1].replace("\"", "")
            val klineArray = klinesJson.split("\",\"")
            
            val klines = mutableListOf<KlineData>()
            
            for (line in klineArray) {
                val parts = line.split(",")
                if (parts.size >= 6) {
                    klines.add(KlineData(
                        date = parts[0].replace("-", ""),
                        open = parts[1].toFloatOrNull() ?: 0f,
                        close = parts[2].toFloatOrNull() ?: 0f,
                        high = parts[3].toFloatOrNull() ?: 0f,
                        low = parts[4].toFloatOrNull() ?: 0f,
                        volume = parts[5].toDoubleOrNull()?.toLong() ?: 0L
                    ))
                }
            }
            
            Result.success(StockData(
                code = code,
                name = "$days日线",
                market = days.toString(),
                days = klines.size,
                startDate = klines.firstOrNull()?.date ?: "",
                endDate = klines.lastOrNull()?.date ?: "",
                data = klines
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取A股热门股票列表
     */
    suspend fun getPopularStocks(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            // 沪深300成分股代码
            val popularCodes = listOf(
                "000001", "000002", "000063", "000333", "000651",
                "000858", "002415", "002594", "600036", "600519",
                "600887", "601318", "601398", "601857", "601988",
                "000000", "000016", "000069", "000408", "000538",
                "000725", "000786", "000895", "002027", "002142",
                "002230", "002236", "002241", "002252", "002460",
                "002475", "002555", "002607", "002736", "002916",
                "002938", "300059", "300122", "300124", "300142",
                "300274", "300308", "300413", "300433", "300498",
                "300628", "300750", "300760", "600000", "600009"
            )
            Result.success(popularCodes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取股票名称
     */
    private suspend fun getStockNameFromSina(code: String, market: String): String? {
        return try {
            val marketCode = if (market == "SH") "sh$code" else "sz$code"
            val url = "https://hq.sinajs.cn/list=$marketCode"
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) return null
            
            val result = response.body?.string() ?: return null
            val pattern = Regex("\"([^\"].*?)\"")
            val matchResult = pattern.find(result) ?: return null
            
            val values = matchResult.groupValues[1].split(",")
            if (values.size > 0) values[0] else null
        } catch (e: Exception) {
            null
        }
    }

    data class SinaKlineItem(
        val day: String,
        val open: String,
        val high: String,
        val low: String,
        val close: String,
        val volume: String
    )
}
