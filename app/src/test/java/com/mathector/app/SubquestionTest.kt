package com.mathector.app

import com.mathector.app.data.QuestionText
import org.junit.Assert.*
import org.junit.Test

class SubquestionTest {
    @Test fun originalSubquestionNumbersBecomeContiguousAndCleaningIsIdempotent() {
        val raw = "12. 已知函数。\n（3）求最值。\n继续说明计算过程。\n(7)证明结论。"
        val expected = "已知函数。\n（1）求最值。\n继续说明计算过程。\n（2）证明结论。"
        assertEquals(expected, QuestionText.clean(raw))
        assertEquals(expected, QuestionText.clean(expected))
        assertEquals("函数题", QuestionText.cleanTitle("第12题：（3）函数题"))
    }
    @Test fun explicitMathAndChoiceMarkersCannotBecomeSubquestionNumbers() {
        val raw = "5. 已知函数。\n\\[\n(1)+x=2; (2)+y=3\n\\]\nA. 0.5\nB. (0,1)\n（4）求值。\n（8）证明结论。"
        val expected = raw.removePrefix("5. ").replace("（4）", "（1）").replace("（8）", "（2）")
        assertEquals(expected, QuestionText.clean(raw))
    }
    @Test fun legacySeparateTaskLinesAreNumberedButOrdinaryExplanationsAreNot() {
        assertEquals("已知函数。\n（1）求最值。\n（2）证明结论。", QuestionText.clean("已知函数。\n求最值。\n证明结论。"))
        val description = "已知函数。\n其中系数大于零。\n函数的定义域是实数集。\n求最值。"
        assertEquals(description, QuestionText.clean(description))
    }
    @Test fun structuredPartsPreserveOrderAndRestartForEachQuestion() {
        val expected = "已知数列。\n（1）求通项。\n（2）证明结论。"
        repeat(2) { assertEquals(expected, QuestionText.withSubquestions("9. 已知数列。", listOf("（3）求通项。", "（6）证明结论。"))) }
        assertEquals("已知数列。\n求通项。", QuestionText.withSubquestions("已知数列。", listOf("求通项。")))
    }
}
