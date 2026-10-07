package com.mathector.app

import com.mathector.app.data.*
import org.junit.Assert.*
import org.junit.Test

class MathContentTest {
    @Test fun exportedBodyNeverAppendsIndependentFormulaEvenWhenLatexSpellingsDiffer() {
        val body = "函数 \\(f(x)=\\log_{0.5}(x^2-5x-6)\\) 的单调增区间是（ ）"
        val q = Question(body = body, latex = "f(x)=\\log_{0.5}\\left(x^{2}-5x-6\\right)")
        assertEquals(body, MathContent.question(q))
        assertEquals(MathContent.preview(q), MathContent.question(q))
        assertEquals(1, MathContent.parse(MathContent.question(q)).filterIsInstance<MathPart.Formula>().size)
        assertEquals("", MathContent.question(Question(latex = "x^2=3")))
    }
    @Test fun previewsKeepInlineMathWithoutAppendingTheIndependentFormula() {
        val q = Question(body = "已知 \\(f(x)=x^2-4x+3\\)，求最小值。", latex = "f(x)=(x-2)^2-1")
        assertEquals(q.body, MathContent.preview(q))
        assertFalse(MathContent.preview(q).contains("(x-2)"))
        assertEquals("", MathContent.preview(Question(latex = "x^2=3")))
    }
    @Test fun standalonePreviewAcceptsRawAndDelimitedLatexWithoutDoubleWrapping() {
        assertEquals("", MathContent.proofreadingFormula("  "))
        assertEquals("\\[\\frac{1}{2}\\]", MathContent.proofreadingFormula(" \\frac{1}{2} "))
        listOf("\\[x^2=3\\]", "\\(x^2=3\\)", "$" + "x^2=3$", "$$" + "x^2=3$$").forEach {
            assertEquals(it, MathContent.proofreadingFormula(it))
            assertEquals(1, MathContent.parse(MathContent.proofreadingFormula(it)).filterIsInstance<MathPart.Formula>().size)
        }
    }
    @Test fun numberingCleanupKeepsValuesOptionsAndSubquestionLines() {
        val input = "12. 已知函数。\n（1）求最值。\n(2) 求定义域。\n③证明结论。\n0.5 是系数。\n(0,1) 是区间。\nA. 1.25\nB. 2"
        assertEquals("已知函数。\n（1）求最值。\n（2）求定义域。\n（3）证明结论。\n0.5 是系数。\n(0,1) 是区间。\nA. 1.25\nB. 2", QuestionText.clean(input))
        assertEquals("求通项", QuestionText.clean("第 15 题：（1）求通项"))
        assertEquals("(1)+x=2", QuestionText.clean("(1)+x=2"))
        assertEquals("已知函数。\n（1）求最值。\n（2）证明结论。", QuestionText.clean("1. 已知函数。（1）求最值。；（2）证明结论。").replace("。；", "。"))
        assertEquals("已知函数", QuestionText.titleFromBody("1. 已知函数 \\(f(x)=\\frac{x^2}{2}\\)。\n（1）求值"))
    }
    @Test fun richMathPreservesTextAndBothDelimiterFamilies() {
        val parts = MathContent.parse("已知 \\(f(x)=\\frac{1}{2}x^2\\)，求值。\n\\[x=3\\]\n再求 $" + "a_n$。\n$$" + "S_n=\\sum_{i=1}^n a_i$$")
        assertEquals(4, parts.filterIsInstance<MathPart.Formula>().size)
        assertEquals(listOf(false, true, false, true), parts.filterIsInstance<MathPart.Formula>().map { it.display })
        assertTrue(parts.filterIsInstance<MathPart.Text>().joinToString("") { it.value }.contains("求值。"))
    }
    @Test fun legacyExpressionsBecomeLatexWithoutFormattingOrdinaryCounts() {
        val parts = MathContent.parse("已知 f(x) = x² − 4x + 3，首项 a₁=2，求前 10 项和。")
        val formulas = parts.filterIsInstance<MathPart.Formula>()
        assertEquals(2, formulas.size)
        assertEquals("f(x) = x^{2} - 4x + 3", formulas[0].latex)
        assertEquals("a_{1}=2", formulas[1].latex)
        assertTrue(parts.filterIsInstance<MathPart.Text>().any { it.value.contains("10 项") })
    }
    @Test fun supplementDoesNotDuplicateExistingFormulaButKeepsMissingMath() {
        assertEquals("已知 y=x²。", MathContent.withSupplement("已知 y=x²。", "y=x^2"))
        assertEquals("求解。\n\\[x^2=3\\]", MathContent.withSupplement("求解。", "x^2=3"))
        assertEquals(listOf(MathPart.Text("价格为 \\$5，待校对 $")), MathContent.parse("价格为 \\$5，待校对 $"))
    }
}
