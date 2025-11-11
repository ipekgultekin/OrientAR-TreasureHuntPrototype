package com.example.orientar

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ScoreboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scoreboard)

        val playButton: Button = findViewById(R.id.btnPlay)
        playButton.setOnClickListener {
            val intent = Intent(this, TreasureHuntGameActivity::class.java)
            startActivity(intent)
        }
    }
}
