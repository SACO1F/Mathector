package com.mathector.app.data

import android.content.Context
import androidx.work.*
import com.mathector.app.MathectorApplication
import kotlinx.coroutines.CancellationException
import java.io.Closeable
import java.security.MessageDigest

data class SolutionResult(val answer: String, val steps: List<String>, val latex: String = "") {
    val formatted: String get() = "答案：$answer\n\n" + steps.mapIndexed { index, step -> "${index + 1}. $step" }.joinToString("\n\n")
}
interface QuestionSolver { suspend fun solve(question: Question): SolutionResult }

fun Question.contentFingerprint(): String = MessageDigest.getInstance("SHA-256")
    .digest(listOf(title, body, latex, sourcePath).joinToString("\u0000").toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

object SolutionScheduler {
    fun name(id: String) = "solution-$id"
    fun enqueue(context: Context, question: Question, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        WorkManager.getInstance(context).enqueueUniqueWork(name(question.id), policy,
            OneTimeWorkRequestBuilder<SolutionWorker>().setInputData(workDataOf("questionId" to question.id, "fingerprint" to question.contentFingerprint()))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag("solutions").addTag(name(question.id)).build())
    }
    fun cancel(context: Context, id: String) { WorkManager.getInstance(context).cancelUniqueWork(name(id)) }
}

class SolutionWorker @JvmOverloads constructor(context: Context, params: WorkerParameters,
    private val solverFactory: (() -> QuestionSolver)? = null) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MathectorApplication
        val dao = app.database.dao()
        val id = inputData.getString("questionId") ?: return Result.failure()
        val fingerprint = inputData.getString("fingerprint") ?: return Result.failure()
        val question = dao.question(id) ?: return Result.success()
        if (question.contentFingerprint() != fingerprint) return Result.success()
        var solver: QuestionSolver? = null
        try {
            require(question.body.isNotBlank() || question.latex.isNotBlank()) { "请先填写题干或公式" }
            dao.saveSolution(id, fingerprint, null, "")
            solver = solverFactory?.invoke() ?: MultimodalRecognitionEngine(app.settings.multimodalConfig())
            val result = solver.solve(question)
            if (isStopped) throw CancellationException()
            dao.saveSolution(id, fingerprint, result)
            return Result.success()
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            dao.saveSolution(id, fingerprint, null, error.message?.take(160) ?: "解题失败，请重试")
            return Result.failure()
        } finally { (solver as? Closeable)?.close() }
    }
}
