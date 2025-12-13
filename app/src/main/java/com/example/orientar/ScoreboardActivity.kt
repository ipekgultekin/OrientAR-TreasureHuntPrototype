package com.example.orientar

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class ScoreboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scoreboard)

        val rvScores: RecyclerView = findViewById(R.id.rvScores)
        val btnPlay: Button = findViewById(R.id.btnPlay)
        val btnHome: Button = findViewById(R.id.btnHome)
        val tvTotalStats: TextView = findViewById(R.id.tvTotalStats)

        // İstatistikleri Göster
        val solved = GameState.totalSolved
        val total = GameState.totalQuestions()
        val totalSeconds = GameState.totalTimeMs / 1000.0

        tvTotalStats.text = String.format(Locale.US, "SOLVED: %d / %d\nTOTAL TIME: %.1f sec", solved, total, totalSeconds)

        // Listeyi Ayarla
        rvScores.layoutManager = LinearLayoutManager(this)
        rvScores.adapter = ScoreboardAdapter(GameState.questions, GameState.bestTimes)

        // Play Again Butonu
        btnPlay.setOnClickListener {
            // Hiç çözülmemiş varsa ona git, yoksa ilk soruya git
            val nextQ = GameState.nextUnsolved() ?: GameState.questions.first()
            val intent = Intent(this, TreasureHuntGameActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Home Butonu (Main Activity'e dön)
        btnHome.setOnClickListener {
            // Buraya kendi ana sayfa Activity ismini yaz (örn: MainActivity::class.java)
            // val intent = Intent(this, MainActivity::class.java)
            // startActivity(intent)
            finish()
        }
    }

    inner class ScoreboardAdapter(
        private val questions: List<Question>,
        private val scores: Map<Int, Long>
    ) : RecyclerView.Adapter<ScoreboardAdapter.ScoreViewHolder>() {

        inner class ScoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imgTrophy: ImageView = itemView.findViewById(R.id.imgTrophy)
            val tvTitle: TextView = itemView.findViewById(R.id.tvQuestionTitle)
            val tvTime: TextView = itemView.findViewById(R.id.tvTime)
            val imgStatus: ImageView = itemView.findViewById(R.id.imgStatusIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScoreViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_score, parent, false)
            return ScoreViewHolder(view)
        }

        override fun onBindViewHolder(holder: ScoreViewHolder, position: Int) {
            val q = questions[position]
            val timeMs = scores[q.id]
            val isSolved = timeMs != null

            holder.tvTitle.text = q.title

            if (isSolved) {
                val sec = timeMs!! / 1000.0
                holder.tvTime.text = String.format(Locale.US, "Completed in %.1f s", sec)
                // Android'in kendi tik işareti
                holder.imgStatus.setImageResource(android.R.drawable.checkbox_on_background)
                holder.imgStatus.setColorFilter(android.graphics.Color.parseColor("#B71C1C")) // Kırmızı Tik

                // Resim Yükle
                try {
                    val ims = assets.open(q.answerImageAssetPath)
                    val d = android.graphics.drawable.Drawable.createFromStream(ims, null)
                    holder.imgTrophy.setImageDrawable(d)
                } catch (e: Exception) {
                    holder.imgTrophy.setImageResource(android.R.drawable.ic_menu_gallery)
                }

            } else {
                holder.tvTime.text = "Not solved yet"
                holder.imgStatus.setImageResource(android.R.drawable.checkbox_off_background)
                holder.imgStatus.setColorFilter(android.graphics.Color.GRAY)
                holder.imgTrophy.setImageResource(android.R.drawable.ic_lock_lock)
            }
        }

        override fun getItemCount(): Int = questions.size
    }
}