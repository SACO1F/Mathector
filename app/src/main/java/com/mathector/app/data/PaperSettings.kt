package com.mathector.app.data

data class PaperSettings(
    val title: String = "",
    val instructions: String = "",
    val minutes: Int = 0,
    val totalScore: Int = 0,
) {
    fun validated(): PaperSettings {
        val normalized = copy(title = title.trim(), instructions = instructions.trim())
        require(normalized.title.length <= 80) { "试卷主标题最多 80 字" }
        require(normalized.instructions.length <= 600) { "考试说明最多 600 字" }
        require(minutes in 0..999) { "考试时间应为 1–999 分钟，留空不显示" }
        require(totalScore in 0..9999) { "满分应为 1–9999 分，留空不显示" }
        return normalized
    }
    val summary: String get() = listOfNotNull(
        minutes.takeIf { it > 0 }?.let { "考试时间：$it 分钟" },
        totalScore.takeIf { it > 0 }?.let { "满分：$it 分" },
    ).joinToString("    ")
}

fun QuestionCollection.paperSettings() = PaperSettings(paperTitle, examInstructions, examMinutes, totalScore)
