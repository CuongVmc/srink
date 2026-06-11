package com.uy.shrinkspace

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

enum class Phase { Idle, Scanning, Scanned, Compressing, Done }

class ShrinkViewModel(app: Application) : AndroidViewModel(app) {
    var phase by mutableStateOf(Phase.Idle)
        private set
    var images by mutableStateOf<List<ImageItem>>(emptyList())
        private set
    var quality by mutableStateOf(60f)
    var maxDimension by mutableStateOf(1920)
    var progress by mutableStateOf(0)
        private set
    var savedBytes by mutableStateOf(0L)
        private set
    var compressedCount by mutableStateOf(0)
        private set
    var skippedCount by mutableStateOf(0)
        private set
    var originalsToDelete by mutableStateOf<List<Uri>>(emptyList())
        private set

    val totalBytes: Long get() = images.sumOf { it.sizeBytes }

    fun scan() {
        phase = Phase.Scanning
        viewModelScope.launch {
            images = MediaScanner.scanImages(getApplication())
            phase = Phase.Scanned
        }
    }

    fun compressAll() {
        phase = Phase.Compressing
        progress = 0
        savedBytes = 0
        compressedCount = 0
        skippedCount = 0
        val deletable = mutableListOf<Uri>()
        viewModelScope.launch {
            for ((index, item) in images.withIndex()) {
                val result = ImageCompressor.compress(
                    getApplication(), item, quality.toInt(), maxDimension
                )
                if (!result.skipped) {
                    val original = item.sizeBytes.takeIf { it > 0 } ?: result.newSizeBytes
                    savedBytes += (original - result.newSizeBytes).coerceAtLeast(0)
                    compressedCount++
                    deletable.add(item.uri)
                } else {
                    skippedCount++
                }
                progress = index + 1
            }
            originalsToDelete = deletable
            phase = Phase.Done
        }
    }

    fun clearDeleteList() {
        originalsToDelete = emptyList()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ShrinkScreen()
                }
            }
        }
    }
}

private val readPermission: String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun ShrinkScreen(vm: ShrinkViewModel = viewModel()) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, readPermission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) vm.scan()
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { vm.clearDeleteList() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("ShrinkSpace", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "Nén toàn bộ ảnh trong máy xuống dung lượng nhỏ nhất để giải phóng bộ nhớ.",
            style = MaterialTheme.typography.bodyMedium,
        )

        when (vm.phase) {
            Phase.Idle -> {
                Button(
                    onClick = {
                        if (hasPermission) vm.scan()
                        else permissionLauncher.launch(readPermission)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Quét ảnh trong máy") }
            }

            Phase.Scanning -> {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                Text("Đang quét ảnh...", Modifier.align(Alignment.CenterHorizontally))
            }

            Phase.Scanned -> {
                StatCard(
                    "Tìm thấy ${vm.images.size} ảnh",
                    "Tổng dung lượng: ${MediaScanner.formatBytes(vm.totalBytes)}",
                )

                Text("Chất lượng nén: ${vm.quality.toInt()}%")
                Slider(
                    value = vm.quality,
                    onValueChange = { vm.quality = it },
                    valueRange = 30f..90f,
                )
                Text(
                    "Thấp hơn = nhẹ hơn. 50–60% gần như không thấy khác biệt trên điện thoại.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Text("Kích thước tối đa (cạnh dài):")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1280, 1920, 2560).forEach { dim ->
                        FilterChip(
                            selected = vm.maxDimension == dim,
                            onClick = { vm.maxDimension = dim },
                            label = { Text("${dim}px") },
                        )
                    }
                }

                Button(
                    onClick = { vm.compressAll() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = vm.images.isNotEmpty(),
                ) { Text("Nén tất cả ${vm.images.size} ảnh") }
            }

            Phase.Compressing -> {
                val total = vm.images.size.coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { vm.progress / total.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Đang nén ${vm.progress}/$total ảnh...")
                Text("Đã tiết kiệm: ${MediaScanner.formatBytes(vm.savedBytes)}")
            }

            Phase.Done -> {
                StatCard(
                    "Xong! Nén được ${vm.compressedCount}/${vm.images.size} ảnh",
                    buildString {
                        append("Tiết kiệm: ${MediaScanner.formatBytes(vm.savedBytes)}\n")
                        append("Ảnh nén lưu ở album Pictures/ShrinkSpace")
                        if (vm.skippedCount > 0) {
                            append("\nBỏ qua ${vm.skippedCount} ảnh (đã tối ưu sẵn hoặc không đọc được)")
                        }
                    },
                )

                if (vm.originalsToDelete.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
                    Button(
                        onClick = {
                            val pending = MediaStore.createTrashRequest(
                                context.contentResolver,
                                vm.originalsToDelete,
                                true,
                            )
                            deleteLauncher.launch(
                                IntentSenderRequest.Builder(pending.intentSender).build()
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                    ) {
                        Text("Xoá ${vm.originalsToDelete.size} ảnh gốc để giải phóng bộ nhớ")
                    }
                    Text(
                        "Ảnh gốc sẽ vào Thùng rác 30 ngày trước khi xoá hẳn — an toàn nếu lỡ tay.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                OutlinedButton(
                    onClick = { vm.scan() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Quét lại") }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
