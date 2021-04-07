package me.swe.main


import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.buildMessageChain
import net.mamoe.mirai.utils.MiraiLogger
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import javax.imageio.ImageIO
import java.io.IOException


class Moca(private val mocaDatabaseInstance: MocaDatabase) {
    private val picturePath = "E:${File.separator}mirai${File.separator}pic${File.separator}"
    private val indexFilePath = picturePath + "index.txt"
    val supermanId = arrayOf(565379987L, 1400625889L)
    private val redisPool = JedisPool(JedisPoolConfig())
    private val mocaLogger = MiraiLogger.create("MocaLogger")
    private val mapMocaCd = mutableMapOf<String, Int>()

    init {

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
        mocaLogger.error("Arg $arg not found.")
        return ""
    }

    /**
     * 从Redis数据库中随机图片
     *
     * @param name 名称
     * @param pictureCount 随机数量
     *
     * @return 含有图像路径的Array
     */
    fun randomPicture(name: String, pictureCount: Int): MutableList<String> {
        redisPool.resource.use { r ->
            r.select(3)
            val randomResult = r.srandmember(name, pictureCount)
            mocaLogger.debug(randomResult.toString())
            return randomResult
        }
    }

    /**
     * 从index.txt加载所有图片路径至Redis数据库
     */
    fun loadIndexFile(): Int {
        redisPool.resource.use { r ->
            r.select(3)
            r.flushDB()
            val inStream: InputStream = File(indexFilePath).inputStream()
            inStream.bufferedReader().useLines { lines ->
                lines.forEach {
                    val line = it.split('|')
                    val filePath = picturePath + line[0]
                    val categories = line[1].split(' ')
                    categories.forEach { category ->
                        r.sadd(category, filePath)
                    }
                }
            }
            var peopleCount = 0
            r.keys("*").forEach { _ ->
                peopleCount++
            }
            return peopleCount
        }
    }

    /**
     * 从Redis加载图片数量统计
     *
     * @param groupId: 群组ID
     *
     * @return 返回排序后的Map
     */
    private fun getPictureCount(groupId: Long): Map<String, String>{
        val groupKeyword = mocaDatabaseInstance.getGroupKeyword(groupId)
        val pictureCount = mutableMapOf<String, Int>()
        val mapSorter = MapSort()
        redisPool.resource.use { r ->
            r.select(3)
            groupKeyword.forEach{ (name, _) ->
                pictureCount[name] = r.scard(name).toInt()
            }
        }
        return mapSorter.mapSortByValue(pictureCount)
    }

    /**
     * 设置cd
     *
     * @param id 群号/QQ号（作为标识符）
     * @param cdType cd类型
     * @param cdLength cd长度
     *
     */
    fun setCd(id: Long, cdType: String, cdLength: Int = 0) {
        val currentTimestamp = (System.currentTimeMillis() / 1000).toInt()
        mocaLogger.info(currentTimestamp.toString())
        val cdString = "${id}_${cdType}"
        if (cdLength != 0) {
            mapMocaCd[cdString] = currentTimestamp + cdLength
            mocaLogger.info("$cdString set to ${currentTimestamp + cdLength}")
        } else {
            val configCdLength = getGroupConfig(id, cdType).toString().toInt()
            mocaLogger.info(configCdLength.toString())
            mapMocaCd[cdString] = currentTimestamp + configCdLength
            mocaLogger.info("$cdString set to ${currentTimestamp + configCdLength}")
        }

    }

    /**
     * 判断是否在cd中
     *
     * @param id 群号/QQ号（作为标识符）
     * @param cdType cd类型
     *
     * @return 返回true/false（在/不在cd中）
     */
    fun isInCd(id: Long, cdType: String): Boolean {
        val currentTimestamp = (System.currentTimeMillis() / 1000).toInt()
        val cdString = "${id}_${cdType}"
        if (cdString !in mapMocaCd.keys) {
            return false
        }
        return currentTimestamp <= mapMocaCd[cdString]!!
    }

    /**
     * 返回换lp次数
     *
     * @param userId QQ号
     *
     * @return 返回次数
     */
    fun getChangeLpTimes(userId: Long): Int {
        val queryResult = mocaDatabaseInstance.getUserConfig(userId, "clp_time").toString()
        return try {
            queryResult.toInt()
        } catch (e: NumberFormatException) {
            0
        }
    }

    /**
     * 从数据库中获取相应群参数.
     *
     * @param groupId 群号
     * @param arg 参数名称
     *
     * @return 参数值
     *
     */
    fun getGroupConfig(groupId: Long, arg: String): Any? {
        if (groupId !in mocaDatabaseInstance.mapGroupConfig.keys) {
            mocaDatabaseInstance.initGroup(groupId)
        }
        val groupConfig = mocaDatabaseInstance.mapGroupConfig[groupId] as Map<*, *>
        return groupConfig[arg]
    }

    /**
     * 获取用户设置的lp
     *
     * @param userId 用户QQ号.
     *
     * @return 未设置："NOT_SET"; 正常返回设置的lp名称
     */
    fun getUserLp(userId: Long): String {
        val userLp = mocaDatabaseInstance.getUserConfig(userId, "lp") as String
        if (userLp == "NOT_FOUND") {
            return "NOT_SET"
        }
        return userLp
    }

    fun setGroupFunction(id: Long, paraName: String, operation: Any): Boolean {
        return mocaDatabaseInstance.setConfig(id, "GROUP", paraName, operation)
    }

    fun buildGroupKeywordPicture(groupId: Long): String {
        val imageMaker = MultiLineTextToImage
        return imageMaker.buildImage("关键词列表",
            "${groupId}_keyword.png",
            mocaDatabaseInstance.getGroupKeyword(groupId)
        )
    }

    fun buildGroupPictureCount(groupId: Long): String {
        val imageMaker = MultiLineTextToImage
        return imageMaker.buildImage("图片数量统计",
            "${groupId}_pic_count.png",
            getPictureCount(groupId)
        )
    }

    fun buildGroupCountPicture(groupId: Long): String {
        val imageMaker = MultiLineTextToImage
        return imageMaker.buildImage("次数统计",
            "${groupId}_count.png",
            mocaDatabaseInstance.getGroupCount(groupId)
        )
    }

}


object MultiLineTextToImage {
    @JvmStatic
    fun buildImage(title: String, toSaveFileName: String, inputMap: Map<String, String>?): String {
        var inputString = ""
        inputMap?.forEach {
            inputString += "${it.key}    ${it.value}\n"
        }
        var img = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        var g2d = img.createGraphics()
        val toUseFont = Font("", Font.PLAIN, 16)
        val titleFont = Font("", Font.PLAIN, 24)
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
        val imageHeight = stringLines * g2d.getFontMetrics(toUseFont).height + 50
        val imageWidth = maxStringLength + 400
        img = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB)
        g2d = img.createGraphics()

        val bgImage: Image = ImageIO.read(File("resource" + File.separator + "bg.png"))
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
        g2d.font = titleFont
        g2d.drawString(title, 40, g2d.getFontMetrics(titleFont).height)
        g2d.font = toUseFont
        var linePosition = 60
        val fontSize = g2d.font.size
        println("font height = $fontSize")
        inputMap?.forEach {
            val beautifiedValue = it.value.replace("|", " | ")
            g2d.drawString(it.key, 10, linePosition)
            g2d.drawString(beautifiedValue, 150, linePosition)
            linePosition += fontSize + 5
        }
        g2d.dispose()
        try {
            val saveImage = File("cache" + File.separator + toSaveFileName)
            ImageIO.write(img, "png", saveImage)
            // println(saveImage.absolutePath)
            return saveImage.absolutePath
        } catch (ex: IOException) {
            ex.printStackTrace()
        }
        return ""
    }
}