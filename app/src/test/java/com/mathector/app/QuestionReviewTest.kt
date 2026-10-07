package com.mathector.app

import com.mathector.app.data.*
import org.junit.Assert.*
import org.junit.Test

class QuestionReviewTest {
    private val saved = Question(title = "函数练习", body = "求函数 \\(f(x)=x^2\\) 的值。", grade = "高一", kind = "解答题",
        difficulty = "基础", knowledge = "函数单调性", reviewed = true)

    @Test fun exerciseEditsInvalidateReviewButAuxiliarySettingsDoNot() {
        listOf(saved.copy(title = "新标题"), saved.copy(body = "新题干"), saved.copy(grade = "高二"), saved.copy(kind = "选择题"),
            saved.copy(difficulty = "进阶"), saved.copy(knowledge = "数列求和")).forEach { changed ->
            assertFalse(QuestionReview.edited(saved, changed).reviewed)
        }
        listOf(saved.copy(latex = "z^2=999"), saved.copy(autoSolve = true), saved.copy(favorite = true), saved.copy(solution = "AI 解答"),
            saved.copy(title = "1. 函数练习")).forEach { auxiliary -> assertTrue(QuestionReview.edited(saved, auxiliary).reviewed) }
    }

    @Test fun draftsRemainPendingUntilExplicitConfirmation() {
        val pending = saved.copy(reviewed = false)
        assertFalse(QuestionReview.edited(pending, pending.copy(reviewed = true)).reviewed)
        assertTrue(QuestionReview.confirmed(pending).reviewed)
        assertFalse(QuestionReview.edited(saved, saved.copy(body = "修改后的题干")).reviewed)
    }

    @Test fun aStandaloneFormulaCannotConfirmAnEmptyExercise() {
        assertThrows(IllegalArgumentException::class.java) { QuestionReview.confirmed(Question(latex = "x^2=3")) }
        assertThrows(IllegalArgumentException::class.java) { QuestionReview.confirmed(Question(body = "  ", latex = "x^2=3")) }
    }
}
