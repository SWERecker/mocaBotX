package me.swe.main

import okhttp3.Request
import java.io.IOException


class Apex {
    private fun getApiUrl(mode: String): String {
        val key = getBotConfig("APEX_APIKEY")
        if (key == "") {
            mocaLogger.warn("Apex API Key not found!")
            return ""
        }
        return when (mode) {
            "map" -> {
                "https://api.mozambiquehe.re/maprotation?version=2&auth=${key}"
            }
            else -> {
                ""
            }
        }
    }

    fun getMapsInfo(): ApexMaps {
        val reqBuilder = Request.Builder()
        reqBuilder.url(getApiUrl("map")).get()
        val call = client.newCall(reqBuilder.build())
        return try {
            val jsonMap = call.execute().body!!.string().jsonToMap()
            // println(jsonMap)

            val br = jsonMap["battle_royale"] as Map<*, *>
            val brCurrent = br["current"] as Map<*, *>
            val brNext = br["next"] as Map<*, *>

            val rk = jsonMap["ranked"] as Map<*, *>
            val rkCurrent = rk["current"] as Map<*, *>
            val rkNext = rk["next"] as Map<*, *>

            val ar = jsonMap["arenas"] as Map<*, *>
            val arCurrent = ar["current"] as Map<*, *>
            val arNext = ar["next"] as Map<*, *>

            val rar = jsonMap["arenasRanked"] as Map<*, *>
            val rarCurrent = rar["current"] as Map<*, *>
            val rarNext = rar["next"] as Map<*, *>

            val apexMaps = ApexMaps()
            return apexMaps.apply {
                brCurrentMap = brCurrent["map"].toString().toChineseMapName()
                brCurrentStartTime = brCurrent["start"].toString().sciToLong()
                brCurrentEndTime = brCurrent["end"].toString().sciToLong()
                brRemainingMins = brCurrent["remainingMins"].toString().sciToInt()
                brRemainingTimer = brCurrent["remainingTimer"].toString()

                brNextMap = brNext["map"].toString().toChineseMapName()
                brNextStartTime = brNext["start"].toString().sciToLong()
                brNextEndTime = brNext["end"].toString().sciToLong()
                brNextDurationMins = brNext["DurationInMinutes"].toString().sciToInt()

                rkCurrentMap = rkCurrent["map"].toString().toChineseMapName()
                rkNextMap = rkNext["map"].toString().toChineseMapName()

                arCurrentMap = arCurrent["map"].toString().toChineseMapName()
                arCurrentStartTime = arCurrent["start"].toString().sciToLong()
                arCurrentEndTime = arCurrent["end"].toString().sciToLong()
                arRemainingMins = arCurrent["remainingMins"].toString().sciToInt()
                arRemainingTimer = arCurrent["remainingTimer"].toString()

                arNextMap = arNext["map"].toString().toChineseMapName()
                arNextStartTime = arNext["start"].toString().sciToLong()
                arNextEndTime = arNext["end"].toString().sciToLong()
                arNextDurationMins = arNext["DurationInMinutes"].toString().sciToInt()

                rarCurrentMap = rarCurrent["map"].toString().toChineseMapName()
                rarCurrentStartTime = rarCurrent["start"].toString().sciToLong()
                rarCurrentEndTime = rarCurrent["end"].toString().sciToLong()
                rarRemainingMins = rarCurrent["remainingMins"].toString().sciToInt()
                rarRemainingTimer = rarCurrent["remainingTimer"].toString()

                rarNextMap = rarNext["map"].toString().toChineseMapName()
                rarNextStartTime = rarNext["start"].toString().sciToLong()
                rarNextEndTime = rarNext["end"].toString().sciToLong()
                rarNextDurationMins = rarNext["DurationInMinutes"].toString().sciToInt()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            ApexMaps()
        }
    }

    private fun String.toChineseMapName(): String {
        return when (this.lowercase().replace(" ", "")) {
            // Battle Royale
            "world'sedge" -> "世界边缘"
            "kingscanyon" -> "诸王峡谷"
            "olympus" -> "奥林匹斯"
            // Arena
            "phaserunner" -> "相位穿梭器"
            "partycrasher" -> "派对破坏者"
            "dome" -> "穹顶（世界边缘）"
            "overflow" -> "溢出"
            "artillery" -> "火炮（诸王峡谷）"
            "thermalstation" -> "热能塔（世界边缘）"
            "oasis" -> "花园（奥林匹斯）"
            "hillsideoutpost" -> "山边前哨（诸王峡谷）"
            else -> "Unknown (${this})"
        }
    }
}

class ApexMaps {
    /**
     * Battle Royale Current
     */
    var brCurrentMap = ""
    var brCurrentStartTime = 0L
    var brCurrentEndTime = 0L
    var brRemainingMins = 0
    var brRemainingTimer = ""

    /**
     * Battle Royale Next
     */
    var brNextMap = ""
    var brNextStartTime = 0L
    var brNextEndTime = 0L
    var brNextDurationMins = 0

    /**
     * Ranked Current
     */
    var rkCurrentMap = ""

    /**
     * Ranked Next
     */
    var rkNextMap = ""

    /**
     * Arena Current
     */
    var arCurrentMap = ""
    var arCurrentStartTime = 0L
    var arCurrentEndTime = 0L
    var arRemainingMins = 0
    var arRemainingTimer = ""

    /**
     * Arena Next
     */
    var arNextMap = ""
    var arNextStartTime = 0L
    var arNextEndTime = 0L
    var arNextDurationMins = 0

    /**
     * Ranked Arena Current
     */
    var rarCurrentMap = ""
    var rarCurrentStartTime = 0L
    var rarCurrentEndTime = 0L
    var rarRemainingMins = 0
    var rarRemainingTimer = ""

    /**
     * Ranked Arena Next
     */
    var rarNextMap = ""
    var rarNextStartTime = 0L
    var rarNextEndTime = 0L
    var rarNextDurationMins = 0
}
