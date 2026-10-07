package com.mathector.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathector.app.data.*

/** One compact layout shared by the library, collections and question picker. */
@Composable internal fun QuestionSummaryCard(
    question: Question,
    tagPrefix: String,
    selected: Boolean? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    extraLabels: List<String> = emptyList(),
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed) .98f else 1f,
        spring(dampingRatio = .74f, stiffness = 450f), label = "question-press")
    val fill by animateColorAsState(
        if(selected == true) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else MaterialTheme.colorScheme.surface,
        tween(160), label = "question-fill")
    val shape = RoundedCornerShape(19.dp)
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val gradeColor = if(dark) Color(0xFF9AB7FF) else Color(0xFF285DCF)
    val knowledgeColor = if(dark) Color(0xFFCFB2FF) else Color(0xFF7653BE)
    val click = if(selected == null) Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
        else Modifier.toggleable(selected, interactionSource = interaction, indication = null, role = Role.Checkbox, onValueChange = { onClick() })
    Column(Modifier.fillMaxWidth().scale(scale).clip(shape).background(fill)
        .border(1.dp, if(selected == true) MaterialTheme.colorScheme.primary.copy(alpha = .6f) else MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), shape)
        .then(click).testTag("$tagPrefix-question-${question.id}").padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(Modifier.weight(1f).testTag("$tagPrefix-tags-${question.id}"),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val grade = KnowledgeCatalog.grade(question.grade)
                MetadataTag(grade.ifBlank { "未设置年级" }, if(grade.isBlank()) neutral else gradeColor, Modifier.testTag("$tagPrefix-grade-${question.id}"))
                val difficulty = question.displayDifficulty()
                val color = when(difficulty) {
                    "基础" -> if(dark) Color(0xFF8ED8B6) else Color(0xFF1D7652)
                    "进阶" -> if(dark) Color(0xFFFFC78C) else Color(0xFF986015)
                    "挑战" -> if(dark) Color(0xFFFFABA6) else Color(0xFFB3413C)
                    else -> neutral
                }
                MetadataTag(difficulty, color, Modifier.testTag("$tagPrefix-difficulty-${question.id}"))
                val points = KnowledgeCatalog.decode(question.knowledge)
                if(points.isEmpty()) MetadataTag("未标注知识点", neutral, Modifier.testTag("$tagPrefix-knowledge-${question.id}-unset"))
                else points.forEach { point -> MetadataTag(point, knowledgeColor, Modifier.testTag("$tagPrefix-knowledge-${question.id}-$point")) }
                QuestionReviewBadge(question.reviewed, Modifier.testTag("$tagPrefix-review-${question.id}"))
                extraLabels.forEach { label -> MetadataTag(label, neutral) }
            }
            if(selected != null) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(if(selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(1.5.dp, if(selected) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = .28f), CircleShape), contentAlignment = Alignment.Center) {
                    if(selected) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            } else trailing?.invoke()
        }
        val title = QuestionText.cleanTitle(question.title)
        val titleModifier = Modifier.fillMaxWidth().testTag("$tagPrefix-title-${question.id}")
        if(MathContent.parse(title).any { it is MathPart.Formula }) RichMathText(title, titleModifier, dark, preview = true, fontSize = 16f)
        else Text(title, titleModifier, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
        RichMathText(MathContent.preview(question).ifBlank { "题干待补充" }, Modifier.fillMaxWidth().testTag("$tagPrefix-math-${question.id}"), dark, preview = true, fontSize = 13f)
    }
}

@Composable internal fun QuestionReviewBadge(reviewed: Boolean, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val label = if(reviewed) "已校对" else "待校对"
    val tint = if(reviewed) {
        if(dark) Color(0xFF8ED8B6) else Color(0xFF1D7652)
    } else {
        if(dark) Color(0xFFFFC78C) else Color(0xFF986015)
    }
    MetadataTag(label, tint, modifier.semantics { contentDescription = "校对状态：$label" })
}

@Composable private fun MetadataTag(value: String, tint: Color, modifier: Modifier = Modifier) {
    Text(value, modifier.clip(RoundedCornerShape(6.dp)).background(tint.copy(alpha = .11f)).padding(horizontal = 6.dp, vertical = 3.dp),
        color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
}
