package com.ustc.vacancychecker.ui.tracker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.ustc.vacancychecker.data.model.SelectedCourseBehavior
import com.ustc.vacancychecker.data.model.TrackedCourse
import com.ustc.vacancychecker.data.model.CourseOrderEditor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(
    onNavigateBack: () -> Unit,
    viewModel: TrackerViewModel = hiltViewModel()
) {
    val courses by viewModel.trackedCourses.collectAsState()
    val isReconcilingGroups by viewModel.isReconcilingGroups.collectAsState()
    val groups = remember(courses) {
        courses.groupBy { it.trackingGroupId }.values.map { group ->
            group.sortedWith(compareBy<TrackedCourse> { it.priority ?: courses.indexOf(it) })
        }
    }
    var courseToDelete by remember { mutableStateOf<TrackedCourse?>(null) }
    var groupToDelete by remember { mutableStateOf<List<TrackedCourse>?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("后台跟踪队列") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        android.widget.Toast.makeText(context, "正在后台刷新选课余量，请稍候...", android.widget.Toast.LENGTH_SHORT).show()
                        viewModel.refreshAll(context)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "立即刷新")
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
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (isReconcilingGroups) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("正在检测历史课堂分组…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("当前没有正在跟踪的课程", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (isReconcilingGroups) {
                    item(key = "legacy-group-reconciliation") {
                        Column(Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "正在识别并合并历史备选课堂…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(groups, key = { it.first().trackingGroupId }) { group ->
                    CourseGroupCard(
                        courses = group,
                        onGroupToggle = { viewModel.toggleGroupMonitoring(group.first().trackingGroupId, it) },
                        onBehaviorChange = { viewModel.setGroupBehavior(group.first().trackingGroupId, it) },
                        onDeleteGroup = { groupToDelete = group },
                        onToggleCourse = { course, enabled -> viewModel.toggleMonitoring(course.courseId, enabled) },
                        onAutoSelectToggle = { course, enabled -> viewModel.toggleAutoSelect(course.courseId, enabled) },
                        onClearMessage = { viewModel.clearSelectMessage(it.courseId) },
                        onDeleteCourse = { courseToDelete = it },
                        onPersistOrder = { groupId, orderedCourseIds ->
                            viewModel.setCourseOrder(groupId, orderedCourseIds)
                        }
                    )
                }
            }
        }
    }

    courseToDelete?.let { course ->
        AlertDialog(
            onDismissRequest = { courseToDelete = null },
            title = { Text("确认删除课堂跟踪") },
            text = { Text("确定要删除 [${course.courseName}] (${course.courseId}) 的跟踪吗？同组其他课堂不会受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeCourse(course.courseId)
                    courseToDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { courseToDelete = null }) { Text("取消") } }
        )
    }

    groupToDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text("确认删除课程组") },
            text = { Text("将删除 [${group.first().courseName}] 的 ${group.size} 个备选课堂跟踪，其他课程组不会受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeGroup(group.first().trackingGroupId)
                    groupToDelete = null
                }) { Text("删除整组", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { groupToDelete = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun CourseGroupCard(
    courses: List<TrackedCourse>,
    onGroupToggle: (Boolean) -> Unit,
    onBehaviorChange: (SelectedCourseBehavior) -> Unit,
    onDeleteGroup: () -> Unit,
    onToggleCourse: (TrackedCourse, Boolean) -> Unit,
    onAutoSelectToggle: (TrackedCourse, Boolean) -> Unit,
    onClearMessage: (TrackedCourse) -> Unit,
    onDeleteCourse: (TrackedCourse) -> Unit,
    onPersistOrder: (String, List<String>) -> Unit
) {
    var expanded by remember(courses.first().trackingGroupId) { mutableStateOf(courses.size == 1) }
    val first = courses.first()
    val groupId = first.trackingGroupId
    var displayedCourses by remember(groupId) { mutableStateOf(courses) }
    var draggingCourseId by remember(groupId) { mutableStateOf<String?>(null) }
    var hasLocalOrderChanges by remember(groupId) { mutableStateOf(false) }

    LaunchedEffect(courses) {
        val incomingIds = courses.map { it.courseId }
        val displayedIds = displayedCourses.map { it.courseId }
        when {
            draggingCourseId != null -> Unit
            !hasLocalOrderChanges -> displayedCourses = courses
            incomingIds == displayedIds -> {
                displayedCourses = courses
                hasLocalOrderChanges = false
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(first.courseName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "已启用 ${courses.count { it.isMonitoring }} / ${courses.size} 个备选课堂",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = first.isGroupMonitoringEnabled, onCheckedChange = onGroupToggle)
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "展开课程组")
                }
            }

            if (!first.isGroupMonitoringEnabled) {
                Text("课程组已暂停；课堂开关和优先级已保留", style = MaterialTheme.typography.labelSmall)
            }

            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                GroupBehaviorSelector(first.effectiveSelectedCourseBehavior, onBehaviorChange)
                Spacer(Modifier.height(8.dp))
                if (displayedCourses.size > 1) {
                    Text("长按拖动手柄调整组内优先级（越靠上越优先）", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                displayedCourses.forEachIndexed { index, course ->
                    key(course.courseId) {
                        DraggableTrackedLineItem(
                            course = course,
                            index = index,
                            groupSize = displayedCourses.size,
                            groupEnabled = first.isGroupMonitoringEnabled,
                            onToggle = { onToggleCourse(course, it) },
                            onAutoSelectToggle = { onAutoSelectToggle(course, it) },
                            onClearMessage = { onClearMessage(course) },
                            onDelete = { onDeleteCourse(course) },
                            onDragStart = { draggingCourseId = course.courseId },
                            onMove = { direction ->
                                val from = displayedCourses.indexOfFirst { it.courseId == course.courseId }
                                val reordered = CourseOrderEditor.move(displayedCourses, from, direction)
                                if (reordered !== displayedCourses) {
                                    displayedCourses = reordered
                                    hasLocalOrderChanges = true
                                }
                            },
                            onDragEnd = {
                                draggingCourseId = null
                                val finalOrder = displayedCourses.map { it.courseId }
                                if (hasLocalOrderChanges && finalOrder != courses.map { it.courseId }) {
                                    onPersistOrder(groupId, finalOrder)
                                } else {
                                    displayedCourses = courses
                                    hasLocalOrderChanges = false
                                }
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = onDeleteGroup, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("删除本课程组", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun GroupBehaviorSelector(
    behavior: SelectedCourseBehavior,
    onBehaviorChange: (SelectedCourseBehavior) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Text("确认已选中课堂后的处理", style = MaterialTheme.typography.labelLarge)
    Box {
        OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(behavior.title(), modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            SelectedCourseBehavior.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.title()) },
                    onClick = {
                        menuExpanded = false
                        onBehaviorChange(option)
                    }
                )
            }
        }
    }
    Text(
        when (behavior) {
            SelectedCourseBehavior.PRIORITY_UPGRADE -> "仅继续检测更高优先级课堂；当前版本不会自动退课，换班状态机会单独接入。"
            SelectedCourseBehavior.DISABLE_GROUP -> "安全默认值：暂停本组，但保留课堂开关与顺序。"
            SelectedCourseBehavior.DELETE_GROUP -> "确认选中后删除本组全部跟踪条目。"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun SelectedCourseBehavior.title(): String = when (this) {
    SelectedCourseBehavior.PRIORITY_UPGRADE -> "优先级升级"
    SelectedCourseBehavior.DISABLE_GROUP -> "禁用本课程跟踪"
    SelectedCourseBehavior.DELETE_GROUP -> "删除本课程跟踪"
}

@Composable
private fun DraggableTrackedLineItem(
    course: TrackedCourse,
    index: Int,
    groupSize: Int,
    groupEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onAutoSelectToggle: (Boolean) -> Unit,
    onClearMessage: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onMove: (Int) -> Unit,
    onDragEnd: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val spacingPx = with(LocalDensity.current) { 8.dp.toPx() }
    val placementOffset = remember(course.courseId) { Animatable(0f) }
    val settlingOffset = remember(course.courseId) { Animatable(0f) }
    var cardHeight by remember(course.courseId) { mutableIntStateOf(0) }
    var previousIndex by remember(course.courseId) { mutableIntStateOf(index) }
    var dragOffset by remember(course.courseId) { mutableFloatStateOf(0f) }
    var isDragging by remember(course.courseId) { mutableStateOf(false) }
    var movePending by remember(course.courseId) { mutableStateOf(false) }

    LaunchedEffect(index, cardHeight) {
        if (index == previousIndex || cardHeight == 0) return@LaunchedEffect
        val distance = cardHeight + spacingPx
        val layoutDelta = (previousIndex - index) * distance
        previousIndex = index
        movePending = false
        if (isDragging) {
            dragOffset += layoutDelta
        } else {
            placementOffset.snapTo(layoutDelta)
            placementOffset.animateTo(
                0f,
                spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
            )
        }
    }

    val visualOffset = if (isDragging) dragOffset else settlingOffset.value
    TrackedLineItem(
        course = course,
        groupEnabled = groupEnabled,
        onToggle = onToggle,
        onAutoSelectToggle = onAutoSelectToggle,
        onClearMessage = onClearMessage,
        onDelete = onDelete,
        modifier = Modifier
            .zIndex(if (isDragging || settlingOffset.isRunning) 1f else 0f)
            .graphicsLayer {
                translationY = visualOffset + placementOffset.value
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
                shadowElevation = if (isDragging) 18.dp.toPx() else 0f
                alpha = if (isDragging) 0.96f else 1f
            }
            .onSizeChanged { cardHeight = it.height },
        dragHandle = if (groupSize > 1) {{
            ReorderHandle(
                groupId = course.trackingGroupId,
                courseId = course.courseId,
                onDragStart = {
                    scope.launch { settlingOffset.stop() }
                    isDragging = true
                    onDragStart()
                },
                onDrag = { delta ->
                    dragOffset += delta
                    val threshold = ((cardHeight + spacingPx) * 0.45f).coerceAtLeast(spacingPx * 2)
                    val direction = when {
                        dragOffset > threshold && index < groupSize - 1 -> 1
                        dragOffset < -threshold && index > 0 -> -1
                        else -> 0
                    }
                    if (direction != 0 && !movePending) {
                        movePending = true
                        onMove(direction)
                    }
                },
                onDragEnd = {
                    val releaseOffset = dragOffset
                    dragOffset = 0f
                    isDragging = false
                    onDragEnd()
                    scope.launch {
                        settlingOffset.snapTo(releaseOffset)
                        settlingOffset.animateTo(
                            0f,
                            spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)
                        )
                    }
                }
            )
        }} else null
    )
}

@Composable
private fun ReorderHandle(
    groupId: String,
    courseId: String,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    Icon(
        Icons.Default.DragHandle,
        contentDescription = "长按拖动调整优先级",
        modifier = Modifier.size(32.dp).pointerInput(groupId, courseId) {
            detectDragGesturesAfterLongPress(
                onDragStart = { currentOnDragStart() },
                onDragEnd = { currentOnDragEnd() },
                onDragCancel = { currentOnDragEnd() },
                onDrag = { change, amount ->
                    change.consume()
                    currentOnDrag(amount.y)
                }
            )
        },
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun TrackedLineItem(
    course: TrackedCourse,
    groupEnabled: Boolean = true,
    onToggle: (Boolean) -> Unit,
    onAutoSelectToggle: (Boolean) -> Unit,
    onClearMessage: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                dragHandle?.invoke()
                if (dragHandle != null) Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(course.courseName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "课堂号: ${course.courseId} • 教师: ${course.teacher.ifBlank { "未知" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    course.courseNumber?.let { Text("课程号: $it", style = MaterialTheme.typography.bodySmall) }
                }
                Switch(checked = course.isMonitoring, onCheckedChange = onToggle)
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自动选课", style = MaterialTheme.typography.bodyMedium)
                    Text("检测到空位后自动尝试选课", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = course.autoSelectEnabled == true,
                    onCheckedChange = onAutoSelectToggle,
                    enabled = groupEnabled && course.isMonitoring
                )
            }

            if (!course.lastSelectMessage.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                        Text(course.lastSelectMessage.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = onClearMessage, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "清除反馈", Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val selected = course.isAlreadySelected == true
                    val statusText = when {
                        selected -> "已选中"
                        course.vacancy > 0 -> "有空位: ${course.vacancy}"
                        else -> "暂无空位"
                    }
                    Text("状态: $statusText", color = if (selected || course.vacancy > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    val time = if (course.lastCheckTime > 0) {
                        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(course.lastCheckTime))
                    } else "尚未检测"
                    Text("最近检测: $time", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "删除该课堂跟踪", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
