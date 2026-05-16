package com.kline.training.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kline.training.KlineTrainingApp
import com.kline.training.R
import com.kline.training.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面Activity
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        updateStats()
    }

    private fun initViews() {
        binding.btnStartTraining.setOnClickListener {
            val intent = Intent(this, TrainingActivity::class.java)
            startActivity(intent)
        }

        binding.btnViewStatistics.setOnClickListener {
            val intent = Intent(this, StatisticsActivity::class.java)
            startActivity(intent)
        }

        binding.btnDataManage.setOnClickListener {
            val intent = Intent(this, DataManageActivity::class.java)
            startActivity(intent)
        }
    }

    private fun updateStats() {
        scope.launch {
            val dataManager = KlineTrainingApp.instance.dataManager
            val stats = withContext(Dispatchers.IO) {
                dataManager.getStatistics()
            }
            val stockCount = withContext(Dispatchers.IO) {
                dataManager.getAvailableStocks().size
            }

            binding.tvDataCount.text = getString(R.string.built_in_data) + ": $stockCount"
            binding.tvTrainingCount.text = "训练次数: ${stats.totalSessions}"
            if (stats.totalSessions > 0) {
                binding.tvWinRate.text = "胜率: %.1f%%".format(stats.winRate)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStats()
    }
}
