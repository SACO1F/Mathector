package com.mathector.app

import com.mathector.app.data.PaperSettings
import org.junit.Assert.*
import org.junit.Test

class PaperSettingsTest {
    @Test fun blankFieldsProduceNoHeaderAndFilledFieldsKeepTheirUnits() {
        assertEquals("", PaperSettings().summary)
        assertEquals("考试时间：120 分钟    满分：150 分", PaperSettings(minutes = 120, totalScore = 150).summary)
        assertEquals("满分：100 分", PaperSettings(totalScore = 100).summary)
        assertEquals("考试时间：90 分钟", PaperSettings(minutes = 90).summary)
    }
    @Test fun validationTrimsEdgesAndPreservesMultilineInstructions() {
        val value = PaperSettings(" 高二数学阶段测试 ", " 答案写在答题卡上。\n请检查题号。 ", 120, 150).validated()
        assertEquals("高二数学阶段测试", value.title)
        assertEquals("答案写在答题卡上。\n请检查题号。", value.instructions)
    }
    @Test fun invalidBoundsCannotReachAnExporter() {
        listOf(PaperSettings(title = "题".repeat(81)), PaperSettings(instructions = "字".repeat(601)),
            PaperSettings(minutes = -1), PaperSettings(minutes = 1000), PaperSettings(totalScore = -1), PaperSettings(totalScore = 10000)
        ).forEach { value ->
            try { value.validated(); fail("Invalid paper settings must fail") } catch (_: IllegalArgumentException) { }
        }
        PaperSettings(title = "题".repeat(80), instructions = "字".repeat(600), minutes = 999, totalScore = 9999).validated()
    }
}
