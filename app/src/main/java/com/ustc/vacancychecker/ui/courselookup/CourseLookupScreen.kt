package com.ustc.vacancychecker.ui.courselookup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseLookupScreen(
    onNavigateBack: () -> Unit,
    onCourseSelected: (String) -> Unit,
    viewModel: CourseLookupViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState

    if (uiState.showWebView) {
        WebViewCourseLookupScreen(
            keyword = uiState.keyword,
            searchType = uiState.searchType,
            onSearchResults = { json -> viewModel.onSearchResults(json) },
            onSearchError = { msg -> viewModel.onSearchError(msg) }
        )
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.showSuccessMessage) {
        uiState.showSuccessMessage?.let { msg ->
            scope.launch {
                snackbarHostState.showSnackbar(msg)
                viewModel.clearSuccessMessage()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (uiState.selectedForTracking.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.addToTracking() },
                    icon = { Icon(Icons.Filled.Search, contentDescription = null) }, // or another icon
                    text = { Text("加入跟踪 (${uiState.selectedForTracking.size})") }
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("查找课堂号") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 搜索类型选择
            val tabs = listOf("课程名 / 编号", "授课教师")
            val selectedTabIndex = if (uiState.searchType == SearchType.COURSE) 0 else 1

            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = {
                            viewModel.updateSearchType(if (index == 0) SearchType.COURSE else SearchType.TEACHER)
                        },
                        text = { Text(title) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 搜索栏：关键字输入框 + 搜索按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.keyword,
                    onValueChange = { viewModel.updateKeyword(it) },
                    label = { Text("搜索关键字") },
                    placeholder = { 
                        Text(if (uiState.searchType == SearchType.COURSE) "输入课程名或编号" else "输入教师名") 
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        autoCorrect = false,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { viewModel.startSearch() }
                    ),
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = { viewModel.startSearch() },
                    enabled = !uiState.isSearching && uiState.keyword.isNotBlank()
                ) {
                    if (uiState.isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("搜索")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 警告信息
            uiState.warningMessage?.let { warning ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = warning,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 搜索结果提示
            if (uiState.results.isNotEmpty()) {
                Text(
                    text = "找到 ${uiState.results.size} 门课程，点击选择：",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 搜索结果列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp) // Leave space for FAB
            ) {
                items(uiState.results) { course ->
                    CourseResultItem(
                        course = course,
                        isSelected = uiState.selectedForTracking.contains(course.classCode),
                        onToggleSelection = { viewModel.toggleSelection(course.classCode) },
                        onClick = {
                            onCourseSelected(course.classCode)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseResultItem(
    course: CourseInfo,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(16.dp)
            ) {
                // 课程名
                Text(
                    text = course.courseName.ifBlank { "（未知课程名）" }.replace("\n", " ").replace(Regex("\\s+"), " ").trim(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))

                // 课堂号 + 教师
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (course.classCode.isNotBlank()) {
                        Text(
                            text = "课堂号: ${course.classCode}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Spacer(modifier = Modifier)
                    }
                    if (course.teacher.isNotBlank()) {
                        Text(
                            text = "教师: ${course.teacher}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() },
                modifier = Modifier.padding(end = 16.dp)
            )
        }
    }
}
