package com.example.orientar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.Chronometer
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.Config
import io.github.sceneview.ar.ARSceneView

class TreasureHuntGameActivity : AppCompatActivity() {

    private lateinit var arSceneView: ARSceneView
    private lateinit var chronoTimer: Chronometer
    private lateinit var tvQuestionTitle: TextView
    private lateinit var tvQuestionText: TextView
    private lateinit var btnClose: Button
    private lateinit var btnNext: Button

    private val CAMERA_REQ = 44

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This must be the AR layout (with ARSceneView@sceneView, Chronometer@chronoTimer, etc.)
        setContentView(R.layout.activity_main)

        // View refs
        arSceneView     = findViewById(R.id.sceneView)
        chronoTimer     = findViewById(R.id.chronoTimer)
        tvQuestionTitle = findViewById(R.id.tvQuestionTitle)
        tvQuestionText  = findViewById(R.id.tvQuestionText)
        btnClose        = findViewById(R.id.btnClose)
        btnNext         = findViewById(R.id.btnNext)

        tvQuestionTitle.text = "Question 1"
        tvQuestionText.text  = "AR kamera AÇILDI mı diye test ediyoruz."

        btnClose.setOnClickListener { finish() }
        btnNext.setOnClickListener  { Toast.makeText(this, "Next TODO", Toast.LENGTH_SHORT).show() }

        // Timer 00:00’dan
        chronoTimer.base = SystemClock.elapsedRealtime()
        chronoTimer.start()

        // 1) Kamera izni
        ensureCameraPermission()

        // 2) ARCore oturumu hazır olunca
        arSceneView.onSessionCreated = { session ->
            // Zemin/duvar plane’lerini görünür yap
            arSceneView.planeRenderer.isVisible = true

            // AR config
            val config = Config(session).apply {
                focusMode = Config.FocusMode.AUTO
                lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                depthMode = Config.DepthMode.AUTOMATIC
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
            }
            session.configure(config)

            Toast.makeText(this, "AR oturumu hazır ✅", Toast.LENGTH_SHORT).show()
        }

        // 3) Her frame’de (şimdilik canlılık testi)
        arSceneView.onSessionUpdated = { _, _ ->
            // Augmented Images + model yerleştirme mantığını burada çalıştıracağız
        }

        // 4) Hata olursa
        arSceneView.onSessionFailed = { exception ->
            Toast.makeText(this, "AR hata: ${exception.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureCameraPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_REQ
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQ && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Kamera izni verildi", Toast.LENGTH_SHORT).show()
        } else if (requestCode == CAMERA_REQ) {
            Toast.makeText(this, "Kamera izni gerekli!", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        chronoTimer.stop()
        arSceneView.destroy()
    }
}
