package com.mathector.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import com.mathector.app.data.ThemeMode
import com.mathector.app.data.Question
import com.mathector.app.data.QuestionCollection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun libraryFiltersAndPaperSettingsPersistWithValidation() {
        val app = ApplicationProvider.getApplicationContext<MathectorApplication>()
        val dao = app.database.dao()
        val q = Question(id = "paper-ui-${UUID.randomUUID()}", title = "试卷设置验证题", body = "已知函数。\n求最值。\n证明结论。", reviewed = true)
        val collection = QuestionCollection(title = "试卷设置验证-${UUID.randomUUID().toString().take(5)}", paperTitle = "数学练习")
        runBlocking { dao.save(q); dao.save(collection); dao.addToCollection(collection.id, q.id) }
        try {
            compose.onNodeWithTag("library-filter-全部").assertIsSelected()
            compose.onNodeWithTag("library-filter-待校对").performClick().assertIsSelected()
            compose.onNodeWithTag("library-filter-全部").performClick().assertIsSelected()
            screenSnapshot("library-filters-0.7.0.png")
            compose.onNodeWithText("题集", useUnmergedTree = true).performClick()
            compose.onNodeWithText(collection.title).performClick()
            compose.onNodeWithTag("collection-detail").performScrollToNode(hasTestTag("paper-settings"))
            compose.onNodeWithTag("paper-title").performTextReplacement("高二数学阶段测试")
            compose.onNodeWithTag("paper-instructions").performTextReplacement("请将答案写在答题卡上。\n请检查题号。")
            compose.onNodeWithTag("paper-minutes").performTextReplacement("0")
            compose.onNodeWithTag("export-pdf").assertIsNotEnabled()
            compose.onNodeWithTag("paper-minutes").performTextReplacement("120")
            compose.onNodeWithTag("paper-score").performTextReplacement("150")
            compose.onNodeWithTag("export-pdf").assertIsEnabled()
            compose.waitUntil(5000) { runBlocking { dao.collections().first().any { it.id == collection.id && it.paperTitle == "高二数学阶段测试" && it.examMinutes == 120 && it.totalScore == 150 && it.examInstructions.contains("请检查题号。") } } }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            compose.waitForIdle()
            screenSnapshot("paper-settings-0.7.0.png")
            compose.onNodeWithContentDescription("返回").assertIsDisplayed()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithText(collection.title).performClick()
            compose.onNodeWithTag("collection-detail").performScrollToNode(hasTestTag("paper-settings"))
            compose.onNodeWithTag("paper-title").assertTextContains("高二数学阶段测试")
            compose.onNodeWithTag("paper-minutes").assertTextContains("120")
            compose.onNodeWithTag("paper-score").assertTextContains("150")
        } finally { runBlocking { dao.deleteQuestion(q.id); dao.deleteCollection(collection.id) } }
    }

    @Test fun knowledgeListRemainsStillDuringRepeatedBottomFlings() {
        val app = ApplicationProvider.getApplicationContext<MathectorApplication>()
        val q = Question(id = "scroll-ui-${UUID.randomUUID()}", title = "知识库滚动验证", body = "求解。")
        runBlocking { app.database.dao().save(q) }
        try {
            compose.onNodeWithTag("library-list").performScrollToNode(hasText(q.title))
            compose.onNodeWithText(q.title).performClick()
            compose.onNodeWithTag("question-editor").performScrollToNode(hasTestTag("knowledge-tags"))
            compose.onNodeWithTag("add-knowledge").performClick()
            compose.onNodeWithTag("knowledge-list").performScrollToNode(hasTestTag("knowledge-数学探究"))
            repeat(3) { compose.onNodeWithTag("knowledge-list").performTouchInput { swipeUp(durationMillis = 170) } }
            compose.waitForIdle()
            val header = compose.onNodeWithText("高中数学知识库").fetchSemanticsNode().boundsInRoot.top
            val last = compose.onNodeWithTag("knowledge-数学探究").fetchSemanticsNode().boundsInRoot.top
            compose.mainClock.autoAdvance = false
            try {
                repeat(6) {
                    compose.onNodeWithTag("knowledge-list").performTouchInput { swipeUp(durationMillis = 170) }
                    repeat(20) {
                        compose.mainClock.advanceTimeByFrame()
                        assertEquals("Sheet must not respond to a list boundary fling", header, compose.onNodeWithText("高中数学知识库").fetchSemanticsNode().boundsInRoot.top, 1f)
                        assertEquals("Bottom item must not oscillate", last, compose.onNodeWithTag("knowledge-数学探究").fetchSemanticsNode().boundsInRoot.top, 1f)
                    }
                }
            } finally { compose.mainClock.autoAdvance = true }
            // Selecting a bottom tag must also keep the grid layout stable.
            compose.onNodeWithTag("knowledge-数学探究").performClick().assertIsSelected()
            assertEquals(last, compose.onNodeWithTag("knowledge-数学探究").fetchSemanticsNode().boundsInRoot.top, 1f)
            snapshot("knowledge-library-0.4.0.png")
            compose.onNodeWithText("完成").performClick()
        } finally { runBlocking { app.database.dao().deleteQuestion(q.id) } }
    }

    private fun snapshot(name: String) {
        val app = ApplicationProvider.getApplicationContext<MathectorApplication>()
        val folder = File(app.getExternalFilesDir(null), "validation").apply { mkdirs() }
        compose.waitForIdle()
        val image = compose.onNodeWithTag(if(name.startsWith("knowledge-library")) "knowledge-sheet" else "question-editor").captureToImage().asAndroidBitmap()
        try { File(folder, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { image.recycle() }
    }

    @Test fun highSchoolChoicesAndKnowledgeTagAddRemovePersist() {
        val app = ApplicationProvider.getApplicationContext<MathectorApplication>()
        val q = Question(id = "tag-ui-${UUID.randomUUID()}", title = "高中数学分类体验", body = "已知等差数列 a₁ = 2，公差 d = 3。求通项公式和前 10 项和。", latex = "a_n=a_1+(n-1)d", grade = "高一")
        runBlocking { app.database.dao().save(q) }
        try {
            compose.onNodeWithTag("library-list").performScrollToNode(hasText(q.title))
            compose.onNodeWithText(q.title).performClick()
            compose.onNodeWithTag("question-editor").performScrollToNode(hasTestTag("classification-年级"))
            compose.onNodeWithTag("choice-年级-高二").performClick().assertIsSelected()
            compose.onNodeWithTag("question-editor").performScrollToNode(hasTestTag("classification-题型"))
            compose.onNodeWithTag("choice-题型-解答题").performClick().assertIsSelected()
            compose.onNodeWithTag("question-editor").performScrollToNode(hasTestTag("classification-难度"))
            compose.onNodeWithTag("choice-难度-进阶").performClick().assertIsSelected()
            compose.onNodeWithTag("question-editor").performScrollToNode(hasTestTag("knowledge-tags"))
            compose.onNodeWithTag("add-knowledge").performClick()
            compose.onNodeWithTag("knowledge-search").performTextInput("数列")
            compose.onNodeWithTag("knowledge-等差数列").performClick().assertIsSelected()
            compose.onNodeWithTag("knowledge-数列求和").performClick().assertIsSelected()
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithTag("remove-knowledge-等差数列").performClick()
            compose.onNodeWithTag("remove-knowledge-等差数列").assertDoesNotExist()
            compose.onNodeWithTag("remove-knowledge-数列求和").assertExists()
            compose.onNodeWithTag("add-knowledge").performClick()
            compose.onNodeWithTag("knowledge-search").performTextInput("数列")
            compose.onNodeWithTag("knowledge-等差数列").performClick()
            compose.onNodeWithTag("knowledge-数列通项").performClick()
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithTag("question-editor").performScrollToNode(hasTestTag("classification-年级"))
            snapshot("classification-0.3.0.png")
            compose.onNodeWithTag("question-editor").performScrollToNode(hasTestTag("solution-panel"))
            compose.onNodeWithTag("auto-solve-question").assertIsOff()
            snapshot("auto-solve-0.3.0.png")
            compose.onNodeWithTag("question-editor").performScrollToNode(hasText("确认并保存题目"))
            compose.onNodeWithText("确认并保存题目").performClick()
            compose.waitUntil(5000) { runBlocking { app.database.dao().question(q.id)?.reviewed == true } }
            val saved = runBlocking { app.database.dao().question(q.id)!! }
            assertEquals("高二", saved.grade); assertEquals("进阶", saved.difficulty)
            assertEquals(listOf("数列求和", "等差数列", "数列通项"), com.mathector.app.data.KnowledgeCatalog.decode(saved.knowledge))
            compose.onNodeWithText(q.title).performClick()
            compose.onNodeWithTag("question-editor").performScrollToNode(hasTestTag("knowledge-tags"))
            compose.onNodeWithTag("remove-knowledge-等差数列").assertExists()
            compose.onNodeWithTag("add-knowledge").performClick()
            compose.onNodeWithText("高中数学知识库").assertExists()
            snapshot("knowledge-library-0.3.0.png")
            compose.onNodeWithText("完成").performClick()
        } finally { runBlocking { app.database.dao().deleteQuestion(q.id) } }
    }

    @Test fun themeSwitchPersistsAndFloatingNavigationStaysCompact() {
        val app = ApplicationProvider.getApplicationContext<MathectorApplication>()
        val original = app.settings.state.value.theme
        try {
            compose.onNodeWithTag("floating-navigation").assertHeightIsEqualTo(52.dp)
            compose.onNodeWithText("我的", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("recognition-multimodal").assertExists()
            compose.onNodeWithText("离线文字识别").assertDoesNotExist()
            compose.onNodeWithTag("theme-DARK").performClick()
            compose.waitUntil(5_000) { app.settings.state.value.theme == ThemeMode.DARK }
            compose.onNodeWithTag("theme-DARK").assertIsSelected()
            screenSnapshot("navigation-dark-0.6.0.png")
            assertEquals("DARK", app.getSharedPreferences("mathector-settings", 0).getString("theme", ""))
            compose.onNodeWithTag("theme-LIGHT").performClick()
            compose.waitUntil(5_000) { app.settings.state.value.theme == ThemeMode.LIGHT }
            compose.onNodeWithTag("theme-LIGHT").assertIsSelected()
            compose.onNodeWithTag("floating-navigation").assertHeightIsEqualTo(52.dp)
            screenSnapshot("navigation-light-0.6.0.png")
        } finally { app.settings.setTheme(original) }
    }

    private fun screenSnapshot(name: String) {
        val app = ApplicationProvider.getApplicationContext<MathectorApplication>()
        val folder = File(app.getExternalFilesDir(null), "validation").apply { mkdirs() }
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        try { File(folder, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { image.recycle() }
    }

    @Test fun libraryToCollectionAndBackWorks() {
        val app = ApplicationProvider.getApplicationContext<MathectorApplication>()
        val suffix = UUID.randomUUID().toString().take(6)
        val question = Question(id = "flow-test-$suffix", title = "函数页面验证-$suffix", body = "已知函数 f(x) = x²，求 f(3)。", grade = "高一", reviewed = true)
        val collectionTitle = "流程验证-$suffix"
        runBlocking { app.database.dao().save(question) }
        compose.waitUntil(10_000) { runBlocking { app.database.dao().question(question.id) != null } }
        try {
        compose.onNodeWithText("题集", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("创建题集").performClick()
        compose.onNode(hasSetTextAction()).performTextInput(collectionTitle)
        compose.onNodeWithText("创建", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(collectionTitle).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(collectionTitle).performClick()
        compose.onNodeWithText("添加题目").performClick()
        compose.onNodeWithTag("picker-search").performTextInput(question.title)
        compose.onNodeWithTag("picker-question-${question.id}").performClick()
        compose.onNodeWithTag("picker-confirm").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("1 道题 · 调整顺序后生成练习文档").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 道题 · 调整顺序后生成练习文档").assertExists()
        compose.onNodeWithText("导出 PDF").assertIsEnabled()
        compose.onNodeWithText(question.title).performClick()
        compose.onNodeWithText("题干与子题").performTextReplacement("编辑后立即返回的内容")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitUntil(10_000) {
            runBlocking { app.database.dao().question(question.id)?.body == "编辑后立即返回的内容" }
        }
        } finally {
            runBlocking {
                app.database.dao().deleteQuestion(question.id)
                app.database.dao().collections().first().filter { it.title == collectionTitle }.forEach { app.database.dao().deleteCollection(it.id) }
            }
        }
    }
}
