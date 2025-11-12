package com.example.orientar

// Basit soru modeli
data class Question(
    val id: Int,
    val title: String,
    val text: String,
    // Augmented Image ismi (DB'ye eklerken verdiğimiz ad)
    val answerImageName: String,
    // assets içindeki görüntü yolu
    val answerImageAssetPath: String,
    // assets içindeki .glb yolu (ör: "3d_models/Backpack.glb")
    val modelFilePath: String
)

object GameState {
    // Örnek tek soru (dosya adlarını kendi asset'lerinle eşleştir)
    val questions: MutableList<Question> = mutableListOf(
        Question(
            id = 1,
            title = "Question 1",
            text = "Find the image and keep it in view for 5s!",
            answerImageName = "parents-aileler",                 // İSİM: dosya adı uzantısız
            answerImageAssetPath = "augmented_images/parents-aileler.jpg",
            modelFilePath = "3d_models/Backpack.glb"
        )
    )

    // === Skor durumları ===
    private val solvedIds = mutableSetOf<Int>()
    private var _totalTimeMs: Long = 0L

    // ScoreboardActivity'nin beklediği üyeler:
    val totalSolved: Int
        get() = solvedIds.size

    val totalTimeMs: Long
        get() = _totalTimeMs

    fun totalQuestions(): Int = questions.size

    fun markSolved(questionId: Int, elapsedMs: Long) {
        // Aynı soru iki kere çözülmesin
        if (solvedIds.add(questionId)) {
            _totalTimeMs += elapsedMs
        }
    }

    fun reset() {
        solvedIds.clear()
        _totalTimeMs = 0L
    }
}
