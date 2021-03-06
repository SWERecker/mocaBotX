package me.swe.main

import com.google.gson.GsonBuilder
import com.google.gson.LongSerializationPolicy
import com.google.gson.reflect.TypeToken
import java.awt.Color
import java.awt.Font
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.*
import java.math.BigDecimal
import java.net.URL
import java.net.URLConnection
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.regex.Pattern
import javax.imageio.ImageIO


val protectedKeys = arrayListOf(
    "关键词", "统计图片数量", "统计次数", "语音",
    "使用说明", "青年大学习", "换lp次数", "签到",
    "抽签", "lp", "设置图片cd", "设置复读概率",
    "设置复读cd", "查看当前参数", "增加关键词",
    "添加关键词", "删除关键词"
)

val Slash: String = File.separator

val mocaDB = MocaDatabase()
val moca = Moca()
val apex = Apex()

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

/**
 * 读取config.txt中的参数
 *
 * 存储格式为
 * X=Y
 *
 * @param arg X
 *
 * @return Y
 */
fun getBotConfig(arg: String): String {
    val inStream: InputStream = File("config.txt").inputStream()
    inStream.bufferedReader().useLines { lines ->
        lines.forEach {
            val line = it.split('=')
            if (line[0] == arg) {
                return line[1].replace("\\n", "\n")
            }
        }
    }
    mocaLogger.error("GetBotConfig: Arg $arg not found.")
    return ""
}

/**
 * 将Json转换为Map<String?, Any?>
 *  @return Map<String?, Any?>
 */
fun String.jsonToMap(): Map<String?, Any?> {
    val gson = GsonBuilder()
        .setLongSerializationPolicy(LongSerializationPolicy.STRING)
        .create()
    return gson.fromJson(this, object: TypeToken<Map<String?, Any?>?>() {}.type)
}

fun String.sciToLong(): Long {
    val num = BigDecimal(this)
    val df = DecimalFormat("0")
    return df.format(num).toLong()
}

fun String.sciToInt(): Int {
    val num = BigDecimal(this)
    val df = DecimalFormat("0")
    return df.format(num).toInt()
}

/**
 * 将多行文字转换成图像
 */
object MultiLineTextToImage {
    @JvmStatic
    fun buildImage(title: String, toSaveFileName: String, inputMap: Map<String, String>?): String {
        var inputString = ""
        inputMap?.forEach {
            inputString += "${it.key}    ${it.value}\n"
        }
        var img = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        var g2d = img.createGraphics()
        val toUseFont = Font.createFont(Font.TRUETYPE_FONT, File("resource" + Slash + "PingFang.ttf"))
        val fontSized = toUseFont.deriveFont(16f)
        val titleFont = Font.createFont(Font.TRUETYPE_FONT, File("resource" + Slash + "PingFang.ttf"))
        val titleSized  = titleFont.deriveFont(24f)
        g2d.dispose()
        val inputStringArray = inputString.split("\n")
        var stringLines = 0
        var maxStringLength = 0

        inputStringArray.forEach {
            stringLines++
            if (g2d.fontMetrics.getStringBounds(it, g2d).width.toInt() > maxStringLength) {
                maxStringLength = g2d.fontMetrics.getStringBounds(it, g2d).width.toInt()
            }
        }
        val imageHeight = stringLines * g2d.getFontMetrics(fontSized).height + 50
        val imageWidth = maxStringLength + 400
        img = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB)
        g2d = img.createGraphics()

        val bgImage: Image = ImageIO.read(File("resource" + Slash + "bg.png"))
        val bgImageHeight = bgImage.getHeight(null)
        val bgImageWidth = bgImage.getWidth(null)
        var bgImageDrawPosX = 0
        var bgImageDrawPosY = 0
        while (bgImageDrawPosY < imageHeight) {
            while (bgImageDrawPosX < imageWidth) {
                g2d.drawImage(bgImage, bgImageDrawPosX, bgImageDrawPosY, bgImageWidth, bgImageHeight, null)
                // println("draw pos: X=$bgImageDrawPosX, Y=$bgImageDrawPosY")
                bgImageDrawPosX += bgImageWidth
            }
            bgImageDrawPosX = 0
            bgImageDrawPosY += bgImageHeight
        }

        g2d.setRenderingHint(
            RenderingHints.KEY_ALPHA_INTERPOLATION,
            RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY
        )
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE)
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_DEFAULT)
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g2d.color = Color.BLACK
        g2d.font = titleSized
        g2d.drawString(title, 40, g2d.getFontMetrics(titleSized).height)
        g2d.font = fontSized
        var linePosition = 60
        val fontSize = g2d.font.size
        // println("font height = $fontSize")
        inputMap?.forEach {
            val beautifiedValue = it.value.replace("|", " | ")
            g2d.drawString(it.key, 10, linePosition)
            g2d.drawString(beautifiedValue, 150, linePosition)
            linePosition += fontSize + 5
        }
        g2d.dispose()
        try {
            val saveImage = File("cache" + Slash + toSaveFileName)
            ImageIO.write(img, "png", saveImage)
            // println(saveImage.absolutePath)
            return saveImage.absolutePath
        } catch (ex: IOException) {
            ex.printStackTrace()
        }
        return ""
    }
}

class SignInResult {
    /**
     * 签到状态
     */
    var signInCode = 0

    /**
     * 签到时间
     */
    var signInTime = 0L

    /**
     * 累计签到天数
     */
    var sumSignInDays = 0

    /**
     * 签到后的面包数量
     */
    var newPanNumber = 0

    /**
     * 今日第x个签到
     */
    var numberOfDay = 0

}

class DrawResult {
    /**
     * 抽签状态码
     */
    var drawCode = 0

    /**
     * 抽签时间
     */
    var drawTime = 0L

    /**
     * 抽签内容
     */
    var luckString = ""

    /**
     * 幸运数字
     */
    var luckyNumber = 0

    /**
     * 附赠的lp图片路径
     */
    var pictureFile = ""

}

class PanModifyResult{
    /**
     * 状态（成功/失败）
     */
    var status: Boolean = false
    /**
     * 修改后面包数量
     */
    var newPanNumber = 0
}

class BuyPanResult{
    /**
     * 状态（成功/失败）
     */
    var status = false
    /**
     * 是否初次购买
     */
    var isFirstTime = false
    /**
     * 购买数量
     */
    var buyNumber = 0
    /**
     * 在下次购买前需要等待的时间（秒）
     */
    var secondsToWait = 0L
    /**
     * 购买后面包数量
     */
    var newPanNumber = 0
}