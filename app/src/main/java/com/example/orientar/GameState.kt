// GameState.kt
data class THQuestion(
    val id: Int,
    val title: String,
    val text: String,
    val answerImageName: String,
    val answerImageAssetPath: String,
    val modelGlbAssetPath: String
)

object GameState {
    val questions = listOf(
        THQuestion(
            id = 1,
            title = "Question 1",
            text = "Everyone looks at me while passing by...",
            answerImageName = "parents-aileler",                 // <-- isim
            answerImageAssetPath = "augmented_images/parents-aileler.jpg", // <-- tam yol + doğru uzantı
            modelGlbAssetPath = "3d_models/Backpack.glb"
        )
    )
    fun firstQuestion() = questions.first()
}
