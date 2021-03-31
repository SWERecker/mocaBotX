package me.swe.main

import net.mamoe.mirai.utils.MiraiLogger
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.io.File
import java.io.FileInputStream
import java.io.InputStream


class Moca(private val mocaDatabaseInstance: MocaDatabase) {
    val qndxxPath = System.getProperty("user.dir") + File.separator + "resource" + File.separator + "qndxx.txt"
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
                if(line[0] == arg){
                    return line[1]
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
    suspend fun loadIndexFile(): Int{
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
     * 设置cd
     *
     * @param id 群号/QQ号（作为标识符）
     * @param cdType cd类型
     * @param cdLength cd长度
     *
     */
    fun setCd(id: Long, cdType: String, cdLength: Int = 0){
        val currentTimestamp = (System.currentTimeMillis() / 1000).toInt()
        mocaLogger.info(currentTimestamp.toString())
        val cdString = "${id}_${cdType}"
        if (cdLength != 0) {
            mapMocaCd[cdString] = currentTimestamp + cdLength
            mocaLogger.info("$cdString set to ${currentTimestamp + cdLength}")
        }else {
            val configCdLength = mocaDatabaseInstance.getGroupConfig(id, cdType).toString().toInt()
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
    fun isInCd(id: Long, cdType: String): Boolean{
        val currentTimestamp = (System.currentTimeMillis() / 1000).toInt()
        println(currentTimestamp)
        val cdString = "${id}_${cdType}"
        if (cdString !in mapMocaCd.keys){
            return false
        }
        return currentTimestamp <= mapMocaCd[cdString]!!
    }

}