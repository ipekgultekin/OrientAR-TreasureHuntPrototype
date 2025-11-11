package com.example.orientar

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ScoreboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scoreboard)

        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val tvScores = findViewById<TextView>(R.id.tvScores)
        val btnPlay = findViewById<Button>(R.id.btnPlay)

        // İleride Firestore'dan gelecek; şimdilik sabit
        tvTitle.text = "Treasure Hunt Leaderboard"
        tvScores.text = """
            1. Ayşe - 02:35
            2. Mehmet - 03:10
            3. Sen - 04:20
        """.trimIndent()

        btnPlay.setOnClickListener {
            val intent = Intent(this, TreasureHuntGameActivity::class.java)
            startActivity(intent)
        }
    }
}
