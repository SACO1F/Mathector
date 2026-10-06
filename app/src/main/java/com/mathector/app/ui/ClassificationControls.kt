package com.mathector.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathector.app.data.KnowledgeCatalog

@Composable internal fun ClassificationChoice(title: String, value: String, options: List<String>, segmented: Boolean = false, onSelect: (String) -> Unit) {
    Surface(Modifier.fillMaxWidth().testTag("classification-$title"), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text(if(value in options) value else if(title == "年级") "选择学习年级" else "尚未评估", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            if (segmented) SlidingChoices(title, value, options, onSelect)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option -> AnimatedTag(option, value == option, { onSelect(option) }, Modifier.testTag("choice-$title-$option"), showIndicator = false) }
            }
        }
    }
}

@Composable private fun SlidingChoices(title: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    val background = MaterialTheme.colorScheme.background
    BoxWithConstraints(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(background).padding(3.dp)) {
        val cell = maxWidth / options.size
        val index = options.indexOf(value)
        val position by animateDpAsState(cell * index.coerceAtLeast(0), spring(dampingRatio = .78f, stiffness = 380f), label = "selection-position")
        if(index >= 0) Box(Modifier.offset(x = position).width(cell).fillMaxHeight().clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .13f)))
        Row(Modifier.fillMaxSize()) {
            options.forEach { option ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(if(pressed) .95f else 1f, spring(dampingRatio = .7f, stiffness = 500f), label = "choice-press")
                val color by animateColorAsState(if(value == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, tween(180), label = "choice-text")
                Box(Modifier.weight(1f).fillMaxHeight().scale(scale).clip(RoundedCornerShape(13.dp))
                    .selectable(value == option, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = { onSelect(option) })
                    .testTag("choice-$title-$option"), contentAlignment = Alignment.Center) {
                    Text(option, color = color, fontWeight = if(value == option) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable internal fun AnimatedTag(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, remove: Boolean = false, showIndicator: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed) .94f else 1f, spring(dampingRatio = .68f, stiffness = 450f), label = "tag-press")
    val tint by animateColorAsState(if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, tween(180), label = "tag-tint")
    val fill by animateColorAsState(if(selected) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else MaterialTheme.colorScheme.background, tween(180), label = "tag-fill")
    Row(modifier.scale(scale).heightIn(min = 48.dp).clip(RoundedCornerShape(15.dp)).background(fill)
        .border(1.dp, if(selected) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else Color.Transparent, RoundedCornerShape(15.dp))
        .selectable(selected, interactionSource = interaction, indication = null, role = if(remove) Role.Button else Role.Checkbox, onClick = onClick)
        .padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        // Reserve the indicator width so selection never reflows the scrollable tag grid.
        if(showIndicator) Box(Modifier.size(14.dp)) { if(remove || selected) Icon(if(remove) Icons.Rounded.Close else Icons.Rounded.Check, if(remove) "移除知识点 $label" else null, Modifier.size(14.dp), tint = tint) }
    }
}

@Composable internal fun KnowledgeTags(value: String, onChange: (String) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    val selected = KnowledgeCatalog.decode(value)
    Surface(Modifier.fillMaxWidth().testTag("knowledge-tags"), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp).animateContentSize(spring(dampingRatio = .85f)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("知识点", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${selected.size} 个标签", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(selected.isEmpty()) Text("从高中知识库添加，可选择多个", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selected.forEach { point -> key(point) { AnimatedTag(point, true, { onChange(KnowledgeCatalog.encode(selected - point)) }, Modifier.testTag("remove-knowledge-$point"), remove = true) } }
                AnimatedTag("添加知识点 +", false, { choosing = true }, Modifier.testTag("add-knowledge"))
            }
        }
    }
    if(choosing) KnowledgeSheet(selected, onToggle = { point -> onChange(KnowledgeCatalog.encode(if(point in selected) selected - point else selected + point)) }, onClose = { choosing = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun KnowledgeSheet(selected: List<String>, onToggle: (String) -> Unit, onClose: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("全部") }
    var adding by remember { mutableStateOf(false) }
    val custom by KnowledgeCatalog.customPoints.collectAsStateWithLifecycle()
    val groups = remember(custom) { KnowledgeCatalog.groups }
    val matches = groups.filterKeys { group == "全部" || it == group }.mapValues { (_, values) -> values.filter { it.contains(search, true) } }.filterValues { it.isNotEmpty() }
    val viewport = LocalConfiguration.current.screenHeightDp.dp * .84f
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), sheetGesturesEnabled = false, dragHandle = null, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().height(viewport).imePadding().padding(horizontal = 22.dp).padding(top = 20.dp, bottom = 12.dp).testTag("knowledge-sheet"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("高中数学知识库", fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("内置 ${KnowledgeCatalog.builtInPoints.size} 个 · 自定义 ${custom.size} 个 · 已选 ${selected.size} 个", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = { adding = true }, modifier = Modifier.testTag("new-knowledge")) { Icon(Icons.Rounded.AddCircleOutline, "新增知识点", tint = MaterialTheme.colorScheme.primary) }
                TextButton(onClick = onClose) { Text("完成") }
            }
            OutlinedTextField(search, { search = it }, placeholder = { Text("搜索知识点") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("knowledge-search"), shape = RoundedCornerShape(16.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("全部") + groups.keys.toList()) { label -> AnimatedTag(label, group == label, { group = label }) } }
            LazyColumn(Modifier.weight(1f).testTag("knowledge-list"), overscrollEffect = null, verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                if(matches.isEmpty()) item { Text("没有匹配的高中知识点", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                matches.forEach { (heading, values) -> item(key = heading) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(heading, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            values.forEach { point -> AnimatedTag(point, point in selected, { onToggle(point) }, Modifier.testTag("knowledge-$point")) }
                        }
                    }
                } }
            }
        }
    }
    if(adding) CustomKnowledgeDialog(group.takeIf { it in groups } ?: "函数", onDismiss = { adding = false }, onAdded = { point ->
        group = point.group
        search = point.name
        onToggle(point.name)
        adding = false
    })
}
