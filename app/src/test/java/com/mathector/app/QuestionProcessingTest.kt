package com.mathector.app

import com.mathector.app.data.Classification
import com.mathector.app.data.QuestionSplitter
import com.mathector.app.data.KnowledgeCatalog
import org.junit.Assert.*
import org.junit.Test

class QuestionProcessingTest {
    @Test fun splitKeepsSubquestionsWithTheirParent() {
        val questions = QuestionSplitter.split("1. 已知一次函数\n（1）求交点\n（2）求函数值\n2、求三角形面积")
        assertEquals(2, questions.size)
        assertTrue(questions[0].contains("（2）"))
    }
    @Test fun decimalDoesNotStartANewQuestion() {
        assertEquals(1, QuestionSplitter.split("1. 求值\n0.5x + 2 = 4").size)
    }
    @Test fun unknownGradeIsNotInvented() {
        assertEquals("", Classification.suggest("求两个数的和").grade)
        assertEquals("", Classification.suggest("已知一次函数").grade)
        assertEquals("高二", Classification.suggest("高二等差数列问题").grade)
    }
    @Test fun knowledgeCatalogIsBuiltInAndRejectsNonHighSchoolLabels() {
        assertTrue(KnowledgeCatalog.points.size >= 60)
        assertEquals(KnowledgeCatalog.points.size, KnowledgeCatalog.points.distinct().size)
        assertFalse(KnowledgeCatalog.points.any { it in listOf("勾股定理", "一次函数", "分数运算") })
        assertEquals(listOf("函数单调性", "等差数列"), KnowledgeCatalog.decode("函数单调性、勾股定理、等差数列、函数单调性"))
        assertEquals("", KnowledgeCatalog.grade("九年级"))
        assertEquals(listOf("高一", "高二", "高三"), KnowledgeCatalog.grades)
    }
    @Test fun aQuestionCanHaveMultipleHighSchoolKnowledgeSuggestions() {
        val labels = KnowledgeCatalog.decode(Classification.suggest("用导数研究函数单调性").knowledge)
        assertTrue(labels.contains("函数单调性"))
        assertTrue(labels.contains("导数概念与运算"))
    }
}
