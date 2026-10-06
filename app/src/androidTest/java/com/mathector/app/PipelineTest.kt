package com.mathector.app

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.mathector.app.data.*
import com.mathector.app.ui.formulaBitmap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(AndroidJUnit4::class)
class PipelineTest {
    private val app = ApplicationProvider.getApplicationContext<MathectorApplication>()

    @Test fun exportContainsOnlySelectionNumbersBodyAndSubquestionsWithInlineMath(): Unit = runBlocking {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var activity: MainActivity? = null
        scenario.onActivity { activity = it }
        val first = Question(title = "9. 不应导出的标题", body = "9. 已知函数 \\(f(x)=\\frac{x^2}{2}+\\sqrt{x}\\)。\n（1）求 \\(f(4)\\)。\n（2）说明定义域。", grade = "高二", solution = "不应导出的答案", sourceLabel = "不应导出的来源", reviewed = true)
        val second = Question(title = "2. 不应导出的另一标题", body = "2. 已知等差数列 \\(a_n=2n+1\\)。\n①求前 10 项和 \\(S_{10}\\)。", reviewed = true)
        val folder = File(app.getExternalFilesDir(null), "validation").apply { mkdirs() }
        try {
            val pdf = DocumentExporter.export(activity!!, "不应导出的题集名", listOf(second, first), word = false)
            try {
                pdf.copyTo(File(folder, "sample-export-0.4.0.pdf"), overwrite = true)
                ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor -> PdfRenderer(descriptor).use { renderer ->
                    val text = (0 until renderer.pageCount).joinToString("\n") { page -> renderer.openPage(page).use { it.textContents.joinToString(" ") { part -> part.text } } }.replace(Regex("\\s+"), "")
                    File(folder, "export-text-validation.txt").writeText(text)
                    assertTrue(text.contains("已知等差数列")); assertTrue(text.contains("已知函数"))
                    assertTrue(text.indexOf("已知等差数列") < text.indexOf("已知函数"))
                    assertFalse(text.contains("不应导出")); assertFalse(text.contains("姓名"))
                    val normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
                    assertTrue(normalized.contains("(1)求")); assertTrue(normalized.contains("(2)说明定义域"))
                    assertFalse(text.contains("\\frac")); assertFalse(text.contains("MATHECTOR"))
                } }
            } finally { pdf.delete() }
            val word = DocumentExporter.export(activity!!, "不应导出的题集名", listOf(second, first), word = true)
            try { ZipFile(word).use { zip ->
                val xml = zip.getInputStream(zip.getEntry("word/document.xml")).use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
                val runs = xml.getElementsByTagName("w:t")
                val text = (0 until runs.length).joinToString("") { runs.item(it).textContent }
                assertTrue(text.startsWith("1. 已知等差数列")); assertTrue(text.contains("2. 已知函数"))
                assertFalse(text.contains("不应导出")); assertFalse(text.contains("9.")); assertFalse(text.contains("①"))
                assertTrue(text.contains("（1）求")); assertTrue(text.contains("（2）说明定义域"))
                assertFalse(text.contains("\\frac")); assertFalse(text.contains("姓名"))
                assertEquals(4, zip.entries().asSequence().count { it.name.startsWith("word/media/") })
                assertEquals(4, xml.getElementsByTagName("w:drawing").length)
                word.copyTo(File(folder, "sample-export-0.4.0.docx"), overwrite = true)
            } } finally { word.delete() }
        } finally { scenario.close() }
    }

    @Test fun collectionOrderAndDeleteCascadeArePersistent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, MathectorDatabase::class.java).build()
        try {
            val dao = db.dao()
            val c = QuestionCollection(title = "验证题集")
            val a = Question(title = "题一"); val b = Question(title = "题二")
            dao.save(c); dao.save(a); dao.save(b)
            dao.addToCollection(c.id, a.id); dao.addToCollection(c.id, b.id); dao.addToCollection(c.id, a.id)
            assertEquals(2, dao.orderedItems(c.id).size)
            dao.move(c.id, b.id, -1)
            assertEquals(b.id, dao.orderedItems(c.id).first().questionId)
            dao.deleteQuestion(b.id)
            assertEquals(listOf(a.id), dao.orderedItems(c.id).map { it.questionId })
        } finally { db.close() }
    }

    @Test fun offlineFormulaProducesVisiblePixelsAndValidDocuments(): Unit = runBlocking {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var activity: MainActivity? = null
        scenario.onActivity { activity = it }
        val owner = activity!!
        try {
        val bitmap = formulaBitmap(owner, "x^2+\\frac{1}{2}=3")
        try {
            val density = bitmap.density / 160f
            assertTrue("Formula snapshots must exclude viewport scrollbars and empty width", bitmap.width / density < 240f)
            assertTrue("Formula snapshots must exclude viewport scrollbars and empty height", bitmap.height / density < 110f)
            var darkPixels = 0
            for(y in 0 until bitmap.height step 2) for(x in 0 until bitmap.width step 2) {
                val color = bitmap.getPixel(x,y)
                if(Color.red(color) < 150 && Color.green(color) < 150 && Color.blue(color) < 150) darkPixels++
            }
            assertTrue("Formula snapshot must contain visible rendered glyphs", darkPixels > 100)
            repeat(5) {
                val repeated = formulaBitmap(owner, "x^2+\\frac{1}{2}=3")
                try {
                    assertEquals("Repeated renders must retain all formula glyphs", bitmap.width, repeated.width)
                    assertEquals(bitmap.height, repeated.height)
                } finally { repeated.recycle() }
            }
        } finally { bitmap.recycle() }
        val q = Question(title = "公式导出验证", body = "求下列方程的解：\\(x^2+\\frac{1}{2}=3\\)。", latex = "x^2+\\frac{1}{2}=3", reviewed = true)
        val documentQuestions = List(8) { q.copy(id = "export-$it", body = q.body + "\n" + List(8) { "请写出计算步骤，检查结果是否符合题意。" }.joinToString("\n")) }
        val validation = File(app.getExternalFilesDir(null), "validation").apply { mkdirs() }
        val pdf = DocumentExporter.export(owner, "导出验证", documentQuestions, word = false)
        try {
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor -> PdfRenderer(descriptor).use { assertTrue("Long collections must paginate", it.pageCount > 1) } }
            pdf.copyTo(File(validation, "sample-export.pdf"), overwrite = true)
        } finally { pdf.delete() }
        val word = DocumentExporter.export(owner, "导出验证", listOf(q), word = true)
        try { ZipFile(word).use { zip ->
            assertNotNull(zip.getEntry("word/media/image1.png"))
            zip.entries().asSequence().filter { it.name.endsWith(".xml") || it.name.endsWith(".rels") }.forEach { entry ->
                zip.getInputStream(entry).use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
            }
            word.copyTo(File(validation, "sample-export.docx"), overwrite = true)
        } } finally { word.delete() }
        } finally { scenario.close() }
    }

    @Test fun missingConfigurationCannotUseLegacyOfflineRecognition() = runBlocking {
        val id = "test-${UUID.randomUUID()}"
        val image = File(app.cacheDir, "$id.jpg")
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 56f }
            canvas.drawText("1. 已知一次函数 y = 2x + 1", 60f, 160f, paint)
            canvas.drawText("求 x = 3 时的函数值。", 60f, 260f, paint)
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } finally { bitmap.recycle() }
        val dao = app.database.dao()
        dao.save(ImportJob(id = id, paths = image.absolutePath))
        try {
            assertFalse("The dedicated test emulator must not contain a real API configuration", app.settings.state.value.recognitionReady)
            val worker = TestListenableWorkerBuilder<ImportWorker>(app).setInputData(workDataOf("jobId" to id, "recognitionMode" to "LOCAL")).build()
            worker.doWork()
            assertNull(dao.question("$id-1-0"))
            assertEquals("failed", dao.importJob(id)!!.status)
            assertTrue(dao.importJob(id)!!.message.contains("配置多模态接口"))
        } finally {
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM questions WHERE id LIKE ?", arrayOf("$id%"))
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM imports WHERE id = ?", arrayOf(id))
            image.delete()
        }
    }
}
