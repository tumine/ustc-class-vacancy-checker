package com.ustc.vacancychecker.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ustc.vacancychecker.ui.login.LoginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    loginViewModel: LoginViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    val currentInterval by settingsViewModel.monitoringInterval.collectAsState()
    val autoSelectEnabled by settingsViewModel.autoSelectEnabled.collectAsState()
    val intervalOptions = if (com.ustc.vacancychecker.BuildConfig.DEBUG) {
        listOf(1, 5, 10, 15, 30, 60, 120, 240)
    } else {
        listOf(5, 10, 15, 30, 60, 120, 240)
    }
    var expanded by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
                .verticalScroll(rememberScrollState())
        ) {
            // 后台监控频率设置
            Text(
                text = "后台监控",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { expanded = true }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "自动刷新频率", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "设置进入监控后后台自动检测的频率。频率过高可能容易被校方接口限制。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = "$currentInterval 分钟",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            intervalOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text("$option 分钟") },
                                    onClick = {
                                        settingsViewModel.updateInterval(option)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            Text(
                text = "选课设置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { settingsViewModel.updateAutoSelectEnabled(!autoSelectEnabled) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "自动选课", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "检测到空位后自动点击选课按钮（谨慎使用）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Switch(
                        checked = autoSelectEnabled,
                        onCheckedChange = { settingsViewModel.updateAutoSelectEnabled(it) }
                    )
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            Text(
                text = "账号设置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )

            // 登出按钮
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showLogoutDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "退出登录",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Icon(
                        imageVector = Icons.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // 关于
            Text(
                text = "关于",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { /* 无操作 */ }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "当前版本", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "v${com.ustc.vacancychecker.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            val uiState = settingsViewModel.uiState
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (!uiState.isCheckingUpdate) {
                        settingsViewModel.checkForUpdate()
                    }
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "检查更新", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (uiState.isCheckingUpdate) "正在检查..." else "从 GitHub 获取最新版本",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出登录吗？退出后需要重新登录。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        loginViewModel.logout()
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 检查更新加载中
    val uiState = settingsViewModel.uiState
    if (uiState.isCheckingUpdate) {
        AlertDialog(
            onDismissRequest = { /* 禁止关闭 */ },
            title = { Text("检查更新") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("正在检查更新...")
                }
            },
            confirmButton = {}
        )
    }

    // 更新检查结果对话框
    val context = LocalContext.current
    val updateInfo = uiState.updateInfo
    if (updateInfo != null) {
        if (updateInfo.hasUpdate) {
            AlertDialog(
                onDismissRequest = { settingsViewModel.dismissUpdateDialog() },
                title = { Text("发现新版本") },
                text = {
                    Column {
                        Text("最新版本: ${updateInfo.latestVersion}")
                        Text("当前版本: ${updateInfo.currentVersion}")
                        if (updateInfo.releaseNotes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "更新说明:",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = updateInfo.releaseNotes,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.releaseUrl))
                            context.startActivity(intent)
                            settingsViewModel.dismissUpdateDialog()
                        }
                    ) {
                        Text("前往下载")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { settingsViewModel.dismissUpdateDialog() }) {
                        Text("稍后再说")
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { settingsViewModel.dismissUpdateDialog() },
                title = { Text("检查更新") },
                text = { Text("当前已是最新版本 (${updateInfo.currentVersion})") },
                confirmButton = {
                    TextButton(onClick = { settingsViewModel.dismissUpdateDialog() }) {
                        Text("确定")
                    }
                }
            )
        }
    }

    // 更新检查失败
    val updateError = uiState.updateError
    if (updateError != null) {
        AlertDialog(
            onDismissRequest = { settingsViewModel.dismissUpdateDialog() },
            title = { Text("检查更新失败") },
            text = { Text(updateError) },
            confirmButton = {
                TextButton(onClick = { settingsViewModel.dismissUpdateDialog() }) {
                    Text("确定")
                }
            }
        )
    }
}
