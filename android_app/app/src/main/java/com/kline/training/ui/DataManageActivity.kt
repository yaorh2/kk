package com.kline.training.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kline.training.KlineTrainingApp
import com.kline.training.databinding.ActivityDataManageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * 数据管理界面
 * 支持在线获取股票数据、查看数据统计、设置自动更新
 */
class DataManageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataManageBinding
    private val dataManager by lazy { KlineTrainingApp.instance.dataManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataManageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupClickListeners()
        loadDataStats()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        // 一键获取热门股票数据
        binding.btnFetchPopular.setOnClickListener {
            fetchPopularStocks()
        }

        // 单只股票查询
        binding.btnFetchSingle.setOnClickListener {
            val code = binding.etStockCode.text.toString().trim()
            if (code.isNotEmpty()) {
                fetchSingleStock(code)
            } else {
                Toast.makeText(this, "请输入股票代码", Toast.LENGTH_SHORT).show()
            }
        }

        // 设置自动更新
        binding.switchAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                dataManager.setupMonthlyAutoUpdate()
                Toast.makeText(this, "已开启每月自动更新（每月1号）", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "已关闭自动更新", Toast.LENGTH_SHORT).show()
            }
        }

        // 清空数据
        binding.btnClearData.setOnClickListener {
            // 简化实现
            Toast.makeText(this, "功能开发中", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 批量获取热门股票数据
     */
    private fun fetchPopularStocks() {
        lifecycleScope.launch {
            binding.btnFetchPopular.isEnabled = false
            binding.progressBar.visibility = android.view.View.VISIBLE
            binding.tvFetchStatus.visibility = android.view.View.VISIBLE
            binding.tvFetchStatus.text = "正在获取热门股票列表..."

            try {
                val successCount = withContext(Dispatchers.IO) {
                    var success = 0
                    
                    // 使用内置的股票列表获取
                    val popularCodes = listOf(
                        "000001", "000002", "000063", "000333", "000651",
                        "000858", "600036", "600519", "600887", "601318",
                        "002594", "300750", "601398", "600000", "600900"
                    )

                    for ((index, code) in popularCodes.withIndex()) {
                        withContext(Dispatchers.Main) {
                            val progress = ((index + 1).toFloat() / popularCodes.size * 100).toInt()
                            binding.tvFetchStatus.text = "正在获取: $code (${index + 1}/${popularCodes.size})"
                            binding.progressBar.progress = progress
                        }

                        val market = if (code.startsWith("6")) "SH" else "SZ"
                        val result = dataManager.fetchOnlineStock(code, market)
                        if (result != null) success++

                        kotlinx.coroutines.delay(300)
                    }
                    
                    success
                }

                Toast.makeText(this@DataManageActivity, 
                    "获取完成！成功 $successCount 只股票", Toast.LENGTH_LONG).show()
                
                loadDataStats()

            } catch (e: Exception) {
                Toast.makeText(this@DataManageActivity, 
                    "获取失败: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnFetchPopular.isEnabled = true
                binding.progressBar.visibility = android.view.View.GONE
                binding.tvFetchStatus.visibility = android.view.View.GONE
            }
        }
    }

    /**
     * 获取单只股票数据
     */
    private fun fetchSingleStock(code: String) {
        lifecycleScope.launch {
            binding.btnFetchSingle.isEnabled = false
            binding.progressBar.visibility = android.view.View.VISIBLE

            try {
                val market = if (code.startsWith("6")) "SH" else "SZ"
                val result = withContext(Dispatchers.IO) {
                    dataManager.fetchOnlineStock(code, market)
                }

                if (result != null) {
                    Toast.makeText(this@DataManageActivity, 
                        "获取成功！${result.name} (${result.data.size}天数据)", 
                        Toast.LENGTH_LONG).show()
                    loadDataStats()
                } else {
                    Toast.makeText(this@DataManageActivity, 
                        "获取失败，请检查股票代码", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this@DataManageActivity, 
                    "获取失败: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnFetchSingle.isEnabled = true
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }

    /**
     * 加载数据统计
     */
    private fun loadDataStats() {
        lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) {
                dataManager.getDataStats()
            }

            binding.tvStockCount.text = stats.stockCount.toString()
            binding.tvTotalDays.text = stats.totalKlineDays.toString()
            binding.tvLastUpdate.text = stats.lastUpdateTimeStr
        }
    }
}
