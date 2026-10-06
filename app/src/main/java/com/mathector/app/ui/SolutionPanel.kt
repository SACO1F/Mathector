package com.mathector.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import com.mathector.app.data.Question
import com.mathector.app.data.contentFingerprint
import com.mathector.app.data.MathContent

@Composable internal fun SolutionPanel(question: Question, enabled: Boolean, onEnabled: (Boolean) -> Unit, state: WorkInfo.State?, dark: Boolean,
    onGenerate: () -> Unit, onCancel: () -> Unit) {
    Surface(Modifier.fillMaxWidth().testTag("solution-panel"), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) { Text("自动解题", fontWeight = FontWeight.SemiBold); Text("开启后，修改题干也会重新生成", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(enabled, onEnabled, modifier = Modifier.testTag("auto-solve-question"))
            }
            Text("使用已配置的模型生成答案与步骤，解答需自行核对。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if(state != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(if(state == WorkInfo.State.RUNNING) "正在生成解答…" else "等待网络与后台任务…", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text("取消解题") }
                }
            } else {
                FilledTonalButton(onClick = onGenerate, enabled = question.body.isNotBlank() || question.latex.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("generate-solution"), shape = RoundedCornerShape(14.dp)) {
                    Text(if(question.solution.isNotBlank()) "重新生成解答" else if(question.solutionError.isNotBlank()) "重试解题" else "生成解答")
                }
            }
            if(question.solutionError.isNotBlank()) Text(question.solutionError, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("solution-error"))
            if(question.solution.isNotBlank() && question.solutionFingerprint == question.contentFingerprint()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                Text("AI 解答 · 待核对", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                RichMathText(MathContent.withSupplement(question.solution, question.solutionLatex), Modifier.fillMaxWidth().testTag("solution-result"), dark)
            }
        }
    }
}
