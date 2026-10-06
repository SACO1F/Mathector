package com.mathector.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathector.app.data.*
import kotlinx.coroutines.delay

@Composable internal fun PaperSettingsEditor(collection: QuestionCollection, exporting: Boolean, canExport: Boolean,
    onChange: (PaperSettings) -> Unit, onExport: (Boolean, PaperSettings) -> Unit) {
    var title by rememberSaveable(collection.id) { mutableStateOf(collection.paperTitle) }
    var instructions by rememberSaveable(collection.id) { mutableStateOf(collection.examInstructions) }
    var minutes by rememberSaveable(collection.id) { mutableStateOf(collection.examMinutes.takeIf { it > 0 }?.toString().orEmpty()) }
    var score by rememberSaveable(collection.id) { mutableStateOf(collection.totalScore.takeIf { it > 0 }?.toString().orEmpty()) }
    val minuteError = minutes.isNotBlank() && (minutes.toIntOrNull() ?: -1) !in 1..999
    val scoreError = score.isNotBlank() && (score.toIntOrNull() ?: -1) !in 1..9999
    val titleError = title.trim().length > 80
    val instructionError = instructions.trim().length > 600
    val paper = if(minuteError || scoreError || titleError || instructionError) null else
        PaperSettings(title.trim(), instructions.trim(), minutes.toIntOrNull() ?: 0, score.toIntOrNull() ?: 0)
    val latest by rememberUpdatedState(paper)
    val latestChange by rememberUpdatedState(onChange)
    LaunchedEffect(paper) { if(paper != null && paper != collection.paperSettings()) { delay(350); latestChange(paper) } }
    DisposableEffect(collection.id) { onDispose { latest?.let(latestChange) } }
    Surface(Modifier.fillMaxWidth().testTag("paper-settings"), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("文档设置", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("自动保存 · 留空不显示", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(title, { title = it.take(120) }, Modifier.fillMaxWidth().testTag("paper-title"), label = { Text("试卷主标题") },
                enabled = !exporting, isError = titleError, maxLines = 3, shape = RoundedCornerShape(12.dp), textStyle = TextStyle(fontSize = 14.sp),
                supportingText = if(titleError) ({ Text("最多 80 字", fontSize = 11.sp) }) else null)
            OutlinedTextField(instructions, { instructions = it.take(700) }, Modifier.fillMaxWidth().testTag("paper-instructions"), label = { Text("考试说明") },
                enabled = !exporting, isError = instructionError, maxLines = 4, shape = RoundedCornerShape(12.dp), textStyle = TextStyle(fontSize = 14.sp),
                supportingText = if(instructionError) ({ Text("最多 600 字", fontSize = 11.sp) }) else null)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(minutes, { minutes = it.take(4) }, Modifier.weight(1f).testTag("paper-minutes"), label = { Text("考试时间") },
                    enabled = !exporting, singleLine = true, isError = minuteError, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp), textStyle = TextStyle(fontSize = 14.sp), suffix = { Text("分钟", fontSize = 12.sp) },
                    supportingText = if(minuteError) ({ Text("填写 1-999", fontSize = 11.sp) }) else null)
                OutlinedTextField(score, { score = it.take(5) }, Modifier.weight(1f).testTag("paper-score"), label = { Text("满分") },
                    enabled = !exporting, singleLine = true, isError = scoreError, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp), textStyle = TextStyle(fontSize = 14.sp), suffix = { Text("分", fontSize = 12.sp) },
                    supportingText = if(scoreError) ({ Text("填写 1-9999", fontSize = 11.sp) }) else null)
            }
            Text("A4 · 顺序编号 · 小题（1）（2） · LaTeX 公式", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = canExport && !exporting && paper != null, onClick = { paper?.let { onExport(false, it) } }, modifier = Modifier.weight(1f).testTag("export-pdf")) { Text("导出 PDF") }
                OutlinedButton(enabled = canExport && !exporting && paper != null, onClick = { paper?.let { onExport(true, it) } }, modifier = Modifier.weight(1f).testTag("export-word")) { Text("导出 Word") }
            }
            if(exporting) Row { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("正在排版与渲染公式…", Modifier.padding(start = 10.dp), fontSize = 12.sp) }
        }
    }
}
