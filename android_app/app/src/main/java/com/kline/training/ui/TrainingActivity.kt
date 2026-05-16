package com.kline.training.ui

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.kline.training.KlineTrainingApp
import com.kline.training.R
import com.kline.training.data.*
import com.kline.training.databinding.ActivityTrainingBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 训练界面Activity
 */
class TrainingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrainingBinding
    private val scope = CoroutineScope(Dispatchers.Main)

    private var stockData: StockData? = null
    private var indicators: TechnicalIndicators? = null
    private var session: TrainingSession? = null

    private var currentIndex = 30
    private var position = 0
    private var avgCost = 0f
    private var currentCapital = 100000f
    private val initialCapital = 100000f
    private var peakCapital = 100000f
    private var maxDrawdown = 0f

    private val tradeRecords = mutableListOf<TradeRecord>()
    private var isFastMode = false
    private val fastModeHandler = Handler(Looper.getMainLooper())
    private val fastModeRunnable = object : Runnable {
        override fun run() {
            if (isFastMode && stockData != null) {
                if (!goNext()) {
                    stopFastMode()
                } else {
                    fastModeHandler.postDelayed(this, 200)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        loadRandomStock()
    }

    private fun initViews() {
        binding.btnBack.setOnClickListener {
            showExitDialog()
        }

        binding.btnNext.setOnClickListener {
            goNext()
        }

        binding.btnBuy.setOnClickListener {
            executeBuy()
        }

        binding.btnSell.setOnClickListener {
            executeSell()
        }

        binding.btnFastMode.setOnClickListener {
            toggleFastMode()
        }

        binding.rgPosition.setOnCheckedChangeListener { _, _ -> }

        updateUI()
    }

    private fun loadRandomStock() {
        scope.launch {
            binding.loadingView.isVisible = true
            binding.klineChart.isVisible = false

            val dataManager = KlineTrainingApp.instance.dataManager
            val stock = withContext(Dispatchers.IO) {
                dataManager.getRandomStock()
            }

            if (stock == null || stock.data.size < 60) {
                Toast.makeText(this@TrainingActivity, "数据不足，请先导入数据", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            stockData = stock
            indicators = IndicatorCalculator.calculateAllIndicators(stock.data)

            // 创建训练会话
            session = TrainingSession(
                sessionId = UUID.randomUUID().toString(),
                stockCode = stock.code,
                stockName = stock.name,
                startTime = System.currentTimeMillis(),
                initialCapital = initialCapital,
                currentCapital = currentCapital
            )

            // 从随机位置开始
            val minStart = 60
            val maxStart = stock.data.size - 60
            currentIndex = if (minStart < maxStart) {
                (minStart..maxStart).random()
            } else {
                minStart
            }

            binding.klineChart.setData(stock.data, indicators)
            binding.klineChart.setVisibleRange(currentIndex - 30, 60)

            binding.loadingView.isVisible = false
            binding.klineChart.isVisible = true

            updateUI()
        }
    }

    private fun goNext(): Boolean {
        if (stockData == null) return false

        if (currentIndex < stockData!!.data.size - 1) {
            currentIndex++
            binding.klineChart.jumpTo(currentIndex)
            updateCapital()
            updateUI()
            return true
        } else {
            finishTraining()
            return false
        }
    }

    private fun executeBuy() {
        if (stockData == null || position >= 100) return

        val currentKline = stockData!!.data[currentIndex]
        val price = currentKline.close

        val positionPercent = when (binding.rgPosition.checkedRadioButtonId) {
            R.id.rbQuarter -> 25
            R.id.rbHalf -> 50
            else -> 100
        }

        val availableCapital = currentCapital * positionPercent / 100
        val quantity = (availableCapital / price / 100).toInt() * 100 // 整手买入

        if (quantity <= 0) {
            Toast.makeText(this, "资金不足", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = quantity * price
        currentCapital -= amount

        // 更新持仓成本
        val totalCost = avgCost * position + price * quantity
        position += quantity
        avgCost = if (position > 0) totalCost / position else 0f

        val trade = TradeRecord(
            date = currentKline.date,
            type = TradeType.BUY,
            price = price,
            quantity = quantity,
            amount = amount,
            klineIndex = currentIndex
        )
        tradeRecords.add(trade)

        binding.klineChart.setTradeRecords(tradeRecords)
        updateUI()
    }

    private fun executeSell() {
        if (stockData == null || position <= 0) return

        val currentKline = stockData!!.data[currentIndex]
        val price = currentKline.close

        val sellPercent = when (binding.rgPosition.checkedRadioButtonId) {
            R.id.rbQuarter -> 25
            R.id.rbHalf -> 50
            else -> 100
        }

        val sellQuantity = position * sellPercent / 100
        if (sellQuantity <= 0) return

        val amount = sellQuantity * price
        currentCapital += amount
        position -= sellQuantity

        // 计算盈亏
        val profit = (price - avgCost) * sellQuantity
        if (profit > 0) {
            session?.winningTrades = (session?.winningTrades ?: 0) + 1
        }

        val trade = TradeRecord(
            date = currentKline.date,
            type = TradeType.SELL,
            price = price,
            quantity = sellQuantity,
            amount = amount,
            klineIndex = currentIndex
        )
        tradeRecords.add(trade)
        session?.totalTrades = (session?.totalTrades ?: 0) + 1

        binding.klineChart.setTradeRecords(tradeRecords)
        updateUI()
    }

    private fun updateCapital() {
        if (stockData == null || position <= 0) return

        val currentKline = stockData!!.data[currentIndex]
        val totalValue = currentCapital + position * currentKline.close

        // 更新峰值和回撤
        if (totalValue > peakCapital) {
            peakCapital = totalValue
        }
        val drawdown = (peakCapital - totalValue) / peakCapital * 100
        if (drawdown < maxDrawdown) {
            maxDrawdown = drawdown
        }
    }

    private fun updateUI() {
        if (stockData == null) return

        val currentKline = stockData!!.data[currentIndex]
        val totalValue = currentCapital + position * currentKline.close
        val profitRate = (totalValue - initialCapital) / initialCapital * 100

        binding.tvCapital.text = "资金: %.0f".format(currentCapital)
        binding.tvPosition.text = "持仓: $position"
        binding.tvAvgCost.text = "成本: %.2f".format(avgCost)
        binding.tvCurrentPrice.text = "现价: %.2f".format(currentKline.close)
        binding.tvTotalValue.text = "总权益: %.0f".format(totalValue)
        binding.tvProfitRate.text = "盈亏: %.1f%%".format(profitRate)
        binding.tvProgress.text = "$currentIndex / ${stockData!!.data.size}"

        // 更新按钮状态
        binding.btnBuy.isEnabled = position < 100
        binding.btnSell.isEnabled = position > 0
    }

    private fun toggleFastMode() {
        isFastMode = !isFastMode
        binding.btnFastMode.text = if (isFastMode) "暂停" else getString(R.string.fast_mode)

        if (isFastMode) {
            fastModeHandler.post(fastModeRunnable)
        } else {
            fastModeHandler.removeCallbacks(fastModeRunnable)
        }
    }

    private fun stopFastMode() {
        isFastMode = false
        binding.btnFastMode.text = getString(R.string.fast_mode)
        fastModeHandler.removeCallbacks(fastModeRunnable)
    }

    private fun showExitDialog() {
        stopFastMode()

        AlertDialog.Builder(this)
            .setTitle("结束训练")
            .setMessage("确定要结束本次训练吗？")
            .setPositiveButton("确定") { _, _ ->
                finishTraining()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun finishTraining() {
        stopFastMode()

        // 平掉所有持仓
        if (position > 0 && stockData != null) {
            val currentKline = stockData!!.data[currentIndex]
            val price = currentKline.close
            val amount = position * price
            currentCapital += amount

            val profit = (price - avgCost) * position
            if (profit > 0) {
                session?.winningTrades = (session?.winningTrades ?: 0) + 1
            }

            val trade = TradeRecord(
                date = currentKline.date,
                type = TradeType.SELL,
                price = price,
                quantity = position,
                amount = amount,
                klineIndex = currentIndex
            )
            tradeRecords.add(trade)
            session?.totalTrades = (session?.totalTrades ?: 0) + 1

            position = 0
        }

        // 保存训练结果
        session?.apply {
            endTime = System.currentTimeMillis()
            currentCapital = this@TrainingActivity.currentCapital
            currentKlineIndex = this@TrainingActivity.currentIndex
            this.tradeRecords.addAll(this@TrainingActivity.tradeRecords)
            this.maxDrawdown = this@TrainingActivity.maxDrawdown
            this.peakCapital = this@TrainingActivity.peakCapital

            scope.launch {
                KlineTrainingApp.instance.dataManager.saveTrainingSession(this@apply)
            }
        }

        // 显示结果
        val totalValue = currentCapital
        val profitRate = (totalValue - initialCapital) / initialCapital * 100

        AlertDialog.Builder(this)
            .setTitle("训练结束")
            .setMessage(
                """
                初始资金: $initialCapital
                最终资金: %.0f
                收益率: %.2f%%
                交易次数: ${session?.totalTrades ?: 0}
                最大回撤: %.2f%%
                """.trimIndent().format(totalValue, profitRate, maxDrawdown)
            )
            .setPositiveButton("确定") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onBackPressed() {
        showExitDialog()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopFastMode()
    }
}
