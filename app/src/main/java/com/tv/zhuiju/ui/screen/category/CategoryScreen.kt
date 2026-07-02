package com.tv.zhuiju.ui.screen.category

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.tv.zhuiju.ui.screen.home.VideoGridItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    onNavigateToPlayer: (Long, String) -> Unit = { _, _ -> },
    viewModel: CategoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "分类",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 仅首次加载且无数据时显示全屏加载
            if (uiState.isLoading && uiState.allItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // === 筛选栏：始终可见，切换分类时不清除 ===
                    FilterBar(
                        uiState = uiState,
                        onSelectParent = { viewModel.selectParentCategory(it) },
                        onSelectSub = { viewModel.selectSubCategory(it) },
                        onSelectYear = { viewModel.selectYear(it) }
                    )

                    // === 内容列表 ===
                    CategoryContentList(
                        uiState = uiState,
                        onLoadMore = { viewModel.loadMore() },
                        onNavigateToPlayer = onNavigateToPlayer
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    uiState: CategoryUiState,
    onSelectParent: (Int) -> Unit,
    onSelectSub: (Int) -> Unit,
    onSelectYear: (Int) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 第一行：父分类（全部 + 电视剧/电影/综艺...）
        FilterRow(
            items = listOf("全部") + uiState.parentCategories.map { it.label },
            selectedIndex = uiState.selectedParentIndex,
            onSelect = onSelectParent
        )

        // 第二行：子分类（根据父分类动态变化）
        if (uiState.subCategories.isNotEmpty()) {
            FilterRow(
                items = listOf("全部") + uiState.subCategories,
                selectedIndex = uiState.selectedSubIndex,
                onSelect = onSelectSub
            )
        }

        // 第三行：年份
        if (uiState.years.isNotEmpty()) {
            FilterRow(
                items = listOf("全部") + uiState.years,
                selectedIndex = uiState.selectedYearIndex,
                onSelect = onSelectYear
            )
        }
    }
}

@Composable
private fun CategoryContentList(
    uiState: CategoryUiState,
    onLoadMore: () -> Unit,
    onNavigateToPlayer: (Long, String) -> Unit
) {
    val listState = rememberLazyListState()

    // 检测滚动到底部，使用 snapshotFlow 确保持续触发加载更多
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 3
        }
        .distinctUntilChanged()
        .filter { it }
        .collect {
            if (!uiState.isLoadingMore) {
                onLoadMore()
            }
        }
    }

    // 列表区域加载中（切换分类时）
    if (uiState.isListLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "加载中...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (uiState.filteredItems.isEmpty()) {
        // 自动触发加载更多
        LaunchedEffect(Unit) {
            if (!uiState.isLoadingMore && !uiState.isLoading) {
                onLoadMore()
            }
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "加载中...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        val rows = uiState.filteredItems.chunked(3)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(rows.size) { index ->
                val rowItems = rows[index]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { item ->
                        VideoGridItem(
                            item = item,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToPlayer(item.id, Gson().toJson(item)) }
                        )
                    }
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            if (uiState.isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.3f))
                    }
                }
            }
        }
    }
}

@Composable
fun FilterRow(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { index, label ->
            FilterChip(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}