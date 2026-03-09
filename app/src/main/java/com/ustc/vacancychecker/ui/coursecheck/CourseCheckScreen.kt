package com.ustc.vacancychecker.ui.coursecheck

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseCheckScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToCourseLookup: () -> Unit = {},
    onNavigateToTracker: () -> Unit = {},
    selectedClassCode: String? = null,
    onSelectedClassCodeConsumed: () -> Unit = {},
    viewModel: CourseCheckViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState

    // 接收从 CourseLookup 页面返回的课堂号
    LaunchedEffect(selectedClassCode) {
        if (!selectedClassCode.isNullOrBlank()) {
            viewModel.updateClassCode(selectedClassCode)
            viewModel.addCourseToTrack(selectedClassCode)
            onSelectedClassCodeConsumed()
        }
    }
    
    val context = LocalContext.current
    LaunchedEffect(uiState.showSuccessMessage) {
        uiState.showSuccessMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearSuccessMessage()
        }
    }
    
    // 如果正在查询，显示 WebView
    if (uiState.showWebView) {
        WebViewCourseCheckScreen(
            classCode = uiState.classCode,
            credentials = viewModel.getCredentials(),
            onNotInSelectTime = { viewModel.onNotInSelectTime() },
            onCourseNotFound = { viewModel.onCourseNotFound() },
            onVacancyResult = { stdCount, limitCount, courseName, teacher ->
                viewModel.onVacancyResult(stdCount, limitCount, courseName, teacher)
            }
        )
        return
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("USTC 选课余量检测") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = onNavigateToTracker) {
                        Icon(
                            imageVector = Icons.Filled.List,
                            contentDescription = "跟踪列表"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "设置"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 课堂号输入框
            OutlinedTextField(
                value = uiState.classCode,
                onValueChange = { viewModel.updateClassCode(it) },
                label = { Text("课堂号") },
                placeholder = { Text("请输入要查询的课堂号") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { viewModel.startCheck() }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 查找课堂号按钮
            OutlinedButton(
                onClick = onNavigateToCourseLookup,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ManageSearch,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("按关键字查找课堂号")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 开始检测按钮
            Button(
                onClick = { viewModel.startCheck() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !uiState.isChecking && uiState.classCode.isNotBlank()
            ) {
                if (uiState.isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("开始检测")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            // 跟踪的课程列表
            if (uiState.trackedCourses.isNotEmpty() && !uiState.isChecking) {
                Text(
                    text = "已跟踪的课程",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.trackedCourses) { code ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            onClick = { viewModel.startCheck(code) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "课堂号: $code",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                IconButton(onClick = { viewModel.removeTrackedCourse(code) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "取消跟踪")
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 错误信息
            uiState.errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            
            // 查询结果
            uiState.result?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.hasVacancy) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (result.hasVacancy) "✅ 有空余名额，可以选课" else "❌ 名额已满，无法选课",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (result.hasVacancy) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "已选 ${result.stdCount} / 上限 ${result.limitCount}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (result.hasVacancy) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedButton(
                            onClick = { viewModel.addToBackgroundTracking() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (result.hasVacancy) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("加入后台跟踪队列")
                        }
                    }
                }
            }
        }
    }
}

