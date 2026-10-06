package com.mathector.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import androidx.work.*
import com.mathector.app.MathectorApplication
import kotlinx.coroutines.CancellationException
import java.io.File

class ImportWorker @JvmOverloads constructor(context: Context, params: WorkerParameters,
    private val engineFactory: (() -> RecognitionEngine)? = null) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MathectorApplication
        val dao = app.database.dao()
        val jobId = inputData.getString("jobId") ?: return Result.failure()
        val job = dao.importJob(jobId) ?: return Result.failure()
        var engine: RecognitionEngine? = null
        var failures = 0; var pageNumber = 0; var count = 0; var lastError = ""
        try {
            engine = engineFactory?.invoke() ?: MultimodalRecognitionEngine(app.settings.multimodalConfig())
            dao.save(job.copy(status = "processing", message = "正在准备识别"))
            for (path in job.paths.split('\n').filter(String::isNotBlank)) {
                if (isStopped) throw CancellationException()
                val file = File(path)
                try {
                    val pages = if (file.extension.lowercase() == "pdf") renderPdf(file, jobId) else listOf(file)
                    for (page in pages) {
                        pageNumber++
                        try {
                        dao.save(job.copy(status = "processing", message = "正在识别第 $pageNumber 页"))
                        val cache = AtomicFile(File(applicationContext.filesDir, "sources/$jobId/recognition-$pageNumber.cache"))
                        val recognized = if (cache.baseFile.exists()) {
                            MultimodalRecognitionEngine.decodeDrafts(cache.readFully().toString(Charsets.UTF_8))
                        } else {
                            val result = engine.recognize(page)
                            cache.baseFile.parentFile?.mkdirs()
                            val stream = cache.startWrite()
                            try { stream.write(MultimodalRecognitionEngine.encodeDrafts(result).toByteArray(Charsets.UTF_8)); cache.finishWrite(stream) }
                            catch (error: Exception) { cache.failWrite(stream); throw error }
                            result
                        }
                        val groups = recognized.questions.ifEmpty { QuestionSplitter.split(recognized.text).ifEmpty { listOf("") }.map { RecognizedQuestion(it) } }
                        for ((index, draft) in groups.withIndex()) {
                            val body = draft.body
                            val id = "$jobId-$pageNumber-$index"
                            // A resumed worker must never overwrite an already edited draft.
                            if (dao.question(id) == null) {
                                val question = KnowledgeCatalog.normalize(Question(id = id, title = QuestionText.titleFromBody(body),
                                    body = body, latex = draft.latex,
                                    grade = draft.grade, kind = draft.kind,
                                    difficulty = draft.difficulty, knowledge = draft.knowledge,
                                    sourcePath = page.absolutePath, sourceLabel = "${file.name} · 第 $pageNumber 页", autoSolve = inputData.getBoolean("autoSolve", false)))
                                dao.save(question)
                                if(question.autoSolve && (question.body.isNotBlank() || question.latex.isNotBlank())) SolutionScheduler.enqueue(applicationContext, question, ExistingWorkPolicy.KEEP)
                            }
                            count++
                        }
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (error: Exception) { failures++; lastError = error.message?.take(100) ?: "页面无法识别" }
                    }
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { failures++; lastError = error.message?.take(100) ?: "文件无法识别"; dao.save(job.copy(status = "processing", message = "${file.name}：$lastError")) }
            }
            dao.save(job.copy(status = if (failures == 0) "complete" else if(count == 0) "failed" else "partial", message = "已生成 $count 道草稿" + if (failures > 0) "，$failures 处识别失败：$lastError" else "，请校对公式与图形"))
            return if (count == 0 && failures > 0) Result.failure() else Result.success()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            dao.save(job.copy(status = "failed", message = error.message?.take(150) ?: "识别失败，请重试"))
            return Result.failure()
        } finally { engine?.close() }
    }

    private fun renderPdf(file: File, jobId: String): List<File> {
        val folder = File(applicationContext.filesDir, "sources/$jobId").apply { mkdirs() }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                require(renderer.pageCount <= 20) { "首版每次支持最多 20 页 PDF，请分批导入" }
                (0 until renderer.pageCount).map { index ->
                    val target = File(folder, "${file.nameWithoutExtension}-$index.jpg")
                    if (!target.exists()) renderer.openPage(index).use { page ->
                        val scale = minOf(1400f / page.width, 2000f / page.height)
                        val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                        } finally { bitmap.recycle() }
                    }
                    target
                }
            }
        }
    }
}
