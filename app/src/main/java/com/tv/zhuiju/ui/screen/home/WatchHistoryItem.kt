package com.tv.zhuiju.ui.screen.home

data class WatchHistoryItem(
    val videoId: Long,
    val videoJson: String,
    val name: String,
    val pic: String? = null,
    val lastPosition: Long = 0L,
    val episodeTitle: String? = null,
    val sourceIndex: Int = 0,
    val episodeIndex: Int = 0,
    val watchedAt: Long = System.currentTimeMillis(),
    val dramaType: String = "video" // "video" | "drama"
)
