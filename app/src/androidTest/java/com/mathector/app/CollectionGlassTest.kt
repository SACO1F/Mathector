package com.mathector.app

import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.mathector.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class CollectionGlassTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<MathectorApplication>()
    private val dao get() = app.database.dao()
    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun popupBoundsOnScreen(tag: String): Rect? {
        if(Build.VERSION.SDK_INT < 29) return null
        val local = bounds(tag)
        var origin = Offset.Zero
        compose.runOnUiThread {
            val focused = WindowInspector.getGlobalWindowViews().single { it.isShown && it.hasWindowFocus() }
            val location = IntArray(2)
            focused.getLocationOnScreen(location)
            origin = Offset(location[0].toFloat(), location[1].toFloat())
        }
        return local.translate(origin)
    }
    private fun webViews(view: View): List<WebView> = when(view) {
        is WebView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { webViews(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun previewsReady(dark: Boolean): Boolean {
        val latch = CountDownLatch(1)
        var valid = true
        val color = if(dark) "rgb(237, 241, 248)" else "rgb(24, 37, 57)"
        compose.runOnUiThread {
            val views = webViews(compose.activity.window.decorView).filter { it.isShown }
            val remaining = AtomicInteger(views.size)
            if(views.isEmpty()) latch.countDown()
            else views.forEach { view ->
                view.evaluateJavascript("document.fonts.status==='loaded' && getComputedStyle(document.body).color==='$color' && document.querySelectorAll('.katex-error').length===0") {
                    if(it != "true") valid = false
                    if(remaining.decrementAndGet() == 0) latch.countDown()
                }
            }
        }
        return latch.await(2, TimeUnit.SECONDS) && valid
    }
    private fun snapshot(name: String, dark: Boolean) {
        compose.waitUntil(15_000) { previewsReady(dark) }
        compose.waitForIdle()
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try { File(File(app.getExternalFilesDir(null), "validation").apply { mkdirs() }, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    @Test fun listReachesTheBottomWhileGlassAddStaysFixedAndFinalExportsRemainVisible() {
        val questions = (1..18).map { index -> Question(title = "函数与图像练习 $index", body = "已知函数 \\(f(x)=x^2+$index\\)，求 \\(f(2)\\) 的值，并说明图像的变化。",
            grade = listOf("高一", "高二", "高三")[index % 3], knowledge = "函数的概念与性质、函数单调性", difficulty = "进阶", reviewed = true) }
        val collection = QuestionCollection(title = "高中数学函数与导数阶段复习、错题整理和综合练习", paperTitle = "高中数学阶段练习", examInstructions = "请写出完整计算过程。", examMinutes = 90, totalScore = 100)
        val original = app.settings.state.value.theme
        runBlocking { questions.forEach { dao.save(it) }; dao.save(collection); dao.addQuestions(collection.id, questions.map { it.id }) }
        try {
            compose.onNodeWithTag("nav-collections").performClick()
            compose.onNodeWithTag("collection-card-${collection.id}").performClick()
            for(theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                val dark = theme == ThemeMode.DARK
                val label = if(dark) "dark" else "light"
                app.settings.setTheme(theme)
                compose.onNodeWithTag("collection-detail").performScrollToIndex(0)
                compose.onNodeWithTag("collection-header").assertIsDisplayed()
                compose.onNodeWithContentDescription("返回").assertIsDisplayed()
                val header = bounds("collection-header")
                val back = compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot
                assertEquals("The header stays at the page top", bounds("collection-screen").top, header.top, 1f)
                assertTrue("The scrolling list starts below the fixed header", bounds("collection-detail").top >= header.bottom)
                snapshot("collection-fixed-header-start-$label-0.18.0.png", dark)
                compose.onNodeWithTag("collection-add-floating").assertHeightIsEqualTo(52.dp)
                assertEquals("The list has no fixed bottom strip", bounds("collection-screen").bottom, bounds("collection-detail").bottom, 1f)
                val button = bounds("collection-add-floating")
                val screenBottom = bounds("collection-screen").bottom
                compose.runOnUiThread {
                    val nav = requireNotNull(ViewCompat.getRootWindowInsets(compose.activity.window.decorView)).getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                    assertTrue("Button avoids system navigation", button.bottom <= screenBottom - nav)
                    if(Build.VERSION.SDK_INT >= 29) assertFalse("System navigation has no contrast block", compose.activity.window.isNavigationBarContrastEnforced)
                }
                compose.onNodeWithTag("collection-detail").performScrollToIndex(4)
                snapshot("collection-glass-scrolling-$label-0.18.0.png", dark)
                assertEquals("Scrolling keeps the header fixed", header, bounds("collection-header"))
                compose.onNodeWithContentDescription("返回").assertIsDisplayed()
                assertEquals(back, compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot)
                assertEquals("The add button remains fixed while scrolling", button, bounds("collection-add-floating"))
                compose.onNodeWithTag("collection-detail").performScrollToIndex(questions.size + 1)
                repeat(2) { compose.onNodeWithTag("collection-detail").performTouchInput { swipeUp() } }
                compose.onNodeWithTag("collection-header").assertIsDisplayed()
                assertEquals("The header stays fixed at the list bottom", header, bounds("collection-header"))
                compose.onNodeWithContentDescription("返回").assertIsDisplayed()
                compose.onNodeWithTag("export-pdf").assertIsDisplayed()
                compose.onNodeWithTag("export-word").assertIsDisplayed()
                assertTrue("The final export row is clear of the floating button", bounds("export-word").bottom < bounds("collection-add-floating").top)
                snapshot("collection-glass-bottom-$label-0.18.0.png", dark)
            }
            compose.onNodeWithTag("collection-add-floating").performClick()
            compose.onNodeWithTag("question-picker").assertExists()
            compose.onNodeWithTag("picker-close").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("question-picker").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("collection-screen").assertExists()
            compose.onNodeWithContentDescription("返回").assertIsDisplayed().performClick()
            compose.onNodeWithTag("collections-list").assertIsDisplayed()
        } finally { app.settings.setTheme(original); runBlocking { dao.deleteCollection(collection.id); questions.forEach { dao.deleteQuestion(it.id) } } }
    }

    @Test fun mainGlassPlusOpensCaptureSheetOnEveryTabInBothThemes() {
        val questions = (1..12).map { Question(title = "数学练习 $it", body = "请说明判断过程，并写出完整的计算步骤。", grade = "高一", knowledge = "函数单调性", difficulty = "基础", reviewed = true) }
        val original = app.settings.state.value.theme
        runBlocking { questions.forEach { dao.save(it) } }
        try {
            for(theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                app.settings.setTheme(theme)
                for(tab in listOf("library", "collections", "profile")) {
                    compose.onNodeWithTag("nav-$tab").performClick()
                    compose.onNodeWithTag("main-add-floating").assertWidthIsEqualTo(50.dp).assertHeightIsEqualTo(50.dp).assertHasClickAction()
                    if(tab == "library") {
                        compose.onNodeWithTag("library-list").performScrollToIndex(3)
                        snapshot("main-glass-plus-${if(theme == ThemeMode.DARK) "dark" else "light"}-0.18.0.png", theme == ThemeMode.DARK)
                    }
                    compose.onNodeWithContentDescription("添加题目").performClick()
                    compose.onNodeWithText("收录一道好题").assertIsDisplayed()
                    compose.onNodeWithText("相册上传").assertExists()
                    compose.onNodeWithText("手动录入").assertExists()
                    Espresso.pressBack()
                    compose.waitUntil(5_000) { compose.onAllNodesWithText("收录一道好题").fetchSemanticsNodes().isEmpty() }
                    compose.onNodeWithTag("main-add-floating").assertIsDisplayed()
                }
            }
        } finally { app.settings.setTheme(original); runBlocking { questions.forEach { dao.deleteQuestion(it.id) } } }
    }

    @Test fun pickerRoundedFiltersKeepSelectionsAndDismissWithoutClosingSheetInBothThemes() {
        val suffix = java.util.UUID.randomUUID().toString().take(6)
        val points = KnowledgeCatalog.builtInPoints.take(10) + KnowledgeCatalog.builtInPoints.last()
        val questions = points.mapIndexed { index, point -> Question(title = "筛选练习-$suffix-$index", body = "请写出解答过程。", grade = "高二", knowledge = point, difficulty = "进阶", reviewed = true) }
        val collection = QuestionCollection(title = "圆角选题-$suffix")
        val original = app.settings.state.value.theme
        runBlocking { questions.forEach { dao.save(it) }; dao.save(collection) }
        try {
            compose.onNodeWithTag("nav-collections").performClick()
            compose.onNodeWithTag("collection-card-${collection.id}").performClick()
            compose.onNodeWithTag("collection-add-floating").performClick()
            compose.onNodeWithTag("picker-search").performTextInput(suffix)
            Espresso.closeSoftKeyboard()
            for(theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                app.settings.setTheme(theme)
                for((id, value) in listOf("grade" to "高二", "knowledge" to points.last(), "difficulty" to "进阶")) {
                    compose.onNodeWithTag("picker-filter-$id").performClick()
                    compose.onNodeWithTag("picker-menu-$id").assertIsDisplayed()
                    val menu = bounds("picker-menu-$id")
                    assertTrue("Popup fits the screen", menu.left >= 0 && menu.right <= compose.activity.resources.displayMetrics.widthPixels)
                    if(id == "knowledge" && Build.VERSION.SDK_INT >= 29) {
                        val margin = 16f * compose.activity.resources.displayMetrics.density
                        val onScreen = requireNotNull(popupBoundsOnScreen("picker-menu-$id"))
                        assertTrue("Wide knowledge popup keeps edge margins: $onScreen", onScreen.left >= margin && onScreen.right <= compose.activity.resources.displayMetrics.widthPixels - margin)
                    }
                    compose.onNodeWithTag("picker-option-$id-${if(theme == ThemeMode.LIGHT) "all" else value}").assertIsSelected()
                    compose.onNodeWithTag("picker-option-$id-$value").performScrollTo().performClick()
                    compose.onNodeWithTag("picker-menu-$id").assertDoesNotExist()
                    compose.onNodeWithTag("picker-filter-$id").performClick()
                    compose.onNodeWithTag("picker-option-$id-$value").performScrollTo().assertIsSelected()
                    snapshot("picker-rounded-$id-${if(theme == ThemeMode.DARK) "dark" else "light"}-0.18.0.png", theme == ThemeMode.DARK)
                    assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
                    compose.waitUntil(5_000) { compose.onAllNodesWithTag("picker-menu-$id").fetchSemanticsNodes().isEmpty() }
                    compose.onNodeWithTag("question-picker").assertExists()
                }
                compose.onNodeWithTag("picker-results").assertTextEquals("1 道可选")
                if(theme == ThemeMode.LIGHT) compose.onNodeWithTag("picker-question-${questions.last().id}").performClick().assertIsOn()
                compose.onNodeWithTag("picker-selected-count").assertTextEquals("已选 1 道")
            }
            compose.onNodeWithTag("picker-close").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("question-picker").fetchSemanticsNodes().isEmpty() }
            assertTrue("Cancelling keeps the collection unchanged", runBlocking { dao.orderedItems(collection.id).isEmpty() })
        } finally { app.settings.setTheme(original); runBlocking { dao.deleteCollection(collection.id); questions.forEach { dao.deleteQuestion(it.id) } } }
    }
}
