package com.example.orientar

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.Chronometer
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TreasureHuntGameActivity : AppCompatActivity() {

    private lateinit var chronometer: Chronometer
    private lateinit var tvQuestion: TextView
    private lateinit var btnOpenAr: Button
    private lateinit var btnSkip: Button

    private val questions = listOf(
        "Everyone looks at me while passing by, but no one remembers me.\nMy arrows speak, I only show the way.",
        "I carry knowledge but I am not a student.\nYou visit me to study, rest, and sometimes sleep. Who am I?",
        "You come here hungry and leave happy.\nI serve many, but I never eat. Where am I?"
    )

    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_treasure_hunt_game)

        chronometer = findViewById(R.id.chronometerTimer)
        tvQuestion = findViewById(R.id.tvQuestion)
        btnOpenAr = findViewById(R.id.btnOpenAr)
        btnSkip = findViewById(R.id.btnSkip)

        // Timer 00:00'dan başlasın
        chronometer.base = SystemClock.elapsedRealtime()
        chronometer.start()

        showQuestion()

        btnSkip.setOnClickListener {
            currentIndex = (currentIndex + 1) % questions.size
            showQuestion()
        }

        btnOpenAr.setOnClickListener {
            // Şimdilik sadece AR ekranını açıyoruz (MainActivity bizim AR ekranı)
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showQuestion() {
        tvQuestion.text = questions[currentIndex]
    }
}
