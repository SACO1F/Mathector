package com.mathector.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathector.app.MathectorApplication
import com.mathector.app.data.CustomKnowledgePoint
import com.mathector.app.data.KnowledgeCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CustomKnowledgeDialog(initialGroup: String, onDismiss: () -> Unit, onAdded: (CustomKnowledgePoint) -> Unit) {
    val store = (LocalContext.current.applicationContext as MathectorApplication).knowledge
    val scope = rememberCoroutineScope()
    var group by rememberSaveable { mutableStateOf(initialGroup) }
    var name by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val submit = {
        if(!saving && name.isNotBlank()) {
            saving = true
            scope.launch {
                try {
                    val point = withContext(Dispatchers.IO) { store.add(group, name) }
                    onAdded(point)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "知识点保存失败，请重试" }
                finally { saving = false }
            }
        }
    }
    AlertDialog(onDismissRequest = { if(!saving) onDismiss() }, modifier = Modifier.testTag("custom-knowledge-dialog"),
        shape = RoundedCornerShape(28.dp), icon = { Icon(Icons.Rounded.AddCircleOutline, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("新增高中知识点", fontSize = 21.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("选择所属模块，新增后即可用于题目分类和筛选。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    OutlinedButton(onClick = { expanded = true }, enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("custom-knowledge-group"),
                        shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text("高中数学模块", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(group, fontSize = 15.sp)
                        }
                        Icon(Icons.Rounded.ExpandMore, null, Modifier.size(20.dp))
                    }
                    RoundedPopupMenu(expanded, { expanded = false }, Modifier.testTag("custom-knowledge-groups")) {
                        KnowledgeCatalog.builtInGroups.keys.forEach { module ->
                            RoundedMenuItem(module, selected = group == module, modifier = Modifier.testTag("custom-knowledge-group-$module"),
                                onClick = { group = module; expanded = false; error = null })
                        }
                    }
                }
                OutlinedTextField(name, { name = it; error = null }, modifier = Modifier.fillMaxWidth().testTag("custom-knowledge-name"),
                    label = { Text("知识点名称") }, singleLine = true, enabled = !saving, shape = RoundedCornerShape(14.dp), isError = error != null,
                    supportingText = { Text(error ?: "最多 32 个字符", modifier = Modifier.testTag("custom-knowledge-help"), fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }))
            }
        },
        confirmButton = { Button(onClick = submit, enabled = name.isNotBlank() && !saving, modifier = Modifier.testTag("custom-knowledge-save"), shape = RoundedCornerShape(14.dp)) {
            if(saving) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)) }
            Text("添加并选中")
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } })
}
