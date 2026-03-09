package com.ustc.vacancychecker.ui.tracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ustc.vacancychecker.data.model.TrackedCourse
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(
    onNavigateBack: () -> Unit,
    viewModel: TrackerViewModel = hiltViewModel()
) {
    val courses by viewModel.trackedCourses.collectAsState()
    var courseToDelete by remember { mutableStateOf<TrackedCourse?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("后台跟踪队列") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        android.widget.Toast.makeText(context, "正在后台刷新选课余量，请稍候...", android.widget.Toast.LENGTH_SHORT).show()
                        viewModel.refreshAll(context) 
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "立即刷新"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (courses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "当前没有正在跟踪的课程",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(courses, key = { it.courseId }) { course ->
                    TrackedLineItem(
                        course = course,
                        onToggle = { isMonitoring ->
                            viewModel.toggleMonitoring(course.courseId, isMonitoring)
                        },
                        onDelete = {
                            courseToDelete = course
                        }
                    )
                }
            }
        }
    }

    if (courseToDelete != null) {
        AlertDialog(
            onDismissRequest = { courseToDelete = null },
            title = { Text("确认删除跟踪") },
            text = { Text("确定要取消对 [${courseToDelete?.courseName}] (${courseToDelete?.courseId}) 的后台自动跟踪吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        courseToDelete?.courseId?.let { viewModel.removeCourse(it) }
                        courseToDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { courseToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun TrackedLineItem(
    course: TrackedCourse,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = course.courseName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "课堂号: ${course.courseId} • 教师: ${course.teacher.ifBlank { "未知" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = course.isMonitoring,
                    onCheckedChange = { onToggle(it) }
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val statusText = if (course.vacancy > 0) "有空位: ${course.vacancy}" else "暂无空位"
                    val statusColor = if (course.vacancy > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

                    Text(
                        text = "状态: $statusText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor
                    )
                    
                    val timeStr = if (course.lastCheckTime > 0) {
                        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(course.lastCheckTime))
                    } else {
                        "尚未检测"
                    }
                    Text(
                        text = "最近检测: $timeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除该次跟踪",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
