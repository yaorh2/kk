package com.kline.training.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kline.training.KlineTrainingApp
import com.kline.training.R
import com.kline.training.data.StatisticsData
import com.kline.training.data.TrainingSession
import com.kline.training.databinding.ActivityStatisticsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * 统计界面Activity
 */
class StatisticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatisticsBinding
    private val scope = CoroutineScope(Dispatchers.Main)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        loadStatistics()
    }

    private fun initViews() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun loadStatistics() {
        scope.launch {
            val dataManager = KlineTrainingApp.instance.dataManager
            
            val stats = withContext(Dispatchers.IO) {
                dataManager.getStatistics()
            }
            
            val history = withContext(Dispatchers.IO) {
                dataManager.loadTrainingHistory()
            }

            updateSummary(stats)
            binding.recyclerView.adapter = HistoryAdapter(history)
        }
    }

    private fun updateSummary(stats: StatisticsData) {
        binding.tvTotalSessions.text = "${stats.totalSessions}"
        binding.tvTotalTrades.text = "${stats.totalTrades}"
        binding.tvWinRate.text = "%.1f%%".format(stats.winRate)
        binding.tvAvgReturn.text = "%.2f%%".format(stats.averageReturn)
        binding.tvMaxDrawdown.text = "%.2f%%".format(stats.maxDrawdown)
        binding.tvBestReturn.text = "%.2f%%".format(stats.bestReturn)
        binding.tvWorstReturn.text = "%.2f%%".format(stats.worstReturn)
    }

    inner class HistoryAdapter(private val sessions: List<TrainingSession>) :
        RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvDate: TextView = view.findViewById(R.id.tvDate)
            val tvStock: TextView = view.findViewById(R.id.tvStock)
            val tvReturn: TextView = view.findViewById(R.id.tvReturn)
            val tvTrades: TextView = view.findViewById(R.id.tvTrades)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val session = sessions[position]
            
            holder.tvDate.text = dateFormat.format(Date(session.startTime))
            holder.tvStock.text = "${session.stockName} (${session.stockCode})"
            
            val returnRate = (session.currentCapital - session.initialCapital) / session.initialCapital * 100
            holder.tvReturn.text = "%.2f%%".format(returnRate)
            holder.tvReturn.setTextColor(
                if (returnRate >= 0) getColor(R.color.red_500) else getColor(R.color.green_500)
            )
            
            holder.tvTrades.text = "${session.totalTrades} 笔交易"
        }

        override fun getItemCount() = sessions.size
    }
}
