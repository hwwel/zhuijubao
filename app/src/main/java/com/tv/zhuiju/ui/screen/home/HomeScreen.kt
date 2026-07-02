package com.tv.zhuiju.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.tv.zhuiju.data.model.VideoItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlayer: (Long, String) -> Unit = { _, _ -> },
    onNavigateToSearch: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { viewModel.tabs.size })
    val showTodayRecommend = remember { androidx.compose.runtime.mutableStateOf(false) }

    if (showTodayRecommend.value) {
        TodayRecommendScreen(
            onBack = { showTodayRecommend.value = false },
            onNavigateToPlayer = onNavigateToPlayer
        )
        return
    }

    // 双向同步：Tab点击 → Pager滚动
    LaunchedEffect(uiState.currentTab) {
        pagerState.animateScrollToPage(uiState.currentTab)
    }

    // 双向同步：Pager滑动 → Tab更新（用snapshotFlow监听currentPage变化）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .filter { it != uiState.currentTab }
            .collectLatest { page ->
                viewModel.onTabSelected(page)
            }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "追剧宝",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "历史记录",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
            if (uiState.isLoading && uiState.bannerItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ScrollableTabRow：标签不挤在一起，可滑动
                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        edgePadding = 16.dp,
                        indicator = { tabPositions ->
                            if (pagerState.currentPage < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        divider = {}
                    ) {
                        viewModel.tabs.forEachIndexed { index, title ->
                            val selected = pagerState.currentPage == index
                            Tab(
                                selected = selected,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                text = {
                                    Text(
                                        text = title,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }

                    // HorizontalPager 填充剩余空间，每个页面独立滚动
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1
                    ) { page ->
                        when (page) {
                            0 -> RecommendPage(
                                bannerItems = uiState.bannerItems,
                                todayRecommend = uiState.todayRecommend,
                                tvSeries = uiState.tvSeries,
                                movies = uiState.movies,
                                variety = uiState.variety,
                                anime = uiState.anime,
                                documentary = uiState.documentary,
                                sports = uiState.sports,
                                watchHistory = uiState.watchHistory,
                                onNavigateToPlayer = onNavigateToPlayer,
                                onNavigateToTab = { tabIndex ->
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(tabIndex)
                                    }
                                },
                                onNavigateToHistory = onNavigateToHistory,
                                onNavigateToTodayRecommend = { showTodayRecommend.value = true }
                            )
                            1 -> CategoryContentPage(
                                items = uiState.tvSeries,
                                isLoadingMore = uiState.isLoadingMore,
                                onLoadMore = { viewModel.loadMore() },
                                onNavigateToPlayer = onNavigateToPlayer
                            )
                            2 -> CategoryContentPage(
                                items = uiState.movies,
                                isLoadingMore = uiState.isLoadingMore,
                                onLoadMore = { viewModel.loadMore() },
                                onNavigateToPlayer = onNavigateToPlayer
                            )
                            3 -> CategoryContentPage(
                                items = uiState.variety,
                                isLoadingMore = uiState.isLoadingMore,
                                onLoadMore = { viewModel.loadMore() },
                                onNavigateToPlayer = onNavigateToPlayer
                            )
                            4 -> CategoryContentPage(
                                items = uiState.anime,
                                isLoadingMore = uiState.isLoadingMore,
                                onLoadMore = { viewModel.loadMore() },
                                onNavigateToPlayer = onNavigateToPlayer
                            )
                            5 -> CategoryContentPage(
                                items = uiState.documentary,
                                isLoadingMore = uiState.isLoadingMore,
                                onLoadMore = { viewModel.loadMore() },
                                onNavigateToPlayer = onNavigateToPlayer
                            )
                            6 -> CategoryContentPage(
                                items = uiState.sports,
                                isLoadingMore = uiState.isLoadingMore,
                                onLoadMore = { viewModel.loadMore() },
                                onNavigateToPlayer = onNavigateToPlayer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecommendPage(
    bannerItems: List<VideoItem>,
    todayRecommend: List<VideoItem>,
    tvSeries: List<VideoItem>,
    movies: List<VideoItem>,
    variety: List<VideoItem>,
    anime: List<VideoItem>,
    documentary: List<VideoItem>,
    sports: List<VideoItem>,
    watchHistory: List<WatchHistoryItem> = emptyList(),
    onNavigateToPlayer: (Long, String) -> Unit = { _, _ -> },
    onNavigateToTab: (Int) -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToTodayRecommend: () -> Unit = {}
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // 轮播图
        if (bannerItems.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                BannerCarousel(items = bannerItems, onItemClick = { item ->
                    onNavigateToPlayer(item.id, Gson().toJson(item))
                })
            }
        }

        // 观看历史
        if (watchHistory.isNotEmpty()) {
            item {
                SectionHeader(title = "观看历史", onMoreClick = { onNavigateToHistory() })
                WatchHistoryRow(
                    history = watchHistory,
                    onItemClick = { historyItem ->
                        onNavigateToPlayer(historyItem.videoId, historyItem.videoJson)
                    }
                )
            }
        }

        // 今日推荐 2x3
        if (todayRecommend.isNotEmpty()) {
            item {
                SectionHeader(title = "今日推荐", onMoreClick = { onNavigateToTodayRecommend() })
                TodayRecommendGrid(items = todayRecommend, onNavigateToPlayer = onNavigateToPlayer)
            }
        }

        // 电视剧 3x3
        if (tvSeries.isNotEmpty()) {
            item {
                SectionHeader(title = "电视剧", onMoreClick = { onNavigateToTab(1) })
                VideoGrid3x3(items = tvSeries.take(9), onNavigateToPlayer = onNavigateToPlayer)
            }
        }

        // 电影 3x3
        if (movies.isNotEmpty()) {
            item {
                SectionHeader(title = "电影", onMoreClick = { onNavigateToTab(2) })
                VideoGrid3x3(items = movies.take(9), onNavigateToPlayer = onNavigateToPlayer)
            }
        }

        // 综艺 3x3
        if (variety.isNotEmpty()) {
            item {
                SectionHeader(title = "综艺", onMoreClick = { onNavigateToTab(3) })
                VideoGrid3x3(items = variety.take(9), onNavigateToPlayer = onNavigateToPlayer)
            }
        }

        // 动漫 3x3
        if (anime.isNotEmpty()) {
            item {
                SectionHeader(title = "动漫", onMoreClick = { onNavigateToTab(4) })
                VideoGrid3x3(items = anime.take(9), onNavigateToPlayer = onNavigateToPlayer)
            }
        }

        // 纪录片 3x3
        if (documentary.isNotEmpty()) {
            item {
                SectionHeader(title = "纪录片", onMoreClick = { onNavigateToTab(5) })
                VideoGrid3x3(items = documentary.take(9), onNavigateToPlayer = onNavigateToPlayer)
            }
        }

        // 体育 3x3
        if (sports.isNotEmpty()) {
            item {
                SectionHeader(title = "体育", onMoreClick = { onNavigateToTab(6) })
                VideoGrid3x3(items = sports.take(9), onNavigateToPlayer = onNavigateToPlayer)
            }
        }
    }
}

@Composable
fun CategoryContentPage(
    items: List<VideoItem>,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onNavigateToPlayer: (Long, String) -> Unit = { _, _ -> }
) {
    if (items.isEmpty()) {
        // 无数据时自动触发加载
        LaunchedEffect(Unit) {
            if (!isLoadingMore) {
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
        val listState = rememberLazyListState()

        // 用 snapshotFlow 检测滑动到底部，始终加载更多
        LaunchedEffect(listState, isLoadingMore) {
            snapshotFlow {
                val layoutInfo = listState.layoutInfo
                val totalCount = layoutInfo.totalItemsCount
                if (totalCount == 0) return@snapshotFlow false
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@snapshotFlow false
                layoutInfo.visibleItemsInfo.size < totalCount && lastVisible.index >= totalCount - 3
            }
            .distinctUntilChanged()
            .collect { atBottom ->
                if (atBottom && !isLoadingMore) {
                    onLoadMore()
                }
            }
        }

        val rows = items.chunked(3)
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
            if (isLoadingMore) {
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
fun SectionHeader(
    title: String,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
                .clickable { onMoreClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "更多",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun TodayRecommendGrid(
    items: List<VideoItem>,
    onNavigateToPlayer: (Long, String) -> Unit = { _, _ -> }
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 第一行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items.take(3).forEach { item ->
                VideoGridLargeItem(
                    item = item,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToPlayer(item.id, Gson().toJson(item)) }
                )
            }
            repeat(3 - items.take(3).size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        // 第二行
        if (items.size > 3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items.drop(3).take(3).forEach { item ->
                    VideoGridLargeItem(
                        item = item,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToPlayer(item.id, Gson().toJson(item)) }
                    )
                }
                repeat(3 - items.drop(3).take(3).size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun VideoGrid3x3(
    items: List<VideoItem>,
    onNavigateToPlayer: (Long, String) -> Unit = { _, _ -> }
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.take(9).chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
    }
}

@Composable
fun WatchHistoryRow(
    history: List<WatchHistoryItem>,
    onItemClick: (WatchHistoryItem) -> Unit = {}
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(history.size) { index ->
            val item = history[index]
            WatchHistoryItem(
                historyItem = item,
                onClick = { onItemClick(item) }
            )
        }
    }
}

@Composable
fun WatchHistoryItem(
    historyItem: WatchHistoryItem,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = historyItem.pic,
                contentDescription = historyItem.name,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = historyItem.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (historyItem.episodeTitle != null) {
            Text(
                text = historyItem.episodeTitle,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}