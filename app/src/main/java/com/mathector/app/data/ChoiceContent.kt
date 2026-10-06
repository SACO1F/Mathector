package com.mathector.app.data

data class ChoiceOption(val label: String, val value: String)
data class ChoiceQuestion(val stem: String, val options: List<ChoiceOption>)

/** Locate complete A/B/C/D options without interpreting labels inside a LaTeX expression. */
object ChoiceContent {
    private val marker = Regex("""(?<![A-Za-z0-9\\])(?:([A-DＡ-Ｄ])[.．、:：)）]|[（(]([A-DＡ-Ｄ])[）)])\s*""")
    fun split(body: String): ChoiceQuestion? {
        val formulas = MathContent.formulaRanges(body)
        val labels = marker.findAll(body).filter { match -> formulas.none { match.range.first in it } }.toList()
        fun label(match: MatchResult): String {
            val char = (match.groups[1] ?: match.groups[2])!!.value.single()
            return (if(char in 'Ａ'..'Ｄ') 'A' + (char - 'Ａ') else char).toString()
        }
        val group = labels.windowed(4).lastOrNull { row -> row.map(::label) == listOf("A", "B", "C", "D") } ?: return null
        val options = group.mapIndexed { index, match ->
            val end = group.getOrNull(index + 1)?.range?.first ?: body.length
            ChoiceOption(label(match), body.substring(match.range.last + 1, end).trim())
        }
        if(options.any { it.value.isBlank() }) return null
        return ChoiceQuestion(body.substring(0, group.first().range.first).trimEnd(), options)
    }
}

/** Widths include the option label and rendered formula dimensions, in document points. */
object ChoiceLayout {
    const val WIDTH = 515f
    const val GAP = 18f
    data class Cell(val index: Int, val left: Float, val contentWidth: Float, val cellWidth: Float)
    fun rows(widths: List<Float>): List<List<Cell>> {
        require(widths.size == 4 && widths.all { it.isFinite() && it >= 0 })
        val natural = widths.map { kotlin.math.ceil(it) + 6f }
        if(natural.sum() + GAP * 3 <= WIDTH) {
            val gap = (WIDTH - natural.sum()) / 3
            var left = 0f
            return listOf(natural.mapIndexed { index, width ->
                val cellWidth = width + if(index < 3) gap else 0f
                Cell(index, left, width, cellWidth).also { left += cellWidth }
            })
        }
        val column = (WIDTH - GAP) / 2
        return listOf(0, 2).map { start -> listOf(Cell(start, 0f, column, column + GAP), Cell(start + 1, column + GAP, column, column)) }
    }
}
