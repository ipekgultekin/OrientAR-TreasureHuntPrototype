package com.example.orientar

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.Chronometer
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode          // ✅ AR anchor düğümü
import io.github.sceneview.loaders.ModelLoader         // ✅ doğru ModelLoader
import io.github.sceneview.node.ModelNode              // ✅ glb’yi tutan düğüm
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
    private val targetName = "batur"      // assets/augmented_images/batur.jpg
    private var modelPlaced = false
    private val tapQueue = java.util.concurrent.ConcurrentLinkedQueue<android.view.MotionEvent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arSceneView     = findViewById(R.id.sceneView)
        // Kullanıcı dokununca olayı sıraya koy
        arSceneView.setOnTouchListener { _, e ->
            if (e.action == android.view.MotionEvent.ACTION_UP) {
                tapQueue.add(android.view.MotionEvent.obtain(e))
            }
            true
        }
        chronoTimer     = findViewById(R.id.chronoTimer)
        tvQuestionTitle = findViewById(R.id.tvQuestionTitle)
        tvQuestionText  = findViewById(R.id.tvQuestionText)
        btnClose        = findViewById(R.id.btnClose)
        btnNext         = findViewById(R.id.btnNext)

        // 🔧 ModelLoader kurucusu: (engine, context, [opsiyonel coroutineScope])
        modelLoader = ModelLoader(
            engine = arSceneView.engine,
            context = this,
            coroutineScope = CoroutineScope(Dispatchers.IO)
        )

        tvQuestionTitle.text = "Question 1"
        tvQuestionText.text  = "AR image tracking testi: batur.jpg"
        btnClose.setOnClickListener { finish() }
        btnNext.setOnClickListener  { Toast.makeText(this, "Next TODO", Toast.LENGTH_SHORT).show() }

        chronoTimer.base = SystemClock.elapsedRealtime()
        chronoTimer.start()

        ensureCameraPermission()

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

            val imageDb = AugmentedImageDatabase(session).apply {
                assets.open("augmented_images/batur.jpg").use { ins ->
                    val bmp = BitmapFactory.decodeStream(ins)
                    addImage(targetName, bmp)
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

        // 2) Her framedeki image update’leri
        arSceneView.onSessionUpdated = { _, frame ->
            if (!modelPlaced) {                         // ✅ label return yok
                val images = frame.getUpdatedTrackables(AugmentedImage::class.java)
                for (img in images) {
                    if (img.trackingState == TrackingState.TRACKING && img.name == targetName) {
                        placeModelOnImage(img)
                        modelPlaced = true
                        break
                    }
                }
            }
            val tap = tapQueue.poll()
            if (tap != null) {
                val sel = pickBestHit(frame, tap, arSceneView.width, arSceneView.height)
                if (sel != null) {
                    placeModelAtAnchor(sel.anchor) // Aşağıdaki fonksiyon
                }
            }
        }

        // 3) Hata
        arSceneView.onSessionFailed = { e ->
            Toast.makeText(this, "AR hata: ${e.message ?: "NULL"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun placeModelOnImage(image: AugmentedImage) {
        val anchor = image.createAnchor(image.centerPose)

        // GLB -> ModelInstance
        val modelInstance = modelLoader.createModelInstance(
            assetFileLocation = "file:///android_asset/3d_models/Backpack.glb"
        )

        // Model düğümü
        val modelNode = ModelNode(
            modelInstance = modelInstance,
            scaleToUnits = 0.35f
        )

        // Anchor tutan AR düğümü (ebeveyn)
        val anchorNode = AnchorNode(engine = arSceneView.engine, anchor = anchor)
        anchorNode.addChildNode(modelNode)

        // Sahneye ekle
        arSceneView.addChildNode(anchorNode)

        Toast.makeText(this, getString(R.string.model_loaded), Toast.LENGTH_SHORT).show()
    }

    private fun placeModelAtAnchor(anchor: com.google.ar.core.Anchor) {
        val modelInstance = modelLoader.createModelInstance(
            assetFileLocation = "file:///android_asset/3d_models/Backpack.glb"
        )
        val modelNode = ModelNode(
            modelInstance = modelInstance,
            scaleToUnits = 0.35f
        )
        val anchorNode = AnchorNode(engine = arSceneView.engine, anchor = anchor)
        anchorNode.addChildNode(modelNode)
        arSceneView.addChildNode(anchorNode)
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

    // --- Session konfigürasyonu (kullansan da olur kullanmasan da mevcut ayarlarınıza uyumlu) ---
    private fun configureSession(session: com.google.ar.core.Session) {
        val cfg = com.google.ar.core.Config(session).apply {
            focusMode = com.google.ar.core.Config.FocusMode.AUTO
            lightEstimationMode = com.google.ar.core.Config.LightEstimationMode.ENVIRONMENTAL_HDR
            planeFindingMode = com.google.ar.core.Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            instantPlacementMode = com.google.ar.core.Config.InstantPlacementMode.LOCAL_Y_UP
            if (session.isDepthModeSupported(com.google.ar.core.Config.DepthMode.AUTOMATIC)) {
                depthMode = com.google.ar.core.Config.DepthMode.AUTOMATIC
            }
        }
        session.configure(cfg)
    }

    // --- Ekrana dokunma veya ekran merkezinden en iyi vurumu (hit) seç ---
    private data class HitResultSelection(
        val anchor: com.google.ar.core.Anchor,
        val trackable: com.google.ar.core.Trackable
    )

    private fun pickBestHit(
        frame: com.google.ar.core.Frame,
        motionEvent: android.view.MotionEvent?,
        viewWidth: Int,
        viewHeight: Int
    ): HitResultSelection? {
        val camera = frame.camera
        if (camera.trackingState != com.google.ar.core.TrackingState.TRACKING) return null

        val touchHits = motionEvent?.let { frame.hitTest(it) } ?: emptyList()
        val centerHits = if (touchHits.isEmpty()) frame.hitTest(viewWidth / 2f, viewHeight / 2f) else emptyList()
        val candidates = touchHits + centerHits

        for (hit in candidates) {
            when (val t = hit.trackable) {
                is com.google.ar.core.Plane ->
                    if (t.isPoseInPolygon(hit.hitPose)) return HitResultSelection(hit.createAnchor(), t)
                is com.google.ar.core.Point ->
                    if (t.orientationMode == com.google.ar.core.Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
                        return HitResultSelection(hit.createAnchor(), t)
                is com.google.ar.core.InstantPlacementPoint ->
                    return HitResultSelection(hit.createAnchor(), t)
            }
        }

        val (rx, ry) = motionEvent?.let { it.x to it.y } ?: (viewWidth / 2f to viewHeight / 2f)
        val instant = frame.hitTestInstantPlacement(rx, ry, 1.0f)
        if (instant.isNotEmpty()) {
            val h = instant.first()
            return HitResultSelection(h.createAnchor(), h.trackable)
        }
        return null
    }
}
