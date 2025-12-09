package com.example.orientar

// Basit soru modeli
data class Question(
    val id: Int,
    val title: String,
    val text: String,
    // Augmented Image ismi (DB'ye eklerken verdiğimiz ad – uzantısız)
    val answerImageName: String,
    // assets içindeki görüntü yolu
    val answerImageAssetPath: String,
    // assets içindeki .glb yolu (ör: "3d_models/Backpack.glb")
    val modelFilePath: String,
    // 3D model ölçeği
    val modelScale: Float,
    // Y ekseni etrafındaki döndürme (derece cinsinden)
    val modelRotationY: Float
)

object GameState {

    // ───── Sorular ─────
    val questions: MutableList<Question> = mutableListOf(
        Question(
            id = 1,
            title = "Question 1",
            text = "Find the Batur image and keep it in view!",
            answerImageName = "batur2",                     // assets/augmented_images/batur2.jpg
            answerImageAssetPath = "augmented_images/batur2.jpg",
            modelFilePath = "3d_models/3d_camera_01.glb",
            modelScale = 0.12f,
            modelRotationY = 180f        // kameraya tam bakmıyorsa bunu değiştirirsin
        ),
        Question(
            id = 2,
            title = "Question 2",
            text = "Find İpek and unlock the glasses!",
            answerImageName = "ipek",                      // assets/augmented_images/ipek.jpg
            answerImageAssetPath = "augmented_images/ipek.jpg",
            modelFilePath = "3d_models/glasses3d.glb",
            modelScale = 0.08f,
            modelRotationY = 90f
        )
    )

    // ───── Skor / süre durumu ─────
    private val solvedIds = mutableSetOf<Int>()
    private var _totalTimeMs: Long = 0L

    val totalSolved: Int
        get() = solvedIds.size

    val totalTimeMs: Long
        get() = _totalTimeMs

    fun totalQuestions(): Int = questions.size

    fun markSolved(questionId: Int, elapsedMs: Long) {
        // Aynı soru ikinci kez sayılmasın
        if (solvedIds.add(questionId)) {
            _totalTimeMs += elapsedMs
        }
    }

    fun isSolved(id: Int): Boolean = solvedIds.contains(id)

    fun nextUnsolved(): Question? =
        questions.firstOrNull { !solvedIds.contains(it.id) }

    fun reset() {
        solvedIds.clear()
        _totalTimeMs = 0L
    }
}
