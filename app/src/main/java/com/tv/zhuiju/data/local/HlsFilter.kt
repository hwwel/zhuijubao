package com.tv.zhuiju.data.local

import java.util.regex.Pattern

/**
 * HLS m3u8 广告过滤器
 * 过滤 m3u8 播放列表中的广告片段
 */
object HlsFilter {

    // 广告特征关键词（匹配这些关键词的 TS 片段将被过滤）
    private val AD_PATTERNS = listOf(
        Pattern.compile(".*广告.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*ad.*\\.ts", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*advert.*\\.ts", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*插播.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*推广.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*sponsor.*\\.ts", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*promo.*\\.ts", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*dsp.*\\.ts", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*midroll.*\\.ts", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*preroll.*\\.ts", Pattern.CASE_INSENSITIVE)
    )

    // 广告时长阈值（秒），小于此值的片段可能是广告
    private const val AD_DURATION_THRESHOLD = 30.0

    /**
     * 过滤 m3u8 内容中的广告行
     * @param m3u8Content 原始 m3u8 内容
     * @return 过滤后的 m3u8 内容
     */
    fun filterM3u8(m3u8Content: String): String {
        val lines = m3u8Content.lines()
        val filteredLines = mutableListOf<String>()
        var skipNext = false
        var currentDuration = 0.0

        for (i in lines.indices) {
            val line = lines[i].trim()

            // 跳过已标记的行
            if (skipNext) {
                skipNext = false
                continue
            }

            // 解析 #EXTINF 时长
            if (line.startsWith("#EXTINF:")) {
                currentDuration = extractDuration(line)
            }

            // 检查 TS 片段是否为广告
            if (line.endsWith(".ts") || line.endsWith(".m3u8")) {
                val isAd = isAdSegment(line, currentDuration)
                if (isAd) {
                    // 跳过该片段和之前的 EXTINF
                    if (filteredLines.isNotEmpty() && filteredLines.last().startsWith("#EXTINF:")) {
                        filteredLines.removeLast()
                    }
                    continue
                }
            }

            filteredLines.add(line)
        }

        return filteredLines.joinToString("\n")
    }

    /**
     * 检查是否为广告片段
     */
    private fun isAdSegment(segmentUrl: String, duration: Double): Boolean {
        // 匹配广告关键词
        for (pattern in AD_PATTERNS) {
            if (pattern.matcher(segmentUrl).matches()) {
                return true
            }
        }
        // 时长短于阈值的片段可能是广告
        if (duration > 0 && duration < AD_DURATION_THRESHOLD) {
            // 需要确认不是过短的正常片段
            return false // 暂时不根据时长过滤，避免误伤
        }
        return false
    }

    /**
     * 从 #EXTINF 行提取时长
     */
    private fun extractDuration(extinfLine: String): Double {
        return try {
            val match = Regex("#EXTINF:([\\d.]+)").find(extinfLine)
            match?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        } catch (_: Exception) {
            0.0
        }
    }
}