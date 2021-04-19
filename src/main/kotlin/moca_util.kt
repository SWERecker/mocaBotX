package me.swe.main

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.*
import kotlin.Comparator
import java.time.ZonedDateTime




/**
 * 字符串相似度计算
 */
class StringSimilarity {
    private fun min(vararg `is`: Int): Int {
        var min = Int.MAX_VALUE
        for (i in `is`) {
            if (min > i) {
                min = i
            }
        }
        return min
    }

    fun compareString(str1: String, str2: String): Float {

        val len1 = str1.length
        val len2 = str2.length
        val dif = Array(len1 + 1) { IntArray(len2 + 1) }
        for (a in 0..len1) {
            dif[a][0] = a
        }
        for (a in 0..len2) {
            dif[0][a] = a
        }
        var temp: Int
        for (i in 1..len1) {
            for (j in 1..len2) {
                temp = if (str1[i - 1] == str2[j - 1]) {
                    0
                } else {
                    1
                }
                dif[i][j] = min(
                    dif[i - 1][j - 1] + temp, dif[i][j - 1] + 1,
                    dif[i - 1][j] + 1
                )
            }
        }
        return 1 - dif[len1][len2].toFloat() / str1.length.coerceAtLeast(str2.length)
    }
}

/**
 * 根据Map的Value进行一个序的排
 */
class MapSort{
    fun mapSortByValue(toSortMap: Map<String, Int>): Map<String, String> {

        //自定义比较器
        val valCmp: java.util.Comparator<Map.Entry<String?, Int>> =
            Comparator { o1, o2 ->
                // TODO Auto-generated method stub
                o2.value - o1.value
            }
        val list: List<Map.Entry<String, Int>> = ArrayList(toSortMap.entries) //传入maps实体
        Collections.sort(list, valCmp)
        val sortedMap = mutableMapOf<String, String>()
        for (i in list.indices) {
            sortedMap[list[i].key] = list[i].value.toString()
        }
        return sortedMap.toMap()
    }
}

/**
 * 时间戳转换成字符串
 * @param pattern 时间样式 yyyy-MM-dd HH:mm:ss
 * @return [String] 时间字符串
 */
fun Long.toDateStr(pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
    val date = Date(this * 1000)
    val format = SimpleDateFormat(pattern)
    return format.format(date)
}

/**
 * 时间戳转换成字符窜
 * @return [Long]返回今天开始的时间戳
 */
fun getTimestampStartOfToday(): Long {
    val timeZone = ZoneId.systemDefault()
    val dt = ZonedDateTime.now(timeZone)
    return dt.toLocalDate().atStartOfDay(timeZone).toEpochSecond()
}

/**
 * 时间戳转换成字符窜
 * @return [Long]返回今天结束的时间戳
 */
fun getTimestampEndOfToday(): Long {
    return getTimestampStartOfToday() + 86399L
}


fun String.isNotFound(): Boolean {
    return this == "NOT_FOUND"
}