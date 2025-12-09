package com.example.orientar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.widget.ImageButton

class TreasureHuntGameActivity : AppCompatActivity() {

    private lateinit var arSceneView: ARSceneView
    private lateinit var chronoTimer: Chronometer
    private lateinit var tvQuestionTitle: TextView
    private lateinit var tvQuestionText: TextView
    private lateinit var btnClose: ImageButton
    private lateinit var btnNext: Button

    private lateinit var modelLoader: ModelLoader

    private val CAMERA_REQ = 44

    // ----- aktif soru / model bilgisi -----
    private lateinit var currentQuestion: Question
    private var targetName: String = ""     // aktif sorunun image adı

    // ----- run-time flags -----
    private var modelPlaced = false
    private var popupShown = false
    private var questionStartMs: Long = 0L
    private var currentAnchorNode: AnchorNode? = null

    // popup geciktirme için
    private val popupDelayMs = 8000L          // 8 saniye
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingPopupRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_treasure_hunt_game)   // <-- kendi layout’un

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
        btnNext.setOnClickListener  { goToNextQuestion() }

        ensureCameraPermission()

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

        // 2) Frame başına: sadece AKTİF sorunun görseline tepki ver
        arSceneView.onSessionUpdated = { _, frame ->
            if (!modelPlaced) {
                val images = frame.getUpdatedTrackables(AugmentedImage::class.java)
                for (img in images) {
                    if (img.trackingState == TrackingState.TRACKING &&
                        img.name == targetName
                    ) {
                        // Sadece aktif sorunun image adı geldiyse
                        placeModelOnImage(img, currentQuestion)
                        modelPlaced = true
                        scheduleCorrectPopup(currentQuestion.id)   // 8 sn sonra popup
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

        tvQuestionTitle.text = q.title
        tvQuestionText.text  = q.text

        // Kronometreyi sıfırla
        questionStartMs = SystemClock.elapsedRealtime()
        chronoTimer.base = questionStartMs
        chronoTimer.start()

        // Eski popup timer'ı iptal et
        pendingPopupRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingPopupRunnable = null
        popupShown = false

        // Eski modeli komple temizle
        currentAnchorNode?.let { old ->
            arSceneView.removeChildNode(old)
            old.destroy()
        }
        currentAnchorNode = null
        modelPlaced = false
    }

    private fun placeModelOnImage(image: AugmentedImage, question: Question) {
        try {
            val anchor = image.createAnchor(image.centerPose)

            val modelInstance = modelLoader.createModelInstance(question.modelFilePath)

            val modelNode = ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = question.modelScale
            ).apply {
                // Y ekseni etrafında döndür (kamera önüne düz baksın)
                rotation = Rotation(0f, question.modelRotationY, 0f)
            }

            // Eski modeli sil
            currentAnchorNode?.let { old ->
                arSceneView.removeChildNode(old)
                old.destroy()
            }

            val anchorNode = AnchorNode(
                engine = arSceneView.engine,
                anchor = anchor
            )

            anchorNode.addChildNode(modelNode)
            arSceneView.addChildNode(anchorNode)

            currentAnchorNode = anchorNode

        } catch (e: Exception) {
            Toast.makeText(this, "Model load error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    // Popup'u hemen değil, belirli bir gecikmeyle göster
    private fun scheduleCorrectPopup(questionId: Int) {
        if (popupShown) return

        pendingPopupRunnable?.let { mainHandler.removeCallbacks(it) }

        val runnable = Runnable {
            if (!isFinishing) {
                showCorrectDialog(questionId)
            }
        }
        pendingPopupRunnable = runnable
        mainHandler.postDelayed(runnable, popupDelayMs)
    }

    private fun showCorrectDialog(questionId: Int) {
        if (popupShown) return
        popupShown = true

        chronoTimer.stop()

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
                    val intent = Intent(this, ScoreboardActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    val nextQ = GameState.nextUnsolved()
                    if (nextQ != null) {
                        loadQuestion(nextQ)
                    } else {
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
        pendingPopupRunnable?.let { mainHandler.removeCallbacks(it) }
        chronoTimer.stop()
        arSceneView.destroy()
    }

    private fun goToNextQuestion() {
        // Eğer hala popup için bekleyen runnable varsa iptal et
        pendingPopupRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingPopupRunnable = null
        popupShown = false

        // Şu anki sorunun index'ini bul
        val currentIndex = GameState.questions.indexOfFirst { it.id == currentQuestion.id }

        if (currentIndex != -1 && currentIndex < GameState.questions.size - 1) {
            val nextQ = GameState.questions[currentIndex + 1]
            loadQuestion(nextQ)   // model temizleme + chrono reset içeride
        } else {
            Toast.makeText(this, "This is the last question.", Toast.LENGTH_SHORT).show()
        }
    }
}
