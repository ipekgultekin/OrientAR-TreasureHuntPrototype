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

        val tvTitle: TextView = findViewById(R.id.tvTitle)
        val tvScores: TextView = findViewById(R.id.tvScores)
        val btnPlay: Button = findViewById(R.id.btnPlay)

        tvTitle.text = "Treasure Hunt Leaderboard"

        val solved = GameState.totalSolved
        val total = GameState.totalQuestions()
        val totalSeconds = GameState.totalTimeMs / 1000

        tvScores.text = "Solved: $solved / $total\nTotal time: ${totalSeconds}s"

        btnPlay.setOnClickListener {
            val firstQuestionId = GameState.questions.first().id
            val intent = Intent(this, TreasureHuntGameActivity::class.java)
            intent.putExtra("QUESTION_ID", firstQuestionId)
            startActivity(intent)
        }
    }
}
