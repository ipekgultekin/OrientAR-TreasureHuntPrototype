package com.example.orientar

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import io.github.sceneview.ar.ARSceneView

class TreasureHuntGameActivity : AppCompatActivity() {

    private lateinit var arSceneView: ARSceneView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arSceneView = findViewById(R.id.sceneView)

        val backButton: Button = findViewById(R.id.btnBack)
        backButton.setOnClickListener {
            finish() // Scoreboard'a geri döner
        }
    }
}
