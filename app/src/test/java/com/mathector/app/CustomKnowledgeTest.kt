package com.mathector.app

import com.mathector.app.data.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class CustomKnowledgeTest {
    private val original = KnowledgeCatalog.customPoints.value
    @After fun restore() = KnowledgeCatalog.installCustomPoints(original)

    @Test fun customNamesAreValidatedAndCannotDuplicateBuiltInsOrAliases() {
        KnowledgeCatalog.installCustomPoints(emptyList())
        assertEquals(CustomKnowledgePoint("函数", "函数图像平移"), KnowledgeCatalog.validateCustomPoint("函数", " 函数图像平移 "))
        listOf("", "对数函数", "二次函数", "a、b", "a,b", "a|b", "a\nb", "a\tb", "x".repeat(33)).forEach { name ->
            assertTrue("Invalid name: $name", runCatching { KnowledgeCatalog.validateCustomPoint("函数", name) }.isFailure)
        }
        assertTrue(runCatching { KnowledgeCatalog.validateCustomPoint("初中数学", "自定义") }.isFailure)
        assertTrue(runCatching { KnowledgeCatalog.validateCustomPoint("函数", "图像平移", listOf(CustomKnowledgePoint("三角", "图像平移"))) }.isFailure)
    }

    @Test fun registeredCustomNamesSurviveQuestionNormalizationAndClassificationFilters() {
        val point = CustomKnowledgePoint("函数", "函数图像平移")
        KnowledgeCatalog.installCustomPoints(listOf(point))
        assertEquals(listOf("函数图像平移", "函数单调性"), KnowledgeCatalog.decode("函数图像平移、函数单调性、函数图像平移、未注册名称"))
        val question = KnowledgeCatalog.normalize(Question(body = "求函数的图像。", grade = "高二", knowledge = "函数图像平移、未注册名称", difficulty = "进阶"))
        assertEquals(point.name, question.knowledge)
        assertTrue(QuestionFilter(grade = "高二", knowledge = point.name, difficulty = "进阶").matches(question))
        assertFalse(QuestionFilter(knowledge = "对数函数").matches(question))
        assertTrue(KnowledgeCatalog.groups.getValue("函数").contains(point.name))
        assertEquals(70, KnowledgeCatalog.builtInPoints.size)
        assertEquals(listOf("高一", "高二", "高三"), KnowledgeCatalog.grades)
    }

    @Test fun invalidOrDuplicateSavedEntriesDoNotCorruptTheTaxonomy() {
        KnowledgeCatalog.installCustomPoints(listOf(CustomKnowledgePoint("函数", " 图像平移 "), CustomKnowledgePoint("三角", "图像平移"),
            CustomKnowledgePoint("函数", "对数函数"), CustomKnowledgePoint("未知模块", "无效"), CustomKnowledgePoint("函数", "a,b")))
        assertEquals(listOf(CustomKnowledgePoint("函数", "图像平移")), KnowledgeCatalog.customPoints.value)
        assertEquals(71, KnowledgeCatalog.points.size)
        assertEquals(13, KnowledgeCatalog.groups.size)
        KnowledgeCatalog.installCustomPoints(emptyList())
        assertEquals("", KnowledgeCatalog.normalize("图像平移"))
    }
}
