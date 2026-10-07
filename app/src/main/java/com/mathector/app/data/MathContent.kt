package com.mathector.app.data

/** Text shared by recognition, previews and document layout. */
object QuestionText {
    // Source main numbers are removed; multiple subquestions get their own contiguous sequence.
    private val prefix = Regex("""^\s*(?:第\s*[0-9一二三四五六七八九十百]+\s*题\s*[:：.．、]?|[0-9]{1,3}\s*[.．、](?![0-9])|[（(]\s*[0-9]{1,3}\s*[)）](?!\s*[+\-=*/^,，])\s*[.．、]?|[①-⑳])\s*""")
    private val subPrefix = Regex("""^\s*(?:[（(]\s*[0-9]{1,3}\s*[)）](?!\s*[+\-=*/^,，])\s*[.．、]?|[①-⑳])\s*""")
    private val task = Regex("""^(?:求|证明|判断|说明|计算|写出|解方程|解不等式|比较|讨论)""")
    fun cleanTitle(value: String): String {
        var result = value.trim()
        repeat(3) { result = prefix.replaceFirst(result, "") }
        return result
    }
    fun clean(value: String): String {
        val ranges = MathContent.formulaRanges(value)
        val boundary = Regex("""(?<=[。；;：:])\s*(?=(?:[（(]\s*[0-9]{1,3}\s*[)）](?!\s*[+\-=*/^,，])|[①-⑳]))""")
        val text = buildString {
            var start = 0
            boundary.findAll(value).filter { match -> ranges.none { match.range.last + 1 in it } }.forEach { match ->
                append(value.substring(start, match.range.first)); append('\n'); start = match.range.last + 1
            }
            append(value.substring(start))
        }
        val formulaRanges = MathContent.formulaRanges(text)
        val marked = mutableListOf<Int>(); val inferred = mutableListOf<Int>()
        var offset = 0
        val lines = text.lines().mapIndexed { index, original ->
            val inFormula = formulaRanges.any { offset + original.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0) in it }
            offset += original.length + 1
            val line = if(inFormula) original.trimEnd() else cleanTitle(original)
            if(!inFormula && subPrefix.containsMatchIn(original)) marked += index
            if(!inFormula && task.containsMatchIn(line)) inferred += index
            line
        }
        val first = lines.indexOfFirst(String::isNotBlank)
        val parts = if(marked.isNotEmpty()) marked else inferred.filter { it > first }
        var number = 0
        return lines.mapIndexed { index, line ->
            if(parts.size > 1 && index in parts) "（${++number}）$line" else line
        }.joinToString("\n").trim()
    }
    fun withSubquestions(stem: String, parts: List<String>): String = clean(
        listOf(clean(stem)).plus(parts.mapIndexed { index, part -> "（${index + 1}）${cleanTitle(part)}" }).joinToString("\n")
    )
    fun normalize(question: Question) = question.copy(title = cleanTitle(question.title).ifBlank { "新题目" }, body = clean(question.body))
    fun titleFromBody(body: String): String = MathContent.parse(clean(body).lineSequence().firstOrNull().orEmpty())
        .filterIsInstance<MathPart.Text>().joinToString("") { it.value }.trim(' ', '，', '。', '：').take(36).ifBlank { "待录入题目" }
}

sealed interface MathPart {
    data class Text(val value: String) : MathPart
    data class Formula(val latex: String, val display: Boolean = false) : MathPart
}

object MathContent {
    private val delimiters = Regex("""(?s)\$\$(.+?)\$\$|\\\[(.+?)\\\]|\\\((.+?)\\\)|(?<![\\\$])\$(?!\$)([^\n$]+?)(?<!\\)\$(?!\$)""")
    private val legacy = Regex("""(?<![A-Za-z0-9])(?:\\[A-Za-z]+|[A-Za-zα-ωΑ-Ω√]|[0-9])(?:[A-Za-z0-9α-ωΑ-Ω²³⁰¹⁴⁵⁶⁷⁸⁹₀-₉ₙπ∞×÷·≤≥≠∈∉∪∩√+\-=−－＋*/^_{}()\[\],.'|\\ \t])*""")
    private val operator = Regex("""[=+\-−－＋*/^_²³⁰¹⁴⁵⁶⁷⁸⁹₀-₉ₙ×÷·≤≥≠∈∉∪∩√]|\\[A-Za-z]+""")
    internal fun formulaRanges(value: String): List<IntRange> = delimiters.findAll(value).map { it.range }.toList()
    fun parse(value: String): List<MathPart> = buildList {
        var offset = 0
        delimiters.findAll(value).forEach { match ->
            addLegacy(value.substring(offset, match.range.first), this)
            val content = (1..4).firstNotNullOf { match.groups[it]?.value }
            add(MathPart.Formula(content.trim(), match.groups[1] != null || match.groups[2] != null))
            offset = match.range.last + 1
        }
        addLegacy(value.substring(offset), this)
    }
    private fun addLegacy(text: String, target: MutableList<MathPart>) {
        var offset = 0
        legacy.findAll(text).forEach { match ->
            val expression = match.value.trimEnd(' ', '\t', '.', ',')
            if (!operator.containsMatchIn(expression)) return@forEach
            if (offset < match.range.first) target += MathPart.Text(text.substring(offset, match.range.first))
            target += MathPart.Formula(toLatex(expression))
            offset = match.range.first + expression.length
        }
        if (offset < text.length) target += MathPart.Text(text.substring(offset))
    }
    fun toLatex(value: String): String {
        val supers = "⁰¹²³⁴⁵⁶⁷⁸⁹"
        return buildString {
            var index = 0
            while (index < value.length) {
                val char = value[index]
                if (char in supers || char in '₀'..'₉') {
                    val upper = char in supers
                    append(if(upper) "^{" else "_{")
                    while(index < value.length && if(upper) value[index] in supers else value[index] in '₀'..'₉') {
                        append(if(upper) supers.indexOf(value[index]) else value[index] - '₀'); index++
                    }
                    append('}'); continue
                }
                append(when(char) {
                    '×' -> "\\times "; '÷' -> "\\div "; '·' -> "\\cdot "; '≤' -> "\\le "; '≥' -> "\\ge "
                    '≠' -> "\\ne "; 'π' -> "\\pi "; '∞' -> "\\infty "; '∈' -> "\\in "; '∉' -> "\\notin "
                    '∪' -> "\\cup "; '∩' -> "\\cap "; '−', '－' -> "-"; '＋' -> "+"; '√' -> "\\sqrt"; 'ₙ' -> "_{n}"; else -> char.toString()
                }); index++
            }
        }
    }
    private fun canonical(value: String) = toLatex(value).replace(Regex("""\\(?:left|right|quad|qquad|,|;)"""), "")
        .replace(Regex("""\s|[{}]"""), "")

    /** Old records may store the only formula separately; keep it without duplicating inline math. */
    fun withSupplement(text: String, latex: String): String {
        if (latex.isBlank()) return text
        val formula = canonical(latex)
        val represented = parse(text).filterIsInstance<MathPart.Formula>().any { canonical(it.latex).contains(formula) }
        return if(represented) text else text.trimEnd() + "\n\\[" + latex.trim() + "\\]"
    }
    // Independent LaTeX is a proofreading aid, never exercise content, even for a blank body.
    fun question(question: Question): String = QuestionText.clean(question.body)
    fun preview(question: Question): String = question(question)

    fun proofreadingFormula(value: String): String {
        val source = value.trim()
        if(source.isEmpty()) return ""
        return if(source.startsWith("\\[") || source.startsWith("\\(") || source.startsWith('$')) source else "\\[$source\\]"
    }
}
