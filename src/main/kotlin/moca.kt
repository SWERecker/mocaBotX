package me.swe.main


import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.utils.MiraiLogger
import java.io.File
import java.io.InputStream

var picturePath: String = ""
val mocaPaPath = File("resource" + File.separator + "pa" + File.separator)
val mocaKeaiPath = File("resource" + File.separator + "keai" + File.separator)

class Moca {
    private val mocaLogger = MiraiLogger.create("MocaLogger")
    private val mocaDB = MocaDatabase()

    /**
     * 初始化，从数据库中加载所有群组的关键词列表至内存.
     */
    init {
        mocaDB.loadAllGroupKeyword()
        mocaDB.loadAllGroupConfig()
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
     * 从缓存中获取某群组的关键词列表.
     *
     * @param groupId 群号
     *
     * @return 群组的关键词列表 Map<String, String>
     *
     */
    fun getGroupKeyword(groupId: Long): Map<String, String> {
        if (groupId !in mapGroupKeywords.keys) {
            mocaDB.dbInitGroup(groupId)
        }
        return mapGroupKeywords[groupId] as Map<String, String>
    }

    /**
     * 匹配关键词，同时返回是否含双倍关键词
     *
     * @param groupId 群号
     * @param key 用户消息内容
     *
     * @return Pair(name, isDouble)
     *
     */
    fun matchKey(groupId: Long, key: String): Pair<String, Boolean> {
        var name = ""
        var isDouble = false
        val groupKeyword = getGroupKeyword(groupId)
        for ((k, v) in groupKeyword) {
            if (v != "") {
                if (Regex(v).find(key.toLowerCase()) != null) {
                    name = k
                    break
                }
            }
        }
        if (key.startsWith("多")) {
            isDouble = true
        }
        return Pair(name, isDouble)
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
        if (groupId !in mapGroupConfig.keys) {
            mocaDB.dbInitGroup(groupId)
        }
        val groupConfig = mapGroupConfig[groupId] as Map<*, *>
        return groupConfig[arg]
    }

    /**
     * 从数据库检查参数是否启用
     *
     * @param groupId 群号
     * @param arg 参数名称
     *
     * @return 参数值
     *
     */
    fun groupConfigEnabled(groupId: Long, arg: String): Boolean {
        val groupConfig = getGroupConfig(groupId, arg) ?: return true
        return groupConfig.toString().toInt() == 1
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
     * 设置用户的lp
     *
     * @param userId 用户QQ号
     * @param preProcessedContent 用户消息
     *
     */
    fun setUserLp(groupId: Long, userId: Long, preProcessedContent: String): MessageChain {
        val toSetLpName = preProcessedContent.substringAfter("是")
        val existUserLp = getUserLp(userId)
        if (toSetLpName.replace("？", "?").contains("?") ||
            toSetLpName.contains("谁")
        ) {
            return if (existUserLp == "NOT_SET") {
                buildMessageChain {
                    +At(userId)
                    +PlainText(" 您还没有设置lp呢，用【wlp是xxx】来设置一个吧~")
                }

            } else {
                buildMessageChain {
                    +At(userId)
                    +PlainText(" 您设置的lp是：${existUserLp}")
                }
            }
        }
        if (existUserLp == toSetLpName) {
            return buildMessageChain {
                +At(userId)
                +PlainText(" 您已经将${existUserLp}设置为您的lp了，请勿重复设置哦~")
            }
        }
        val groupKeyword = getGroupKeyword(groupId)
        return if (toSetLpName in groupKeyword.keys) {
            mocaDB.setConfig(userId, "USER", "lp", toSetLpName)
            buildMessageChain {
                +At(userId)
                +PlainText(" 设置lp为：$toSetLpName")
            }
        } else {
            val matchResult = matchLp(toSetLpName, groupKeyword)
            if (matchResult.isNullOrEmpty()) {
                buildMessageChain {
                    +At(userId)
                    +PlainText("\n没有在此群找到您要设置的lp哦~\n")
                    +PlainText("请发送【@摩卡 关键词列表】查看可设置的关键词列表哦~")
                }
            } else {
                var resultString = "\n未找到${toSetLpName}，您要设置的可能是：\n"
                matchResult.forEach { (name, _) ->
                    resultString += name + "\n"
                }
                resultString = resultString.trimEnd()
                buildMessageChain {
                    +At(userId)
                    +PlainText(resultString)
                }
            }
        }
    }

    /**
     * 匹配lp
     *
     * @param lpName lp名称
     * @param groupKeyword 群关键词列表
     *
     */
    private fun matchLp(lpName: String, groupKeyword: Map<String, String>): MutableMap<String, Float> {
        val matchString = StringSimilarity()
        val matchResult = mutableMapOf<String, Float>()
        groupKeyword.forEach { (name, keys) ->
            val keyList = keys.split('|')
            keyList.forEach {
                val similarity = matchString.compareString(it, lpName)
                if (similarity > 0.5) {
                    println("$it, $lpName 相似度：$similarity")
                    matchResult[name] = similarity
                }
            }
        }
        return matchResult
    }

    /**
     * 从Redis加载图片数量统计
     *
     * @param groupId: 群组ID
     *
     * @return 返回排序后的Map
     */
    private fun getPictureCount(groupId: Long): Map<String, String> {
        val groupKeyword = getGroupKeyword(groupId)
        val pictureCount = mutableMapOf<String, Int>()
        val mapSorter = MapSort()
        redisPool.resource.use { r ->
            r.select(3)
            groupKeyword.forEach { (name, _) ->
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
        val currentTimestamp = System.currentTimeMillis() / 1000
        val cdString = "${id}_${cdType}"
        if (cdLength != 0) {
            mapMocaCd[cdString] = currentTimestamp + cdLength.toLong()
            mocaLogger.info("$cdString set to ${currentTimestamp + cdLength}")
        } else {
            val configCdLength = getGroupConfig(id, cdType).toString().toLong()
            mapMocaCd[cdString] = currentTimestamp + configCdLength
            mocaLogger.info("$cdString +$configCdLength")
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
        val currentTimestamp = System.currentTimeMillis() / 1000
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
        val queryResult = mocaDB.getUserConfig(userId, "clp_time").toString()
        return try {
            queryResult.toInt()
        } catch (e: NumberFormatException) {
            0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 获取用户设置的lp
     *
     * @param userId 用户QQ号.
     *
     * @return 未设置："NOT_SET"; 正常返回设置的lp名称
     */
    fun getUserLp(userId: Long): String {
        val userLp = mocaDB.getUserConfig(userId, "lp") as String
        if (userLp == "NOT_FOUND") {
            return "NOT_SET"
        }
        return userLp
    }

    /**
     * 设置群功能参数.
     *
     * @param groupId 群号.
     * @param paraName 参数名称
     * @param operation 具体操作
     *
     * @return 设置结果(t/f)
     */
    fun setGroupFunction(groupId: Long, paraName: String, operation: Any): Boolean {
        return mocaDB.setConfig(groupId, "GROUP", paraName, operation)
    }

    /**
     * 创建keyword图片.
     *
     * @param groupId 群号
     *
     * @return 图像的绝对路径
     */
    fun buildGroupKeywordPicture(groupId: Long): String {
        val imageMaker = MultiLineTextToImage
        return imageMaker.buildImage(
            "关键词列表",
            "${groupId}_keyword.png",
            getGroupKeyword(groupId)
        )
    }

    /**
     * 创建图像数量统计的图片.
     *
     * @param groupId 群号
     *
     * @return 图像的绝对路径
     */
    fun buildGroupPictureCount(groupId: Long): String {
        val imageMaker = MultiLineTextToImage
        return imageMaker.buildImage(
            "图片数量统计",
            "${groupId}_pic_count.png",
            getPictureCount(groupId)
        )
    }

    /**
     * 创建群统计次数的图片.
     *
     * @param groupId 群号
     *
     * @return 图像的绝对路径
     */
    fun buildGroupCountPicture(groupId: Long): String {
        val imageMaker = MultiLineTextToImage
        return imageMaker.buildImage(
            "次数统计",
            "${groupId}_count.png",
            mocaDB.getGroupCount(groupId)
        )
    }

    /**
     * 检查某QQ是否为Superman
     *
     * @return true/false(是/不是Superman)
     */
    fun isSuperman(userId: Long): Boolean {
        val supermanId = mutableListOf<Long>()
        getBotConfig("SUPERMAN").split(',').also {
            for (str in it) {
                supermanId.add(str.toLong())
            }
        }
        return userId in supermanId
    }

    /**
     * 调用mocaDB.dbInitGroup
     */
    fun initGroup(id: Long) {
        mocaDB.dbInitGroup(id)
    }

    /**
     * 调用mocaDB.updateGroupCount
     */
    fun updateCount(groupId: Long, name: String) {
        mocaDB.updateGroupCount(groupId, name)
    }

    /**
     * 获取用户面包数量
     *
     * @param userId 用户QQ号
     *
     * @return 面包数量(Int)
     */
    fun getUserPan(userId: Long): Int {
        val userPan = mocaDB.getUserConfig(userId, "pan").also {
            if (it == "NOT_FOUND") {
                return 0
            }
        }
        return userPan.toString().toInt()
    }

    /**
     * 设置用户面包数量
     *
     * @param userId 用户QQ号
     * @param panNumber 要设置的面包数量
     *
     * @return true/false(成功/失败)
     */
    private fun setUserPan(userId: Long, panNumber: Int): Boolean {
        return mocaDB.setConfig(userId, "USER", "pan", panNumber)
    }

    /**
     * 改变用户面包数量
     *
     * @param userId 用户QQ号
     * @param delta 要设置的面包数量
     *
     * @return Pair(true/false(成功/失败), 改变后面包数量)
     */
    private fun panNumberModify(userId: Long, delta: Int): Pair<Boolean, Int> {
        val userPan = getUserPan(userId)
        return if (delta > 0) {
            setUserPan(userId, userPan + delta)
            Pair(true, getUserPan(userId))
        } else {
            if (-delta > userPan) {
                mocaLogger.info("User $userId Pan not enough($userPan < ${-delta})")
                Pair(false, userPan)
            } else {
                setUserPan(userId, userPan + delta)
                Pair(true, getUserPan(userId))
            }
        }
    }

    /**
     * 买面包
     *
     *
     *
     * @param userId 用户QQ号
     *
     * @return Pair(购买状态, 参数值)
     *
     * 购买状态备注，参数值
     *
     *  -1(在买面包cd中), 还需要等待的时间(s)
     *
     *  1~10(初次购买(此次买面包的数量)), 买完后拥有的面包数量
     *
     *  10~20(购买成功(10 + 此次买面包的数量)), 买完后拥有的面包数量
     *
     *
     */
    fun buyPan(userId: Long): Pair<Int, Long> {
        val currentTimestamp = System.currentTimeMillis() / 1000
        mocaDB.getUserConfig(userId, "last_buy_time").also {
            return if (it == "NOT_FOUND") {
                mocaDB.setConfig(userId, "USER", "last_buy_time", currentTimestamp)
                val buyCount = (1..10).random()
                val modifyResult = panNumberModify(userId, buyCount)
                Pair(buyCount, modifyResult.second.toLong())
            } else {
                val userLastBuyTime = it.toString().toInt()
                if (currentTimestamp - userLastBuyTime < 3600) {
                    Pair(-1, 3600L + userLastBuyTime - currentTimestamp)
                } else {
                    mocaDB.setConfig(userId, "USER", "last_buy_time", currentTimestamp)
                    val buyCount = (1..10).random()
                    val modifyResult = panNumberModify(userId, buyCount)
                    Pair(10 + buyCount, modifyResult.second.toLong())
                }
            }
        }
    }

    /**
     * 吃面包.
     *
     * @param userId 用户QQ号
     *
     * @return Pair(状态（成功/失败）, 剩余面包数量)
     */
    fun eatPan(userId: Long, panNumber: Int): Pair<Boolean, Int> {
        return panNumberModify(userId, -panNumber)
    }

    /**
     * 签到.
     *
     * @param userId 用户QQ号
     *
     * @return arrayListOf(状态, 上次签到时间/签到时间, 合计签到天数, 用户面包数)
     */
    fun userSignIn(userId: Long): MutableList<Any> {
        val tempLastSignInTime = mocaDB.getUserConfig(userId, "signin_time").toString()
        var lastSignInTime = 0L
        if (!tempLastSignInTime.isNotFound()) {
            lastSignInTime = tempLastSignInTime.toLong()
        }
        if (lastSignInTime > getTimestampStartOfToday() && lastSignInTime < getTimestampEndOfToday()) {
            return arrayListOf(-1, lastSignInTime, 0, 0)
        }

        val tempUserPan = mocaDB.getUserConfig(userId, "pan").toString()
        var userOwnPan = 0
        val signInTime = System.currentTimeMillis() / 1000
        var sumSignInDay = 0
        if (!tempUserPan.isNotFound()) {
            userOwnPan = tempUserPan.toInt()
        }
        val tempSumDay = mocaDB.getUserConfig(userId, "sum_day").toString()
        if (!tempSumDay.isNotFound()) {
            sumSignInDay = tempSumDay.toInt()
        }
        userOwnPan += 5
        // println("lastSignInTime = $lastSignInTime")
        // println("userOwnPan += 5, = $userOwnPan")
        // println("sumSignInDay = $sumSignInDay")
        return if (tempLastSignInTime.isNotFound()) {
            sumSignInDay += 1
            mocaDB.setConfig(userId, "USER", "signin_time", signInTime)
            mocaDB.setConfig(userId, "USER", "sum_day", sumSignInDay)
            val panResult = panNumberModify(userId, 5)
            arrayListOf(1, signInTime, sumSignInDay, panResult.second)
        } else {
            sumSignInDay += 1
            mocaDB.setConfig(userId, "USER", "signin_time", signInTime)
            mocaDB.setConfig(userId, "USER", "sum_day", sumSignInDay)
            val panResult = panNumberModify(userId, 5)
            arrayListOf(0, signInTime, sumSignInDay, panResult.second)
        }
    }

    /**
     * 签到.
     *
     * @param userId 用户QQ号
     * @param groupId 群号
     *
     * @return arrayListOf(状态, 抽签时间/上次抽签时间, 今日运势, 今日幸运数字, lpImagePath)
     */
    fun userDraw(userId: Long, groupId: Long): MutableList<Any> {
        val tempDrawTime = mocaDB.getUserConfig(userId, "draw_time").toString()
        val lastDrawTime: Long
        if (!tempDrawTime.isNotFound()) {
            lastDrawTime = tempDrawTime.toLong()
            if (lastDrawTime > getTimestampStartOfToday() && lastDrawTime < getTimestampEndOfToday()) {
                val todayDrawStatus = mocaDB.getUserConfig(userId, "today_draw_status").toString()
                val todayLuckyNumber = mocaDB.getUserConfig(userId, "today_lucky_num").toString()
                return arrayListOf(-1, lastDrawTime, todayDrawStatus, todayLuckyNumber, "")
            }
        }
        val usePanResult = panNumberModify(userId, -2)
        if (!usePanResult.first) {
            return arrayListOf(-2, 0, "", "", "")
        }
        val drawTime = System.currentTimeMillis() / 1000
        val luckyNumber = (1..10).random()
        val luckString = arrayListOf(
            "大吉~~", "大吉~", "大吉~~", "大吉~",
            "中吉", "中吉", "中吉", "中吉", "中吉", "中吉",
            "中吉", "中吉", "中吉", "中吉", "中吉", "中吉",
            "小吉", "小吉", "小吉", "小吉",
            "吉", "吉", "吉", "吉", "吉",
            "末吉", "末吉", "末吉", "末吉",
            "凶..."
        ).random()
        val userLp = getUserLp(userId)
        val pictureFile = if (userLp in getGroupKeyword(groupId).keys) {
            randomPicture(userLp, 1)[0]
        } else {
            ""
        }
        mocaDB.setConfig(userId, "USER", "draw_time", drawTime)
        mocaDB.setConfig(userId, "USER", "today_draw_status", luckString)
        mocaDB.setConfig(userId, "USER", "today_lucky_num", luckyNumber)
        return arrayListOf(0, drawTime, luckString, luckyNumber, pictureFile)
    }

    fun keywordEdit(groupId: Long, paras: List<String>, operation: String = "ADD"): MessageChain {
        val groupKeyword = getGroupKeyword(groupId).toMutableMap()
        val (name, key) = paras
        if (name !in groupKeyword.keys) {
            return PlainText("错误：未找到“${name}”，请检查是名称否正确").toMessageChain()
        }
        val existKeys = groupKeyword[name]
        val keysList = groupKeyword[name]?.split("|")?.toMutableList()
        if (keysList != null) {
            when (operation) {
                "ADD" -> {
                    return if (existKeys?.let { Regex(it).find(key.toLowerCase()) } != null) {
                        PlainText("错误：“${name}”中已存在能够识别“${key}”的关键词").toMessageChain()
                    } else {
                        keysList.add(key)
                        groupKeyword[name] = keysList.joinToString("|")
                        mocaDB.saveGroupKeyword(groupId, groupKeyword.toMap())
                        PlainText("成功向“${name}”中添加了关键词“${key}”").toMessageChain()
                    }
                }
                "REMOVE" -> {
                    keysList.remove(key).also {
                        return if (it) {
                            groupKeyword[name] = keysList.joinToString("|")
                            mocaDB.saveGroupKeyword(groupId, groupKeyword.toMap())
                            PlainText("成功删除了关键词“${key}”").toMessageChain()
                        } else {
                            PlainText("${name}中未找到关键词“${key}”").toMessageChain()
                        }
                    }
                }
            }
        }
        return PlainText("null").toMessageChain()
    }

    suspend fun submitPictures(groupId: Long, images: List<Image>, category: String): String {
        var successImageCount = 0
        var exceptionImageCount = 0
        var exceptionMessage = ""
        val imageCategory = if (category == "") {
            "${groupId}/UNDEFINED"
        } else {
            "${groupId}/${category}"
        }
        images.forEach { image ->
            val imageId = image.imageId
            val imageUrl = image.queryUrl()
            val fileName = imageId.substring(imageId.indexOf("{") + 1, imageId.indexOf("}") - 1)
            downloadImage(imageUrl, imageCategory, fileName).also {
                if (it == "SUCCESS") {
                    successImageCount++
                } else {
                    exceptionImageCount++
                    exceptionMessage += it + "\n"
                }
            }
        }
        var result = "提交图片：成功${successImageCount}张"
        if (exceptionImageCount != 0) {
            result += "，失败${exceptionImageCount}张\n发生错误：\n" + exceptionMessage
        }
        return result
    }
}
