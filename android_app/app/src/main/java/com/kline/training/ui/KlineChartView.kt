package com.kline.training.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.kline.training.R
import com.kline.training.data.KlineData
import com.kline.training.data.TechnicalIndicators
import com.kline.training.data.TradeRecord
import kotlin.math.max
import kotlin.math.min

/**
 * K线图表自定义视图
 */
class KlineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 数据
    private var klines: List<KlineData> = emptyList()
    private var indicators: TechnicalIndicators? = null
    private var tradeRecords: List<TradeRecord> = emptyList()
    
    // 显示范围
    private var visibleStart = 0
    private var visibleCount = 60
    
    // 区域划分
    private var mainChartHeight = 0f
    private var volumeHeight = 0f
    private var indicatorHeight = 0f
    
    // 画笔
    private val positivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val negativePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ma5Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ma10Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ma20Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val difPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val deaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val macdPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tradeMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // 缩放和拖动
    private var candleWidth = 10f
    private var candleSpace = 2f
    private var isDragging = false
    private var lastX = 0f
    
    // 指标开关
    var showMA = true
    var showVolume = true
    var showMACD = true
    var showKDJ = false
    var showRSI = false
    
    init {
        initPaints()
    }
    
    private fun initPaints() {
        // 阳线（红色，中国股市习惯）
        positivePaint.color = context.getColor(R.color.red_500)
        positivePaint.style = Paint.Style.FILL
        
        // 阴线（绿色）
        negativePaint.color = context.getColor(R.color.green_500)
        negativePaint.style = Paint.Style.FILL
        
        // 网格
        gridPaint.color = Color.parseColor("#30FFFFFF")
        gridPaint.strokeWidth = 1f
        gridPaint.style = Paint.Style.STROKE
        
        // 文字
        textPaint.color = Color.WHITE
        textPaint.textSize = 24f
        
        // MA均线
        ma5Paint.color = context.getColor(R.color.ma5)
        ma5Paint.style = Paint.Style.STROKE
        ma5Paint.strokeWidth = 1.5f
        
        ma10Paint.color = context.getColor(R.color.ma10)
        ma10Paint.style = Paint.Style.STROKE
        ma10Paint.strokeWidth = 1.5f
        
        ma20Paint.color = context.getColor(R.color.ma20)
        ma20Paint.style = Paint.Style.STROKE
        ma20Paint.strokeWidth = 1.5f
        
        // MACD
        difPaint.color = context.getColor(R.color.dif)
        difPaint.style = Paint.Style.STROKE
        difPaint.strokeWidth = 1.5f
        
        deaPaint.color = context.getColor(R.color.dea)
        deaPaint.style = Paint.Style.STROKE
        deaPaint.strokeWidth = 1.5f
        
        macdPaint.style = Paint.Style.FILL
        
        // 交易标记
        tradeMarkPaint.style = Paint.Style.FILL
    }
    
    fun setData(klines: List<KlineData>, indicators: TechnicalIndicators?) {
        this.klines = klines
        this.indicators = indicators
        visibleStart = 0
        invalidate()
    }
    
    fun setTradeRecords(records: List<TradeRecord>) {
        this.tradeRecords = records
        invalidate()
    }
    
    fun setVisibleRange(start: Int, count: Int = visibleCount) {
        visibleStart = max(0, min(start, klines.size - count))
        visibleCount = count
        invalidate()
    }
    
    fun nextCandle(): Boolean {
        if (visibleStart + visibleCount < klines.size) {
            visibleStart++
            invalidate()
            return true
        }
        return false
    }
    
    fun jumpTo(index: Int) {
        visibleStart = max(0, min(index - visibleCount / 2, klines.size - visibleCount))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (klines.isEmpty()) return
        
        calculateHeights()
        drawGrid(canvas)
        drawMainChart(canvas)
        if (showVolume) {
            drawVolumeChart(canvas)
        }
        if (showMACD) {
            drawMACDChart(canvas)
        }
        drawPriceLabels(canvas)
        drawTradeMarks(canvas)
    }
    
    private fun calculateHeights() {
        val totalHeight = height.toFloat()
        val indicatorCount = listOf(showVolume, showMACD, showKDJ, showRSI).count { it }
        
        if (indicatorCount > 0) {
            mainChartHeight = totalHeight * 0.5f
            val indicatorTotal = totalHeight - mainChartHeight
            volumeHeight = if (showVolume) indicatorTotal / indicatorCount else 0f
            indicatorHeight = if (showMACD || showKDJ || showRSI) indicatorTotal / indicatorCount else 0f
        } else {
            mainChartHeight = totalHeight
            volumeHeight = 0f
            indicatorHeight = 0f
        }
    }
    
    private fun drawGrid(canvas: Canvas) {
        // 水平网格线
        val horizontalLines = 4
        for (i in 0..horizontalLines) {
            val y = mainChartHeight * i / horizontalLines
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }
        
        // 垂直网格线
        val verticalLines = 6
        for (i in 0..verticalLines) {
            val x = width.toFloat() * i / verticalLines
            canvas.drawLine(x, 0f, x, mainChartHeight, gridPaint)
        }
    }
    
    private fun drawMainChart(canvas: Canvas) {
        val visibleEnd = min(visibleStart + visibleCount, klines.size)
        val visibleKlines = klines.subList(visibleStart, visibleEnd)
        
        if (visibleKlines.isEmpty()) return
        
        // 计算价格范围
        var minPrice = Float.MAX_VALUE
        var maxPrice = Float.MIN_VALUE
        for (kline in visibleKlines) {
            minPrice = min(minPrice, kline.low)
            maxPrice = max(maxPrice, kline.high)
        }
        
        // 增加一点边距
        val priceRange = maxPrice - minPrice
        minPrice -= priceRange * 0.05f
        maxPrice += priceRange * 0.05f
        
        val priceScale = mainChartHeight / (maxPrice - minPrice)
        
        // 计算K线宽度
        val totalWidth = width.toFloat()
        candleWidth = totalWidth / visibleCount * 0.7f
        candleSpace = totalWidth / visibleCount * 0.3f
        
        // 绘制K线
        for ((i, kline) in visibleKlines.withIndex()) {
            val x = i * (candleWidth + candleSpace) + candleSpace / 2
            
            // 影线
            val highY = mainChartHeight - (kline.high - minPrice) * priceScale
            val lowY = mainChartHeight - (kline.low - minPrice) * priceScale
            
            val paint = if (kline.isPositive) positivePaint else negativePaint
            canvas.drawLine(x + candleWidth / 2, highY, x + candleWidth / 2, lowY, paint)
            
            // 实体
            val openY = mainChartHeight - (kline.open - minPrice) * priceScale
            val closeY = mainChartHeight - (kline.close - minPrice) * priceScale
            
            val topY = min(openY, closeY)
            val bottomY = max(openY, closeY)
            
            canvas.drawRect(x, topY, x + candleWidth, bottomY, paint)
        }
        
        // 绘制均线
        if (showMA && indicators != null) {
            drawMA(canvas, indicators!!.ma5, ma5Paint, minPrice, priceScale, visibleStart, visibleEnd)
            drawMA(canvas, indicators!!.ma10, ma10Paint, minPrice, priceScale, visibleStart, visibleEnd)
            drawMA(canvas, indicators!!.ma20, ma20Paint, minPrice, priceScale, visibleStart, visibleEnd)
        }
    }
    
    private fun drawMA(
        canvas: Canvas,
        ma: List<Float?>,
        paint: Paint,
        minPrice: Float,
        priceScale: Float,
        start: Int,
        end: Int
    ) {
        var firstPoint = true
        var lastX = 0f
        var lastY = 0f
        
        for (i in start until end) {
            val value = ma.getOrNull(i) ?: continue
            val x = (i - start) * (candleWidth + candleSpace) + candleWidth / 2
            val y = mainChartHeight - (value - minPrice) * priceScale
            
            if (firstPoint) {
                firstPoint = false
            } else {
                canvas.drawLine(lastX, lastY, x, y, paint)
            }
            lastX = x
            lastY = y
        }
    }
    
    private fun drawVolumeChart(canvas: Canvas) {
        val visibleEnd = min(visibleStart + visibleCount, klines.size)
        val visibleKlines = klines.subList(visibleStart, visibleEnd)
        
        if (visibleKlines.isEmpty()) return
        
        val topY = mainChartHeight
        val bottomY = topY + volumeHeight
        
        // 最大成交量
        val maxVolume = visibleKlines.maxOfOrNull { it.volume } ?: 1L
        val volumeScale = volumeHeight / maxVolume.toFloat()
        
        for ((i, kline) in visibleKlines.withIndex()) {
            val x = i * (candleWidth + candleSpace) + candleSpace / 2
            val volumeHeight = kline.volume * volumeScale
            
            val paint = if (kline.isPositive) positivePaint else negativePaint
            paint.alpha = 128
            
            canvas.drawRect(x, bottomY - volumeHeight, x + candleWidth, bottomY, paint)
            paint.alpha = 255
        }
    }
    
    private fun drawMACDChart(canvas: Canvas) {
        val visibleEnd = min(visibleStart + visibleCount, klines.size)
        
        if (indicators == null) return
        
        val macdList = indicators!!.macd.subList(visibleStart, min(visibleEnd, indicators!!.macd.size))
        
        if (macdList.isEmpty()) return
        
        val topY = mainChartHeight + if (showVolume) volumeHeight else 0f
        val bottomY = topY + indicatorHeight
        val centerY = (topY + bottomY) / 2
        
        // 计算MACD范围
        var maxMACD = Float.MIN_VALUE
        var minMACD = Float.MAX_VALUE
        for (macd in macdList) {
            maxMACD = max(maxMACD, macd.dif, macd.dea, macd.macd)
            minMACD = min(minMACD, macd.dif, macd.dea, macd.macd)
        }
        
        val range = max(maxMACD, -minMACD)
        val scale = indicatorHeight / 2 / range
        
        // 绘制MACD柱状图
        for ((i, macd) in macdList.withIndex()) {
            val x = i * (candleWidth + candleSpace) + candleSpace / 2
            val h = macd.macd * scale
            
            macdPaint.color = if (macd.macd >= 0) {
                context.getColor(R.color.red_500)
            } else {
                context.getColor(R.color.green_500)
            }
            macdPaint.alpha = 180
            
            if (h >= 0) {
                canvas.drawRect(x, centerY - h, x + candleWidth, centerY, macdPaint)
            } else {
                canvas.drawRect(x, centerY, x + candleWidth, centerY - h, macdPaint)
            }
        }
        
        // 绘制DIF和DEA线
        var firstPoint = true
        var lastXDif = 0f
        var lastYDif = 0f
        var lastXDea = 0f
        var lastYDea = 0f
        
        for ((i, macd) in macdList.withIndex()) {
            val x = i * (candleWidth + candleSpace) + candleWidth / 2
            val yDif = centerY - macd.dif * scale
            val yDea = centerY - macd.dea * scale
            
            if (firstPoint) {
                firstPoint = false
            } else {
                canvas.drawLine(lastXDif, lastYDif, x, yDif, difPaint)
                canvas.drawLine(lastXDea, lastYDea, x, yDea, deaPaint)
            }
            lastXDif = x
            lastYDif = yDif
            lastXDea = x
            lastYDea = yDea
        }
    }
    
    private fun drawPriceLabels(canvas: Canvas) {
        val visibleEnd = min(visibleStart + visibleCount, klines.size)
        val visibleKlines = klines.subList(visibleStart, visibleEnd)
        
        if (visibleKlines.isEmpty()) return
        
        var minPrice = Float.MAX_VALUE
        var maxPrice = Float.MIN_VALUE
        for (kline in visibleKlines) {
            minPrice = min(minPrice, kline.low)
            maxPrice = max(maxPrice, kline.high)
        }
        
        val priceRange = maxPrice - minPrice
        minPrice -= priceRange * 0.05f
        maxPrice += priceRange * 0.05f
        
        val priceScale = mainChartHeight / (maxPrice - minPrice)
        
        // 绘制价格标签
        val labelCount = 5
        for (i in 0 until labelCount) {
            val price = minPrice + (maxPrice - minPrice) * i / (labelCount - 1)
            val y = mainChartHeight - (price - minPrice) * priceScale
            val text = String.format("%.2f", price)
            canvas.drawText(text, 10f, y, textPaint)
        }
    }
    
    private fun drawTradeMarks(canvas: Canvas) {
        val visibleEnd = min(visibleStart + visibleCount, klines.size)
        val visibleKlines = klines.subList(visibleStart, visibleEnd)
        
        if (visibleKlines.isEmpty()) return
        
        var minPrice = Float.MAX_VALUE
        var maxPrice = Float.MIN_VALUE
        for (kline in visibleKlines) {
            minPrice = min(minPrice, kline.low)
            maxPrice = max(maxPrice, kline.high)
        }
        
        val priceRange = maxPrice - minPrice
        minPrice -= priceRange * 0.05f
        maxPrice += priceRange * 0.05f
        
        val priceScale = mainChartHeight / (maxPrice - minPrice)
        
        for (trade in tradeRecords) {
            val index = trade.klineIndex - visibleStart
            if (index < 0 || index >= visibleCount) continue
            
            val x = index * (candleWidth + candleSpace) + candleWidth / 2
            val y = mainChartHeight - (trade.price - minPrice) * priceScale
            
            if (trade.type == com.kline.training.data.TradeType.BUY) {
                // 买入标记：红色向上箭头
                tradeMarkPaint.color = context.getColor(R.color.red_500)
                drawArrow(canvas, x, y + 20, true)
            } else {
                // 卖出标记：绿色向下箭头
                tradeMarkPaint.color = context.getColor(R.color.green_500)
                drawArrow(canvas, x, y - 20, false)
            }
        }
    }
    
    private fun drawArrow(canvas: Canvas, x: Float, y: Float, isUp: Boolean) {
        val size = 15f
        val direction = if (isUp) -1 else 1
        
        val path = android.graphics.Path()
        path.moveTo(x, y + size * direction)
        path.lineTo(x - size / 2, y)
        path.lineTo(x + size / 2, y)
        path.close()
        
        canvas.drawPath(path, tradeMarkPaint)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                lastX = event.x
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val deltaX = event.x - lastX
                    val moveCount = (deltaX / (candleWidth + candleSpace)).toInt()
                    if (moveCount != 0) {
                        visibleStart = max(0, min(visibleStart - moveCount, klines.size - visibleCount))
                        lastX = event.x
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return true
    }
}
