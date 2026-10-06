package com.mathector.app.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable fun PdfPreview(file: File, onBack: () -> Unit) {
    val pageCount by produceState(0, file) { value = withContext(Dispatchers.IO) { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } } } }
    Column {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回题集") }
            Column { Text("PDF 分页预览"); Text("$pageCount 页 · 与保存文件一致", style = MaterialTheme.typography.labelSmall) }
        }
        LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(pageCount) { index ->
                val image by produceState<Bitmap?>(null, file, index) {
                    value = withContext(Dispatchers.IO) {
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { renderer -> renderer.openPage(index).use { page ->
                            val width = 1100; val height = (width.toFloat() * page.height / page.width).toInt()
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap -> bitmap.eraseColor(AndroidColor.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                        } } }
                    }
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("第 ${index + 1} 页", style = MaterialTheme.typography.labelSmall)
                    image?.let { Image(it.asImageBitmap(), "题集第 ${index+1} 页", Modifier.fillMaxWidth().background(Color.White)) }
                        ?: Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
    }
}
