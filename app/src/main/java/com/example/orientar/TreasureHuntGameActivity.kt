package com.example.orientar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.Chronometer
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class TreasureHuntGameActivity : AppCompatActivity() {

    private lateinit var arSceneView: ARSceneView
    private lateinit var chronoTimer: Chronometer
    private lateinit var tvQuestionTitle: TextView
    private lateinit var tvQuestionText: TextView
    private lateinit var btnClose: Button
    private lateinit var btnNext: Button

    private lateinit var modelLoader: ModelLoader

    private val CAMERA_REQ = 44

    // ----- aktif soru / model bilgisi -----
    private lateinit var currentQuestion: Question
    private var targetName: String = ""      // aktif sorunun image adı
    private var modelPath: String = ""       // aktif sorunun model yolu (istersen kullanırsın)

    // ----- run-time flags -----
    private var modelPlaced = false
    private var popupShown = false
    private var questionStartMs: Long = 0L
    private var currentAnchorNode: AnchorNode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // AR oyun layout’un hangisiyse onu kullan
        setContentView(R.layout.activity_main)

        arSceneView     = findViewById(R.id.sceneView)
        chronoTimer     = findViewById(R.id.chronoTimer)
        tvQuestionTitle = findViewById(R.id.tvQuestionTitle)
        tvQuestionText  = findViewById(R.id.tvQuestionText)
        btnClose        = findViewById(R.id.btnClose)
        btnNext         = findViewById(R.id.btnNext)

        modelLoader = ModelLoader(
            engine = arSceneView.engine,
            context = this,
            coroutineScope = CoroutineScope(Dispatchers.IO)
        )

        btnClose.setOnClickListener { finish() }
        btnNext.setOnClickListener {
            Toast.makeText(this, "Use the camera to find the answers!", Toast.LENGTH_SHORT).show()
        }

        ensureCameraPermission()

        // Ekrandaki timer oyunun toplam süresini göstersin
        chronoTimer.base = SystemClock.elapsedRealtime()
        chronoTimer.start()

        // İlk soruyu yükle (ilk çözülmemiş olan)
        val firstQ = GameState.nextUnsolved() ?: GameState.questions.first()
        loadQuestion(firstQ)

        // 1) AR oturumu + Augmented Image DB
        arSceneView.onSessionCreated = { session ->
            arSceneView.planeRenderer.isVisible = true

            val cfg = Config(session).apply {
                focusMode = Config.FocusMode.AUTO
                lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                depthMode = Config.DepthMode.AUTOMATIC
            }

            // Tüm sorulardaki görselleri DB'ye ekle
            val imageDb = AugmentedImageDatabase(session).apply {
                for (q in GameState.questions) {
                    assets.open(q.answerImageAssetPath).use { ins ->
                        val bmp = BitmapFactory.decodeStream(ins)
                        addImage(q.answerImageName, bmp)
                    }
                }
            }
            cfg.augmentedImageDatabase = imageDb

            try {
                session.configure(cfg)
            } catch (_: Exception) {
                val fb = Config(session).apply {
                    focusMode = Config.FocusMode.AUTO
                    lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                    depthMode = Config.DepthMode.DISABLED
                    augmentedImageDatabase = imageDb
                }
                session.configure(fb)
            }

            Toast.makeText(this, getString(R.string.ar_session_ready), Toast.LENGTH_SHORT).show()
        }

        // 2) Her framede: SADECE aktif sorunun görseline tepki ver
        arSceneView.onSessionUpdated = { _, frame ->
            if (!modelPlaced) {
                val images = frame.getUpdatedTrackables(AugmentedImage::class.java)
                for (img in images) {
                    if (img.trackingState == TrackingState.TRACKING &&
                        img.name == targetName
                    ) {
                        // Aktif sorunun görseli bulundu
                        placeModelOnImage(img)
                        showCorrectDialog(currentQuestion.id)
                        modelPlaced = true
                        break
                    }
                }
            }
        }

        // 3) Hata
        arSceneView.onSessionFailed = { e ->
            Toast.makeText(this, "AR error: ${e.message ?: "NULL"}", Toast.LENGTH_LONG).show()
        }
    }

    // Aktif soruyu UI + state'e yükleyen fonksiyon
    private fun loadQuestion(q: Question) {
        currentQuestion = q
        targetName = q.answerImageName
        modelPath = q.modelFilePath

        tvQuestionTitle.text = "Question ${q.id}"
        tvQuestionText.text  = q.text

        // Eski modeli sahneden kaldır
        currentAnchorNode?.let { old ->
            arSceneView.removeChildNode(old)
            old.destroy()
        }
        currentAnchorNode = null

        modelPlaced = false
        popupShown = false
        questionStartMs = SystemClock.elapsedRealtime()
    }

    private fun placeModelOnImage(image: AugmentedImage) {
        try {
            // İstersen GameState üzerinden de gidebilirsin ama şu an iki soru için böyle:
            val modelFile = when (image.name) {
                "batur" -> "3d_models/3d_camera_01.glb"
                "ipek"  -> "3d_models/glasses3d.glb"
                else    -> return   // tanımadığımız isim, boşver
            }

            val anchor = image.createAnchor(image.centerPose)

            val modelInstance = modelLoader.createModelInstance(
                assetFileLocation = modelFile      // assets/3d_models/...
            )

            val modelNode = ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = 0.08f              // burada boyut ayarı
            )

            // Önce eski modeli sahneden kaldır
            currentAnchorNode?.let { old ->
                arSceneView.removeChildNode(old)
                old.destroy()
            }

            val anchorNode = AnchorNode(engine = arSceneView.engine, anchor = anchor)
            anchorNode.addChildNode(modelNode)
            arSceneView.addChildNode(anchorNode)

            // Şimdiki modeli sakla
            currentAnchorNode = anchorNode

            Toast.makeText(this, getString(R.string.model_loaded), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Model load error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun showCorrectDialog(questionId: Int) {
        if (popupShown) return
        popupShown = true

        val elapsedMs = SystemClock.elapsedRealtime() - questionStartMs
        GameState.markSolved(questionId, elapsedMs)

        val allSolved = GameState.totalSolved == GameState.totalQuestions()

        val msg = if (allSolved) {
            "You answered all questions!\n" +
                    "Solved: ${GameState.totalSolved}/${GameState.totalQuestions()}\n" +
                    "Total time: ${GameState.totalTimeMs / 1000.0} s"
        } else {
            "Correct answer!\nGet ready for the next question ✨"
        }

        AlertDialog.Builder(this)
            .setTitle("Correct!")
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton(
                if (allSolved) "See Scoreboard" else "Next Question"
            ) { d, _ ->
                d.dismiss()
                if (allSolved) {
                    // Tüm sorular bitti → Scoreboard'a git
                    val intent = Intent(this, ScoreboardActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    // Sonraki çözülmemiş soruyu yükle
                    val nextQ = GameState.nextUnsolved()
                    if (nextQ != null) {
                        loadQuestion(nextQ)
                    } else {
                        // güvenlik için
                        val intent = Intent(this, ScoreboardActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                }
            }
            .show()
    }

    private fun ensureCameraPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQ)
        }
    }

    override fun onRequestPermissionsResult(req: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(req, perms, res)
        if (req == CAMERA_REQ && res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, getString(R.string.camera_permission_granted), Toast.LENGTH_SHORT).show()
        } else if (req == CAMERA_REQ) {
            Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        chronoTimer.stop()
        arSceneView.destroy()
    }
}
