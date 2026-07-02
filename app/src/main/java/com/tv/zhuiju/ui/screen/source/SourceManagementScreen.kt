package com.tv.zhuiju.ui.screen.source

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tv.zhuiju.data.model.CloudSource
import com.tv.zhuiju.data.model.SourceCategoryItem
import com.tv.zhuiju.data.model.SourceConfigFull
import com.tv.zhuiju.data.model.VideoCategory

/** 云端源分类名映射 */
private val categoryLabels = mapOf(
    "main" to "推荐采集源",
    "vod" to "综合采集源",
    "short" to "短剧采集源",
    "anime" to "动漫采集源",
    "midnight" to "午夜采集源"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceManagementScreen(
    onBack: () -> Unit = {},
    viewModel: SourceManagementViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("采集管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.selectedTab == 0) {
                        IconButton(onClick = { viewModel.showAddDialog() }) {
                            Icon(Icons.Filled.Add, contentDescription = "添加采集源")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab切换
            val tabs = listOf("我的采集源", "云端采集站")
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (uiState.selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (uiState.selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // 内容区域
            when (uiState.selectedTab) {
                0 -> CustomSourcesTab(uiState, viewModel)
                1 -> CloudSourcesTab(uiState, viewModel, context)
            }
        }
    }

    // 添加/编辑对话框
    if (uiState.showAddDialog) {
        AddSourceDialog(
            isEditing = uiState.editingSource != null,
            name = uiState.dialogName,
            url = uiState.dialogUrl,
            homeAc = uiState.dialogHomeAc,
            onNameChange = { viewModel.updateDialogName(it) },
            onUrlChange = { viewModel.updateDialogUrl(it) },
            onHomeAcChange = { viewModel.updateDialogHomeAc(it) },
            onConfirm = { viewModel.saveSource() },
            onDismiss = { viewModel.dismissDialog() }
        )
    }
}

// ==================== 我的采集源 Tab ====================

@Composable
private fun CustomSourcesTab(
    uiState: SourceManagementUiState,
    viewModel: SourceManagementViewModel
) {
    if (uiState.sources.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "暂无采集源",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "点击右上角 + 添加自定义 API 源",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "或切换到「云端采集站」一键添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.sources, key = { it.config.id }) { source ->
                CustomSourceCard(
                    source = source,
                    isTesting = uiState.testingSourceId == source.config.id,
                    testCategories = if (uiState.editingBindingsSourceId == source.config.id)
                        uiState.testCategories else emptyList(),
                    onTest = { viewModel.testSource(source.config.id) },
                    onToggleEnabled = { viewModel.toggleSourceEnabled(source.config.id) },
                    onEdit = { viewModel.showEditDialog(source) },
                    onDelete = { viewModel.deleteSource(source.config.id) },
                    onBindCategory = { typeName, typeId, category ->
                        viewModel.bindCategory(typeName, typeId, category)
                    },
                    onUnbindCategory = { typeName ->
                        viewModel.unbindCategory(typeName)
                    },
                    onDismissTest = { viewModel.dismissTestResults() }
                )
            }
        }
    }
}

@Composable
private fun CustomSourceCard(
    source: SourceConfigFull,
    isTesting: Boolean,
    testCategories: List<SourceCategoryItem>,
    onTest: () -> Unit,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBindCategory: (String, Int, VideoCategory) -> Unit,
    onUnbindCategory: (String) -> Unit,
    onDismissTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (source.config.enabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.config.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = source.config.baseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = source.config.enabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 绑定数量提示
            if (source.bindings.isNotEmpty()) {
                Text(
                    text = "已绑定 ${source.bindings.size} 个分类",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onTest,
                    enabled = !isTesting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    } else {
                        Icon(
                            Icons.Filled.Wifi,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text("测试")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onEdit) {
                    Text("编辑")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 测试结果：分类绑定列表
            if (testCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "分类绑定（共 ${testCategories.size} 个）",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                testCategories.forEach { category ->
                    BindableCategoryRow(
                        category = category,
                        onBind = { localCategory ->
                            onBindCategory(category.typeName, category.typeId, localCategory)
                        },
                        onUnbind = { onUnbindCategory(category.typeName) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onDismissTest) {
                    Text("收起")
                }
            }
        }
    }
}

@Composable
private fun BindableCategoryRow(
    category: SourceCategoryItem,
    onBind: (VideoCategory) -> Unit,
    onUnbind: () -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }
    val localCategories = VideoCategory.entries.toList()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDropdown = true },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = category.typeName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "(ID: ${category.typeId})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))

        Box {
            Button(
                onClick = { showDropdown = true },
                colors = if (category.boundLocalCategory != null) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(
                    if (category.boundLocalCategory != null) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = category.boundLocalCategory?.label ?: "绑定",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false }
            ) {
                if (category.boundLocalCategory != null) {
                    DropdownMenuItem(
                        text = { Text("解绑", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showDropdown = false
                            onUnbind()
                        }
                    )
                }
                localCategories.forEach { localCat ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (category.boundLocalCategory == localCat) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(localCat.label)
                            }
                        },
                        onClick = {
                            showDropdown = false
                            onBind(localCat)
                        }
                    )
                }
            }
        }
    }
}

// ==================== 云端采集站 Tab ====================

@Composable
private fun CloudSourcesTab(
    uiState: SourceManagementUiState,
    viewModel: SourceManagementViewModel,
    context: Context
) {
    if (uiState.cloudSources.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("加载云端采集站列表...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    // 按分类分组
    val groupedSources = uiState.cloudSources.groupBy { it.category }
    // 分类展示顺序
    val categoryOrder = listOf("main", "vod", "short", "anime", "midnight")

    // 已添加的自定义源URL集合
    val customSourceUrls = uiState.sources.map { it.config.baseUrl.trimEnd('/') }.toSet()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categoryOrder.forEach { catKey ->
            val sources = groupedSources[catKey] ?: return@forEach
            if (sources.isEmpty()) return@forEach

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = categoryLabels[catKey] ?: catKey,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${sources.size})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(sources.size) { index ->
                val source = sources[index]
                val isAdded = customSourceUrls.contains(source.url.trimEnd('/'))
                val testState = uiState.cloudTestStates[source.name] ?: CloudTestState.UNTESTED
                val isEnabled = isAdded && uiState.sources.any {
                    it.config.baseUrl.trimEnd('/') == source.url.trimEnd('/') && it.config.enabled
                }

                CloudSourceCard(
                    source = source,
                    isAdded = isAdded,
                    isEnabled = isEnabled,
                    testState = testState,
                    onToggle = { viewModel.addCloudSource(source) },
                    onTest = { viewModel.testCloudSource(source) },
                    onBind = { viewModel.testAndBindCloudSource(source) },
                    onCopyUrl = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("url", source.url))
                        Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // 分类绑定结果（如果从云端触发了绑定）
        if (uiState.testCategories.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "分类绑定（共 ${uiState.testCategories.size} 个）",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        uiState.testCategories.forEach { category ->
                            BindableCategoryRow(
                                category = category,
                                onBind = { localCategory ->
                                    viewModel.bindCategory(category.typeName, category.typeId, localCategory)
                                },
                                onUnbind = { viewModel.unbindCategory(category.typeName) }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = { viewModel.dismissTestResults() }) {
                            Text("收起")
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

/**
 * 云端采集站单项卡片
 * 布局：[小竖条] 名称  开关
 *       链接（点击复制）
 *       [测试] [绑定]
 */
@Composable
private fun CloudSourceCard(
    source: CloudSource,
    isAdded: Boolean,
    isEnabled: Boolean,
    testState: CloudTestState,
    onToggle: () -> Unit,
    onTest: () -> Unit,
    onBind: () -> Unit,
    onCopyUrl: () -> Unit
) {
    // 状态颜色：已测试可用=绿色，已测试不可用=红色，JSON中verified=绿色，否则=灰色
    val indicatorColor = when (testState) {
        CloudTestState.AVAILABLE -> Color(0xFF4CAF50)
        CloudTestState.UNAVAILABLE -> Color(0xFFF44336)
        CloudTestState.TESTING -> Color(0xFFFFC107)
        CloudTestState.UNTESTED -> if (source.verified) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // 小竖条（状态指示器）
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(indicatorColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // 第一行：名称 + 开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = source.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            // 已添加标记
                            if (isAdded) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "已添加",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (source.remark.isNotBlank()) {
                            Text(
                                text = source.remark,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { onToggle() }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 第二行：链接（点击复制）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onCopyUrl() }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Link,
                        contentDescription = "复制链接",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = source.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "复制",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 第三行：测试 + 绑定按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 测试按钮
                    FilterChip(
                        selected = testState == CloudTestState.AVAILABLE,
                        onClick = onTest,
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (testState == CloudTestState.TESTING) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = when (testState) {
                                        CloudTestState.UNTESTED -> "测试"
                                        CloudTestState.TESTING -> "测试中"
                                        CloudTestState.AVAILABLE -> "可用"
                                        CloudTestState.UNAVAILABLE -> "不可用"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF4CAF50).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFF4CAF50)
                        )
                    )
                    // 绑定按钮（仅可用时显示）
                    if (testState == CloudTestState.AVAILABLE || (testState == CloudTestState.UNTESTED && source.verified)) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                if (!isAdded) onToggle() // 先添加
                                onBind()
                            },
                            label = {
                                Text("绑定", style = MaterialTheme.typography.labelSmall)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ==================== 添加/编辑对话框 ====================

@Composable
private fun AddSourceDialog(
    isEditing: Boolean,
    name: String,
    url: String,
    homeAc: String,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onHomeAcChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "编辑采集源" else "添加采集源")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("源名称") },
                    placeholder = { Text("如：自定义源1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    label = { Text("API 地址") },
                    placeholder = { Text("如：https://api.example.com/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = homeAc,
                    onValueChange = onHomeAcChange,
                    label = { Text("首页 ac 参数") },
                    placeholder = { Text("videolist 或 detail") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}