package com.mathector.app

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mathector.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.Normalizer
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(AndroidJUnit4::class)
class PaperExportTest {
    private val app = ApplicationProvider.getApplicationContext<MathectorApplication>()
    private val folder get() = File(app.getExternalFilesDir(null), "validation").apply { mkdirs() }
    private fun pdfPages(file: File): List<String> = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer -> (0 until renderer.pageCount).map { index ->
            renderer.openPage(index).use { page -> Normalizer.normalize(page.textContents.joinToString(" ") { it.text }, Normalizer.Form.NFKC).replace(Regex("\\s+"), "") }
        } }
    }
    @Test fun paperHeaderAndSubquestionNumbersExportInBothFormats(): Unit = runBlocking {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var activity: MainActivity? = null
        scenario.onActivity { activity = it }
        val paper = PaperSettings("高二数学阶段测试", "请将答案写在答题卡上。\n请检查题号，完成后核对答案。", 120, 150)
        val questions = listOf(
            Question(title = "私有标题不应导出", kind = "选择题", body = "9. 函数 \\(f(x)=\\log_{0.5}(x^2-5x-6)\\) 的单调增区间是（ ）\nA. \\((5/2,+\\infty)\\)\nB. \\((-\\infty,5/2)\\)\nC. \\((-\\infty,2)\\)\nD. \\((-\\infty,-1)\\)", reviewed = true),
            Question(body = "15. 已知函数 \\(f(x)=x^2-4x+3\\)。\n（3）求 \\(f(2)\\)。\n（8）说明函数的单调区间。", reviewed = true),
            Question(body = "已知等差数列 \\(a_n=2n+1\\)。\n求前 10 项和。\n证明该数列递增。", reviewed = true),
        )
        try {
            val pdf = DocumentExporter.export(activity!!, "阶段测试", questions, false, paper)
            try {
                pdf.copyTo(File(folder, "exam-paper-export-0.7.0.pdf"), overwrite = true)
                val pages = pdfPages(pdf); assertEquals(1, pages.size)
                val text = pages.single()
                assertTrue(text.startsWith(paper.title)); assertTrue(text.contains("考试时间:120分钟")); assertTrue(text.contains("满分:150分"))
                assertTrue(text.contains("考试说明:")); assertTrue(text.contains("请检查题号"))
                assertTrue(text.contains("1.函数")); assertTrue(text.contains("2.已知函数")); assertTrue(text.contains("3.已知等差数列"))
                assertEquals(2, Regex("\\(1\\)").findAll(text).count()); assertEquals(2, Regex("\\(2\\)").findAll(text).count())
                assertFalse(text.contains("私有标题")); assertFalse(text.contains("\\log")); assertFalse(text.contains("15."))
            } finally { pdf.delete() }
            val word = DocumentExporter.export(activity!!, "阶段测试", questions, true, paper)
            try {
                word.copyTo(File(folder, "exam-paper-export-0.7.0.docx"), overwrite = true)
                ZipFile(word).use { zip ->
                    val doc = zip.getInputStream(zip.getEntry("word/document.xml")).use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
                    val nodes = doc.getElementsByTagName("w:t")
                    val text = (0 until nodes.length).joinToString("") { nodes.item(it).textContent }
                    assertTrue(text.startsWith(paper.title + paper.summary)); assertTrue(text.contains(paper.instructions.lineSequence().first()))
                    assertTrue(text.contains("1. 函数")); assertTrue(text.contains("2. 已知函数")); assertTrue(text.contains("3. 已知等差数列"))
                    assertEquals(2, Regex("（1）").findAll(text).count()); assertEquals(2, Regex("（2）").findAll(text).count())
                    assertEquals(1, doc.getElementsByTagName("w:b").length)
                    assertEquals(2, doc.getElementsByTagName("w:jc").length)
                    assertEquals(8, doc.getElementsByTagName("w:drawing").length)
                    assertFalse(text.contains("私有标题")); assertFalse(text.contains("（3）")); assertFalse(text.contains("（8）"))
                    zip.entries().asSequence().filter { it.name.endsWith(".xml") || it.name.endsWith(".rels") }.forEach { entry -> zip.getInputStream(entry).use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) } }
                }
            } finally { word.delete() }
        } finally { scenario.close() }
    }
    @Test fun longPaperKeepsHeaderOnFirstPageAndRetainsEverySubquestion(): Unit = runBlocking {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var activity: MainActivity? = null
        scenario.onActivity { activity = it }
        val paper = PaperSettings("高中数学综合练习", "请将计算过程写完整；证明题需给出理由。", 90, 100)
        val questions = List(9) { index -> Question(body = "已知第${index + 1}组函数条件。\n（4）求最值。\n" + List(7) { "请写出完整的计算过程，并检查定义域和边界条件。" }.joinToString("\n") + "\n（6）证明结论，校验点${index + 1}。", reviewed = true) }
        try {
            val pdf = DocumentExporter.export(activity!!, "综合练习", questions, false, paper)
            try {
                pdf.copyTo(File(folder, "exam-paper-multipage-0.7.0.pdf"), overwrite = true)
                val pages = pdfPages(pdf); assertTrue(pages.size > 1)
                assertTrue(pages.first().contains(paper.title)); pages.drop(1).forEach { assertFalse(it.contains(paper.title)); assertFalse(it.contains("考试时间")) }
                val text = pages.joinToString("")
                assertEquals(9, Regex("\\(1\\)求最值").findAll(text).count()); assertEquals(9, Regex("\\(2\\)证明结论").findAll(text).count())
                assertTrue(text.contains("校验点9"))
            } finally { pdf.delete() }
        } finally { scenario.close() }
    }
}
