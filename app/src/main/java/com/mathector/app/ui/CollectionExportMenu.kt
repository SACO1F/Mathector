package com.mathector.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CollectionActionButton(label: String, description: String, icon: ImageVector, modifier: Modifier = Modifier,
    enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    FilledTonalButton(onClick, modifier, enabled = enabled, shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else Icon(icon, description, Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp)
    }
}

@Composable
internal fun CollectionExportMenu(id: String, enabled: Boolean, loading: Boolean, onExport: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CollectionActionButton("导出", "导出题集", Icons.Rounded.FileDownload, Modifier.testTag("collection-export-$id"), enabled, loading) { expanded = true }
        RoundedPopupMenu(expanded, { expanded = false }, Modifier.testTag("collection-export-menu-$id"), width = 208.dp) {
            RoundedMenuItem("导出 PDF", enabled = enabled, icon = Icons.Rounded.PictureAsPdf, modifier = Modifier.testTag("collection-export-pdf-$id"),
                onClick = { expanded = false; onExport(false) })
            RoundedMenuDivider()
            RoundedMenuItem("导出 Word", enabled = enabled, icon = Icons.Rounded.Description, modifier = Modifier.testTag("collection-export-word-$id"),
                onClick = { expanded = false; onExport(true) })
        }
    }
}
