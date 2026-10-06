package com.mathector.app

import com.mathector.app.data.*
import org.junit.Assert.*
import org.junit.Test

class ChoiceContentTest {
    @Test fun inlineOptionsPreserveTheirMathAndStem() {
        val parsed = ChoiceContent.split("求函数值。 A. \\(x=1\\) B. \\(x=2\\) C. \\(x=3\\) D. \\(x=4\\)")!!
        assertEquals("求函数值。", parsed.stem)
        assertEquals(listOf("A", "B", "C", "D"), parsed.options.map { it.label })
        assertEquals("\\(x=3\\)", parsed.options[2].value)
    }
    @Test fun fullWidthAndParenthesizedLabelsNormalizeWithoutLosingValues() {
        val parsed = ChoiceContent.split("选出正确值。\n（Ａ）0.5\n(B) -1\nＣ、(0,1)\nD：2")!!
        assertEquals(listOf("0.5", "-1", "(0,1)", "2"), parsed.options.map { it.value })
        assertEquals(listOf("A", "B", "C", "D"), parsed.options.map { it.label })
    }
    @Test fun labelsInsideMathAreIgnoredAndIncompleteChoicesStayUnchanged() {
        val body = "已知 \\(\\text{A. B. C. D.}\\)。\nA. 一\nB. 二\nC. 三\nD. 四"
        assertTrue(ChoiceContent.split(body)!!.stem.contains("\\text{A. B. C. D.}"))
        assertNull(ChoiceContent.split("A. 一 B. 二 D. 四"))
        assertNull(ChoiceContent.split("A. 一 B. 二 C. D. 四"))
    }
    @Test fun fourOptionsSpreadAcrossTheRowUsingTheirCombinedWidths() {
        val rows = ChoiceLayout.rows(listOf(370f, 20f, 20f, 20f))
        assertEquals(1, rows.size)
        assertEquals(listOf(0, 1, 2, 3), rows.single().map { it.index })
        val last = rows.single().last()
        assertEquals(515f, last.left + last.cellWidth, .01f)
        rows.single().zipWithNext().forEach { (a, b) -> assertTrue(b.left - a.left - a.contentWidth >= ChoiceLayout.GAP - .01f) }
    }
    @Test fun overflowingChoicesUseTwoAlignedColumnsInTwoRows() {
        val rows = ChoiceLayout.rows(listOf(180f, 170f, 185f, 190f))
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), rows.map { it.map(ChoiceLayout.Cell::index) })
        assertEquals(rows[0][1].left, rows[1][1].left, .01f)
        assertEquals(515f, rows.last().last().left + rows.last().last().cellWidth, .01f)
    }
}
