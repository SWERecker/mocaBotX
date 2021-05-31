package me.swe.main

import java.io.*
import java.net.URL
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.*
import kotlin.Comparator
import java.time.ZonedDateTime
import java.net.URLConnection
import java.util.regex.Matcher
import java.util.regex.Pattern

val protectedKeys = arrayListOf(
    "关键词", "统计图片数量", "统计次数", "语音",
    "使用说明", "青年大学习", "换lp次数", "签到",
    "抽签", "lp", "设置图片cd", "设置复读概率",
    "设置复读cd", "查看当前参数", "增加关键词",
    "添加关键词", "删除关键词"
)

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
class MapSort {
    fun mapSortByValue(toSortMap: Map<String, Int>): Map<String, String> {

        //自定义比较器
        val valCmp: java.util.Comparator<Map.Entry<String?, Int>> =
            Comparator { o1, o2 ->
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

/**
 * String.isNotFound()
 *
 * @return String == "NOT_FOUND"
 */
fun String.isNotFound(): Boolean {
    return this == "NOT_FOUND"
}

/**
 * 随机，事件是否发生
 *
 * @param possibility 概率
 *
 * @return true/false
 */
fun randomDo(possibility: Int): Boolean {
    val seed = (0..99).random()
    return seed < possibility
}

/**
 * 下载图片
 *
 * @param url 下载链接
 * @param downloadPath 下载路径
 * @param fileName 文件名
 *
 * @return 下载状态/错误内容
 */
fun downloadImage(url: String, downloadPath: String, fileName: String): Pair<String, String> {
    val urlObject = URL(url)
    var byteSum = 0
    var byteRead: Int
    try {
        val conn: URLConnection = urlObject.openConnection()
        val inStream: InputStream = conn.getInputStream()
        val fileType = conn.contentType.split("/")[1]
        if (!File(downloadPath).exists()) {
            File(downloadPath).mkdirs()
        }
        val fs = FileOutputStream(downloadPath + "${fileName}.${fileType}")
        val buffer = ByteArray(1204)
        while (inStream.read(buffer).also { byteRead = it } != -1) {
            byteSum += byteRead
            fs.write(buffer, 0, byteRead)
        }
        fs.close()
        return Pair("SUCCESS", "${fileName}.${fileType}")
    } catch (e: FileNotFoundException) {
        e.printStackTrace()
        return Pair("FAIL", e.localizedMessage)
    } catch (e: IOException) {
        e.printStackTrace()
        return Pair("FAIL", e.localizedMessage)
    } catch (e: Exception) {
        e.printStackTrace()
        return Pair("FAIL", e.localizedMessage)
    }
}

fun containSpecialChar(str: String): Boolean {
    val regEx = "[ _`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]|\n|\r|\t"
    val p = Pattern.compile(regEx)
    val m = p.matcher(str)
    return m.find()
}

fun containProtectedKeys(str: String): Boolean {
    var flag = false
    for (key in protectedKeys) {
        if (key in str) {
            flag = true
        }
    }
    return flag
}