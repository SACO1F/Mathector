package com.mathector.app.data

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.*
import android.text.style.ReplacementSpan
import com.mathector.app.ui.formulaBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

object DocumentExporter {
    private data class ExportQuestion(val body: List<MathPart>, val options: List<List<MathPart>> = emptyList())
    private fun bodyParts(value: String) = MathContent.parse(value).flatMap { part ->
        if(part is MathPart.Formula && part.display) listOf(MathPart.Text("\n"), part, MathPart.Text("\n")) else listOf(part)
    }
    suspend fun export(context: Context, title: String, questions: List<Question>, word: Boolean, paperSettings: PaperSettings = PaperSettings()): File {
        require(questions.isNotEmpty()) { "请先加入至少一道题" }
        require(questions.all { MathContent.question(it).isNotBlank() }) { "有题目尚未填写题干，请先补充；独立公式仅用于校对，不参与导出" }
        val paper = paperSettings.validated()
        val content = questions.mapIndexed { index, question ->
            val body = MathContent.question(question)
            val choice = if(question.kind == "选择题") ChoiceContent.split(body) else null
            val number = MathPart.Text("${index + 1}. ")
            if(choice == null) ExportQuestion(listOf(number) + bodyParts(body)) else {
                ExportQuestion(listOf(number) + bodyParts(choice.stem), choice.options.map { option ->
                    listOf(MathPart.Text("${option.label}. ")) + MathContent.parse(option.value).map { part -> when(part) {
                        is MathPart.Formula -> part.copy(display = false)
                        is MathPart.Text -> part.copy(value = part.value.replace(Regex("\\s+"), " "))
                    } }
                })
            }
        }
        val rendered = mutableMapOf<String, Bitmap>()
        try {
            content.flatMap { it.body + it.options.flatten() }.filterIsInstance<MathPart.Formula>().map { it.latex }.distinct().forEach { latex -> rendered[latex] = formulaBitmap(context, latex) }
            return withContext(Dispatchers.IO) {
                val folder = File(context.cacheDir, "exports").apply { mkdirs() }
                val filename = title.replace(Regex("[^\\p{L}\\p{N}_ -]"), "").take(50).ifBlank { "数学题集" }
                val target = File(folder, "$filename-${System.currentTimeMillis()}.${if(word) "docx" else "pdf"}")
                try { if (word) word(target, content, rendered, paper) else pdf(target, content, rendered, paper); target }
                catch (error: Exception) { target.delete(); throw error }
            }
        } finally { rendered.values.forEach(Bitmap::recycle) }
    }

    private fun dimensions(bitmap: Bitmap, maxWidth: Float = 500f): Pair<Float, Float> {
        val scale = minOf(minOf(maxWidth, 500f) / bitmap.width, 500f / bitmap.height, 12f / (21f * bitmap.density / 160f))
        return bitmap.width * scale to bitmap.height * scale
    }

    private class FormulaSpan(private val bitmap: Bitmap, maxWidth: Float) : ReplacementSpan() {
        private val width = dimensions(bitmap, maxWidth).first
        private val height = dimensions(bitmap, maxWidth).second
        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            val metrics = paint.fontMetricsInt
            val center = (metrics.ascent + metrics.descent) / 2f
            fm?.apply { ascent = minOf(metrics.ascent, floor(center - height / 2).toInt()); descent = maxOf(metrics.descent, ceil(center + height / 2).toInt()); top = ascent; bottom = descent }
            return ceil(width).toInt()
        }
        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            val center = (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2
            val above = y + center - height / 2
            canvas.drawBitmap(bitmap, null, RectF(x, above, x + width, above + height), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

    private fun textPaint() = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; color = Color.BLACK }
    private fun spannable(parts: List<MathPart>, formulas: Map<String, Bitmap>, width: Float): CharSequence {
        val text = SpannableStringBuilder()
        parts.forEach { part -> when(part) {
            is MathPart.Text -> text.append(part.value)
            is MathPart.Formula -> { val start = text.length; text.append('\uFFFC'); text.setSpan(FormulaSpan(formulas.getValue(part.latex), width), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        } }
        return text
    }
    private fun textLayout(parts: List<MathPart>, formulas: Map<String, Bitmap>, width: Float, paint: TextPaint = textPaint(), alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout {
        val text = spannable(parts, formulas, width)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt()).setAlignment(alignment).setLineSpacing(5f, 1.15f).setIncludePad(false).build()
    }
    private fun optionRows(question: ExportQuestion, formulas: Map<String, Bitmap>): List<List<ChoiceLayout.Cell>> =
        if(question.options.isEmpty()) emptyList() else ChoiceLayout.rows(question.options.map { Layout.getDesiredWidth(spannable(it, formulas, ChoiceLayout.WIDTH), textPaint()) })

    private fun pdf(file: File, questions: List<ExportQuestion>, formulas: Map<String, Bitmap>, paper: PaperSettings) {
        val document = PdfDocument()
        try {
            val writer = PdfWriter(document)
            writer.header(paper)
            questions.forEach { writer.question(it, formulas) }
            writer.finish()
            file.outputStream().use(document::writeTo)
        } finally { document.close() }
    }

    private class PdfWriter(private val document: PdfDocument) {
        private var number = 0
        private var page: PdfDocument.Page? = null
        private var y = 40f
        init { newPage() }
        private fun newPage() {
            finish(); number++; page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create()); y = 40f
        }
        private data class Column(val left: Float, val layout: StaticLayout, val top: Int = 0)
        fun header(paper: PaperSettings) {
            fun line(value: String, size: Float, bold: Boolean = false, center: Boolean = false) {
                val paint = textPaint().apply { textSize = size; if(bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
                val layout = textLayout(listOf(MathPart.Text(value)), emptyMap(), ChoiceLayout.WIDTH, paint, if(center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                drawRow(listOf(Column(0f, layout))); y += 10f
            }
            if(paper.title.isNotBlank()) line(paper.title, 20f, bold = true, center = true)
            if(paper.summary.isNotBlank()) line(paper.summary, 11f, center = true)
            if(paper.instructions.isNotBlank()) line("考试说明：\n${paper.instructions}", 11f)
            if(paper.title.isNotBlank() || paper.summary.isNotBlank() || paper.instructions.isNotBlank()) y += 12f
        }
        fun question(question: ExportQuestion, formulas: Map<String, Bitmap>) {
            val stem = textLayout(question.body, formulas, ChoiceLayout.WIDTH)
            val rows = optionRows(question, formulas).map { row ->
                val columns = row.map { cell -> Column(cell.left, textLayout(question.options[cell.index], formulas, cell.contentWidth)) }
                val baseline = columns.maxOf { it.layout.getLineBaseline(0) }
                columns.map { it.copy(top = baseline - it.layout.getLineBaseline(0)) }
            }
            val height = stem.height + rows.sumOf { row -> row.maxOf { it.layout.height + it.top } + 6 } + 24f
            if (height <= 762f && y > 40f && y + height > 802) newPage()
            drawRow(listOf(Column(0f, stem)))
            if(rows.isNotEmpty()) y += 6f
            rows.forEach { row -> drawRow(row); y += 6f }
            y += 24f
        }
        private fun drawRow(columns: List<Column>) {
            val rowHeight = columns.maxOf { it.layout.height + it.top }
            if(rowHeight <= 762 && y > 40f && y + rowHeight > 802) newPage()
            val lines = IntArray(columns.size)
            while(columns.indices.any { lines[it] < columns[it].layout.lineCount }) {
                val ends = columns.mapIndexed { index, column ->
                    val layout = column.layout
                    val top = layout.getLineTop(lines[index])
                    val offset = if(lines[index] == 0) column.top else 0
                    var end = lines[index]
                    while(end < layout.lineCount && layout.getLineBottom(end) - top + offset <= 802 - y) end++
                    end
                }
                if(columns.indices.all { ends[it] == lines[it] }) { newPage(); continue }
                var consumed = 0
                columns.forEachIndexed { index, column -> if(ends[index] > lines[index]) {
                    val top = column.layout.getLineTop(lines[index])
                    val height = column.layout.getLineBottom(ends[index] - 1) - top
                    val offset = if(lines[index] == 0) column.top else 0
                    consumed = maxOf(consumed, height + offset)
                    val left = 40f + column.left
                    page!!.canvas.apply { save(); clipRect(left, y + offset, left + column.layout.width, y + offset + height); translate(left, y + offset - top); column.layout.draw(this); restore() }
                    lines[index] = ends[index]
                } }
                y += consumed
                if(columns.indices.any { lines[it] < columns[it].layout.lineCount }) newPage()
            }
        }
        fun finish() { page?.let { document.finishPage(it); page = null } }
    }

    private fun xml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun word(file: File, questions: List<ExportQuestion>, formulas: Map<String, Bitmap>, paper: PaperSettings) {
        val media = linkedMapOf<String, Pair<Int, ByteArray>>()
        var drawingId = 0
        fun image(latex: String, maxWidth: Float): String {
            val bitmap = formulas.getValue(latex)
            val entry = media.getOrPut(latex) {
                val stream = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                media.size + 1 to stream.toByteArray()
            }
            val index = entry.first
            val (width, height) = dimensions(bitmap, maxWidth)
            val cx = (width * 12700).toLong(); val cy = (height * 12700).toLong()
            val shift = (-maxOf(0f, height / 2 - 4f) * 2).toInt()
            return """<w:r><w:rPr><w:position w:val="$shift"/></w:rPr><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="$cx" cy="$cy"/><wp:docPr id="${++drawingId}" name="公式"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="$index" name="image$index.png"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rId$index"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"""
        }
        fun paragraphs(parts: List<MathPart>, maxWidth: Float = ChoiceLayout.WIDTH, after: Int = 100): String = buildString {
            val paragraphStart = "<w:p><w:pPr><w:spacing w:after=\"$after\" w:line=\"360\" w:lineRule=\"auto\"/><w:rPr><w:sz w:val=\"24\"/></w:rPr></w:pPr>"
            append(paragraphStart)
            parts.forEach { part -> when(part) {
                is MathPart.Text -> part.value.split('\n').forEachIndexed { index, line ->
                    if(index > 0) { append("</w:p>"); append(paragraphStart) }
                    if(line.isNotEmpty()) append("<w:r><w:rPr><w:sz w:val=\"24\"/></w:rPr><w:t xml:space=\"preserve\">${xml(line)}</w:t></w:r>")
                }
                is MathPart.Formula -> append(image(part.latex, maxWidth))
            } }
            append("</w:p>")
        }
        fun table(question: ExportQuestion): String = buildString {
            val rows = optionRows(question, formulas)
            if(rows.isEmpty()) return@buildString
            val widths = rows.first().map { (it.cellWidth * 20).roundToInt() }.toMutableList()
            widths[widths.lastIndex] = 10300 - widths.dropLast(1).sum()
            append("<w:tbl><w:tblPr><w:tblW w:w=\"10300\" w:type=\"dxa\"/><w:tblBorders>")
            listOf("top", "left", "bottom", "right", "insideH", "insideV").forEach { append("<w:$it w:val=\"nil\"/>") }
            append("</w:tblBorders><w:tblLayout w:type=\"fixed\"/><w:tblCellMar><w:top w:w=\"0\" w:type=\"dxa\"/><w:bottom w:w=\"0\" w:type=\"dxa\"/><w:left w:w=\"0\" w:type=\"dxa\"/><w:right w:w=\"0\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr><w:tblGrid>")
            widths.forEach { append("<w:gridCol w:w=\"$it\"/>") }; append("</w:tblGrid>")
            rows.forEach { row ->
                append("<w:tr>")
                row.forEachIndexed { index, cell ->
                    val gap = ((cell.cellWidth - cell.contentWidth) * 20).roundToInt()
                    append("<w:tc><w:tcPr><w:tcW w:w=\"${widths[index]}\" w:type=\"dxa\"/><w:tcMar><w:right w:w=\"$gap\" w:type=\"dxa\"/></w:tcMar><w:vAlign w:val=\"top\"/></w:tcPr>")
                    append(paragraphs(question.options[cell.index], cell.contentWidth, after = 0)); append("</w:tc>")
                }
                append("</w:tr>")
            }
            append("</w:tbl>")
        }
        val body = buildString {
            fun header(value: String, size: Int, bold: Boolean = false, center: Boolean = false) {
                append("<w:p><w:pPr><w:keepNext/><w:spacing w:after=\"200\" w:line=\"360\" w:lineRule=\"auto\"/>")
                if(center) append("<w:jc w:val=\"center\"/>")
                append("</w:pPr><w:r><w:rPr>")
                if(bold) append("<w:b/>")
                append("<w:sz w:val=\"$size\"/>")
                append("</w:rPr>")
                value.lines().forEachIndexed { index, line -> if(index > 0) append("<w:br/>"); append("<w:t xml:space=\"preserve\">${xml(line)}</w:t>") }
                append("</w:r></w:p>")
            }
            if(paper.title.isNotBlank()) header(paper.title, 40, bold = true, center = true)
            if(paper.summary.isNotBlank()) header(paper.summary, 22, center = true)
            if(paper.instructions.isNotBlank()) header("考试说明：\n${paper.instructions}", 22)
            questions.forEach { question ->
                append(paragraphs(question.body)); append(table(question))
                append("<w:p><w:pPr><w:spacing w:after=\"240\"/></w:pPr></w:p>")
            }
            append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"800\" w:right=\"800\" w:bottom=\"800\" w:left=\"800\"/></w:sectPr>")
        }
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, value: String) { zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            entry("_rels/.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            entry("word/document.xml", """<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>$body</w:body></w:document>""")
            entry("word/_rels/document.xml.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" + media.values.joinToString("") { (id, _) -> "<Relationship Id=\"rId$id\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image$id.png\"/>" } + "</Relationships>")
            media.values.forEach { (id, bytes) -> zip.putNextEntry(ZipEntry("word/media/image$id.png")); zip.write(bytes); zip.closeEntry() }
        }
    }
}
