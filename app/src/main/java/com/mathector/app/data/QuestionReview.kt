package com.mathector.app.data

/** Only an explicit confirmation reviews an exercise. Scratch formulas and AI settings do not change its content. */
object QuestionReview {
    fun edited(saved: Question, draft: Question): Question = draft.copy(
        reviewed = saved.reviewed && sameContent(saved, draft)
    )

    private fun sameContent(first: Question, second: Question): Boolean =
        QuestionText.cleanTitle(first.title) == QuestionText.cleanTitle(second.title) &&
        QuestionText.clean(first.body) == QuestionText.clean(second.body) &&
        KnowledgeCatalog.grade(first.grade) == KnowledgeCatalog.grade(second.grade) &&
        first.kind == second.kind && first.difficulty == second.difficulty &&
        KnowledgeCatalog.normalize(first.knowledge) == KnowledgeCatalog.normalize(second.knowledge)

    fun confirmed(draft: Question): Question {
        require(draft.body.isNotBlank()) { "请先填写题干，独立公式仅用于校对" }
        return draft.copy(reviewed = true)
    }
}
