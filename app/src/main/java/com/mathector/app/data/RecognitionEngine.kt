package com.mathector.app.data

import java.io.Closeable
import java.io.File

data class RecognitionResult(val text: String, val questions: List<RecognizedQuestion> = emptyList())
data class RecognizedQuestion(val body: String, val latex: String = "", val sourceRegion: String? = null,
    val grade: String = "", val kind: String = "解答题", val difficulty: String = "待评估", val knowledge: String = "")
interface RecognitionEngine : Closeable {
    suspend fun recognize(source: File): RecognitionResult
}
