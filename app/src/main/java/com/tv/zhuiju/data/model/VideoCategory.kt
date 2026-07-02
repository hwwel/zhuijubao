package com.tv.zhuiju.data.model

import com.tv.zhuiju.data.local.YouthModeManager

enum class VideoCategory(val label: String, val keywords: List<String>) {
    TV_SERIES("电视剧", listOf(
        "电视剧", "国产剧", "欧美剧", "韩剧", "日剧", "港剧台", "港剧", "泰剧", "海外剧",
        "Netflix自制剧", "马泰剧", "内地剧", "台湾剧", "连续剧", "香港剧", "韩国剧", "日本剧", "泰国剧",
        "剧泰剧", "台剧", "香港剧"
    )),
    MOVIE("电影", listOf(
        "电影", "动作片", "喜剧片", "爱情片", "科幻片", "恐怖片", "剧情片", "战争片",
        "4K电影", "邵氏电影", "Netflix电影", "伦理片", "灾难片", "悬疑片", "犯罪片", "奇幻片",
        "电影片"
    )),
    VARIETY("综艺", listOf(
        "综艺", "大陆综艺", "日韩综艺", "港台综艺", "欧美综艺", "综艺片"
    )),
    ANIME("动漫", listOf(
        "动漫", "国产动漫", "日韩动漫", "欧美动漫", "动画片", "港台动漫", "海外动漫",
        "有声动漫", "中国动漫", "日本动漫", "动漫片"
    )),
    DOCUMENTARY("纪录片", listOf("纪录片", "记录片")),
    SPORTS("体育", listOf(
        "体育赛事", "篮球", "足球", "斯诺克", "台球", "其他赛事", "体育", "网球"
    )),
    DRAMA("短剧", listOf(
        "爽文短剧", "女频恋爱", "反转爽剧", "古装仙侠", "年代穿越", "脑洞悬疑",
        "现代都市", "擦边短剧", "漫剧", "短剧", "AI漫剧"
    )),
    ETHICS("伦理", listOf(
        "伦理", "港台三级", "韩国伦理", "西方伦理", "日本伦理", "两性课堂", "伦理片"
    )),
    OTHER("其他", listOf(
        "演唱会", "预告片", "影视解说", "写真", "热舞", "科普", "学习", "电影解说",
        "写真热舞", "科普学习", "新闻资讯", "电影资讯", "娱乐新闻", "演员"
    ));

    companion object {
        /** 青少年模式下需要屏蔽的分类 */
        val YOUTH_BLOCKED_CATEGORIES = setOf(ETHICS, OTHER)

        /**
         * 青少年模式下被屏蔽的分类名列表（从 YouthModeManager 获取）
         */
        fun blockedCategoriesInYouthMode(): Set<String> {
            return if (YouthModeManager.isEnabled) {
                YouthModeManager.blockedCategories
            } else {
                emptySet()
            }
        }

        /**
         * 检查 typeName 在青少年模式下是否被屏蔽
         */
        fun isBlockedInYouthMode(typeName: String?): Boolean {
            if (typeName.isNullOrBlank()) return false
            if (!YouthModeManager.isEnabled) return false
            return YouthModeManager.blockedCategories.any { blocked ->
                typeName.contains(blocked, ignoreCase = true)
            }
        }

        /**
         * 获取青少年模式下可用的分类列表
         */
        fun availableCategories(): List<VideoCategory> {
            return if (YouthModeManager.isEnabled) {
                entries.filter { it !in YOUTH_BLOCKED_CATEGORIES }
            } else {
                entries.toList()
            }
        }

        /**
         * 青少年模式过滤：移除被屏蔽的分类数据
         */
        fun filterYouthMode(items: List<VideoItem>): List<VideoItem> {
            if (!YouthModeManager.isEnabled) return items
            return items.filter { item ->
                !isBlockedInYouthMode(item.typeName)
            }
        }
        /**
         * 根据 typeName 分类，支持自定义绑定映射。
         * 优先使用 customBindings，未命中时回退到默认关键词匹配。
         */
        fun classify(typeName: String?, customBindings: Map<String, VideoCategory> = emptyMap()): VideoCategory {
            if (typeName.isNullOrBlank()) return TV_SERIES
            val name = typeName.trim()
            // 1. 优先检查自定义绑定
            if (customBindings.containsKey(name)) {
                return customBindings[name]!!
            }
            // 2. 默认关键词匹配
            entries.forEach { category ->
                if (category.keywords.any { it == name }) {
                    return category
                }
            }
            return TV_SERIES
        }

        /**
         * 根据 VideoItem 完整信息分类，结合 voteClass 和剧集数量修正模糊标签。
         * 1. 先用 typeName 分类（支持自定义绑定）
         * 2. 若 typeName 未匹配，尝试用 vodClass 中的标签分类
         * 3. 电影标签但有多集 → 改为综艺/剧集
         */
        fun classify(item: VideoItem, customBindings: Map<String, VideoCategory> = emptyMap()): VideoCategory {
            val category = classify(item.typeName, customBindings)
            val effectiveCategory = if (category == TV_SERIES && item.typeName.isNullOrBlank()) {
                // typeName 为空时，尝试从 vodClass 解析
                classifyFromVodClass(item.vodClass)
            } else {
                category
            }
            // 电影标签但剧集数 > 3（排除多源/多画质干扰）→ 改为综艺
            if (effectiveCategory == MOVIE && item.countEpisodes() > 3) {
                return VARIETY
            }
            return effectiveCategory
        }

        /**
         * 从 vodClass（逗号分隔的标签）中尝试匹配分类。
         * vodClass 示例: "喜剧,动作,韩剧" 或 "记录片,纪录片"
         */
        private fun classifyFromVodClass(vodClass: String?): VideoCategory {
            if (vodClass.isNullOrBlank()) return TV_SERIES
            val tags = vodClass.split(",").map { it.trim() }.filter { it.isNotBlank() }
            // 按所有分类关键词匹配，返回第一个命中的
            entries.forEach { category ->
                if (tags.any { tag -> category.keywords.any { it == tag } }) {
                    return category
                }
            }
            return TV_SERIES
        }

        /** 从 playUrl 解析剧集数量 */
        fun countEpisodes(playUrl: String?): Int {
            if (playUrl.isNullOrBlank()) return 0
            return playUrl.split("$$$")
                .flatMap { source -> source.split("#").filter { it.isNotBlank() } }
                .count { it.contains("$") }
        }
    }

    /** 映射为 API videolist 接口的 t 参数值（用于服务端分类过滤，返回子级 type_id 列表） */
    fun toApiTypeIds(): List<Int> = when (this) {
        // 不同API的type_id映射完全不同（闪电t=13=国产剧，极速t=13=恐怖片），
        // 因此全部改为客户端过滤，不传 t 参数
        TV_SERIES -> emptyList()
        MOVIE -> emptyList()
        VARIETY -> emptyList()
        ANIME -> emptyList()
        DOCUMENTARY -> emptyList()
        SPORTS -> emptyList()
        DRAMA -> emptyList()
        ETHICS -> emptyList()
        OTHER -> emptyList()
    }
}