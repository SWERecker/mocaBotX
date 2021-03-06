package me.swe.main


import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.utils.ExternalResource.Companion.sendAsImageTo
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.bson.Document
import java.io.File

var picturePath: String = ""
val mocaPaPath = File("resource" + Slash + "pa" + Slash)
val mocaKeaiPath = File("resource" + Slash + "keai" + Slash)

class Moca {
    val groupPicture = GroupPicture()
    val pan = Pan()
    /**
     * 初始化，从数据库中加载所有群组的关键词列表至内存.
     */
    init {
        mocaDB.loadAllGroupKeyword()
        mocaDB.loadAllGroupConfig()
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
                if (Regex(v).matches(key.lowercase())) {
                    name = k
                    break
                }
            }
        }

        if (name == "") {
            for ((k, v) in groupKeyword) {
                if (v != "") {
                    if (Regex(v).find(key.lowercase()) != null) {
                        name = k
                        break
                    }
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
        val groupConfig = getGroupConfig(groupId, arg) ?: return false
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
            moca.setUserConfig(userId, "lp", toSetLpName)
            mocaDB.updateUserChangeLpTimes(userId)
            buildMessageChain {
                +At(userId)
                +PlainText(" 设置lp为：$toSetLpName")
            }
        } else {
            val matchResult = matchLp(toSetLpName, groupKeyword)
            if (matchResult.isEmpty()) {
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
                    mocaLogger.debug("$it, $lpName 相似度：$similarity")
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
            val finalCdLength = if (cdLength > 8) cdLength else 8
            mapMocaCd[cdString] = currentTimestamp + finalCdLength.toLong()
            mocaLogger.debug("$id: $cdType set to ${currentTimestamp + finalCdLength}")
        } else {
            val configCdLength = getGroupConfig(id, cdType).toString().toInt()
            val finalCdLength = if (configCdLength > 8) configCdLength else 8
            mapMocaCd[cdString] = currentTimestamp + finalCdLength.toLong()
            mocaLogger.debug("$id: $cdType +$finalCdLength")
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
        return mocaDB.getUserConfigInt(userId, "clp_time")
    }

    /**
     * 获取用户设置的lp
     *
     * @param userId 用户QQ号.
     *
     * @return 未设置：NOT_SET 正常返回设置的lp名称
     */
    fun getUserLp(userId: Long): String {
        val userLp = mocaDB.getUserConfig(userId, "lp")
        if (userLp.isNotFound()) {
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
    fun buildGroupKeywordImage(groupId: Long): String {
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
    fun buildGroupPictureCountImage(groupId: Long): String {
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
    fun buildGroupCountImage(groupId: Long): String {
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
        val supermanIds = mutableListOf<Long>()
        getBotConfig("SUPERMAN").split(',').also {
            for (str in it) {
                supermanIds.add(str.toLong())
            }
        }
        return userId in supermanIds
    }

    /**
     * 调用mocaDB.getGroupPicKeywords
     */
    fun getGroupPicKeyword(groupId: Long): String {
        return mocaDB.getGroupPicKeywords(groupId)
    }

    /**
     * 买面包
     *
     * @param userId 用户QQ号
     *
     * @return BuyPanResult
     *
     * status = true/false
     *
     * isFirstTime = true/false
     *
     * buyNumber: 购买的面包数量
     *
     * secondsToWait: 在cd中时还需等待的时间
     *
     * newPanNumber: 购买成功后新的面包数量
     *
     */
    fun buyPan(userId: Long): BuyPanResult {
        val currentTimestamp = System.currentTimeMillis() / 1000
        val buyPanResult = BuyPanResult()
        mocaDB.getUserConfig(userId, "last_buy_time").also {
            return if (it.isNotFound()) {
                // first buy
                moca.setUserConfig(userId, "last_buy_time", currentTimestamp)
                val buyCount = (1..10).random()
                val modifyResult = pan.modifyUserPan(userId, buyCount)
                mocaDB.mocaLog("UserBuyPan", targetId = userId, description = "firstTime = True")
                buyPanResult.apply {
                    status = true
                    isFirstTime = true
                    buyNumber = buyCount
                    newPanNumber = modifyResult.newPanNumber
                }
            } else {
                val userLastBuyTime = it.toInt()
                if (currentTimestamp - userLastBuyTime < 3600) {
                    // in cd
                    buyPanResult.apply {
                        secondsToWait = 3600L + userLastBuyTime - currentTimestamp
                    }
                } else {
                    // success buy
                    moca.setUserConfig(userId, "last_buy_time", currentTimestamp)
                    val buyCount = (1..10).random()
                    val modifyResult = pan.modifyUserPan(userId, buyCount)
                    mocaDB.mocaLog("UserBuyPan", targetId = userId, description = "firstTime = False")
                    buyPanResult.apply {
                        status = true
                        buyNumber = buyCount
                        newPanNumber = modifyResult.newPanNumber
                    }
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
    fun eatPan(userId: Long, panNumber: Int): PanModifyResult {
        return Pan().modifyUserPan(userId, -panNumber)
    }

    /**
     * 签到.
     *
     * @param userId 用户QQ号
     *
     * @return SignInResult
     */
    fun userSignIn(userId: Long): SignInResult {
        val signInResult = SignInResult()
        val tempLastSignInTime = mocaDB.getUserConfig(userId, "signin_time")
        val lastSignInTime = if (!tempLastSignInTime.isNotFound()) {
            tempLastSignInTime.toLong()
        } else {
            0L
        }
        if (lastSignInTime > getTimestampStartOfToday() && lastSignInTime < getTimestampEndOfToday()) {
            signInResult.apply {
                signInCode = -1
                signInTime = lastSignInTime
                sumSignInDays = 0
                newPanNumber = 0
            }
            return signInResult
        }

        val tempSumDay  = mocaDB.getUserConfig(userId, "sum_day")
        val signInTime = System.currentTimeMillis() / 1000
        var userOwnPan = 0
        var sumSignInDay = 0

        if (!tempSumDay.isNotFound()) {
            sumSignInDay = tempSumDay.toInt()
        }
        userOwnPan += 5

        return if (tempLastSignInTime.isNotFound()) {
            sumSignInDay += 1
            moca.setUserConfig(userId, "signin_time", signInTime)
            moca.setUserConfig(userId, "sum_day", sumSignInDay)
            val panResult = pan.modifyUserPan(userId, 5)
            mocaDB.mocaLog("UserSignIn", targetId = userId,
                description = "firstTime = True, sumSignInDay = $sumSignInDay, userOwnPan = $userOwnPan")
            val orderOfDay = mocaDB.incUserSignInCount()
            signInResult.apply {
                signInCode = 1
                this.signInTime = signInTime
                sumSignInDays = sumSignInDay
                newPanNumber = panResult.newPanNumber
                numberOfDay = orderOfDay
            }
        } else {
            sumSignInDay += 1
            moca.setUserConfig(userId, "signin_time", signInTime)
            moca.setUserConfig(userId, "sum_day", sumSignInDay)
            val panResult = pan.modifyUserPan(userId, 5)
            mocaDB.mocaLog("UserSignIn", targetId = userId,
                description = "firstTime = False, sumSignInDay = $sumSignInDay, userOwnPan = $userOwnPan")
            val orderOfDay = mocaDB.incUserSignInCount()
            signInResult.apply {
                signInCode = 0
                this.signInTime = signInTime
                sumSignInDays = sumSignInDay
                newPanNumber = panResult.newPanNumber
                numberOfDay = orderOfDay
            }
        }
    }

    /**
     * 抽签.
     *
     * @param userId 用户QQ号
     * @param groupId 群号
     *
     * @return arrayListOf(状态, 抽签时间/上次抽签时间, 今日运势, 今日幸运数字, lpImagePath)
     */
    fun userDraw(userId: Long, groupId: Long): DrawResult {
        val tempDrawTime = mocaDB.getUserConfig(userId, "draw_time")
        val lastDrawTime: Long
        if (!tempDrawTime.isNotFound()) {
            lastDrawTime = tempDrawTime.toLong()
            if (lastDrawTime > getTimestampStartOfToday() && lastDrawTime < getTimestampEndOfToday()) {
                val todayDrawStatus = mocaDB.getUserConfig(userId, "today_draw_status")
                val todayLuckyNumber = mocaDB.getUserConfigInt(userId, "today_lucky_num")
                val drawResult = DrawResult()
                drawResult.apply {
                    drawCode = -1
                    drawTime = lastDrawTime
                    luckString = todayDrawStatus
                    luckyNumber = todayLuckyNumber
                }
                return drawResult
            }
        }
        val usePanResult = pan.modifyUserPan(userId, -2)
        if (!usePanResult.status) {
            val drawResult = DrawResult()
            drawResult.apply {
                drawCode = -2
                drawTime = 0
            }
            return drawResult
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
        moca.setUserConfig(userId, "draw_time", drawTime)
        moca.setUserConfig(userId, "today_draw_status", luckString)
        moca.setUserConfig(userId, "today_lucky_num", luckyNumber)

        mocaDB.mocaLog("UserDraw", groupId = groupId, targetId = userId,
            description = "luck = {$luckString}, luck_num = {$luckyNumber} lp = {$pictureFile}")
        val drawResult = DrawResult()
        drawResult.apply {
            drawCode = 0
            this.drawTime = drawTime
            this.luckString = luckString
            this.luckyNumber = luckyNumber
            this.pictureFile = pictureFile
        }
        return drawResult
    }

    fun keywordEdit(groupId: Long, paras: List<String>, operation: String): MessageChain {
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
                    return if (existKeys?.let { Regex(it).find(key.lowercase()) } != null) {
                        PlainText("错误：“${name}”中已存在能够识别“${key}”的关键词").toMessageChain()
                    } else {
                        keysList.add(key)
                        groupKeyword[name] = keysList.joinToString("|")
                        mocaDB.saveGroupKeyword(groupId, groupKeyword.toMap())
                        mocaDB.mocaLog("GroupAddKey", groupId = groupId, description = key)
                        PlainText("成功向“${name}”中添加了关键词“${key}”").toMessageChain()
                    }
                }
                "REMOVE" -> {
                    keysList.remove(key).also {
                        return if (it) {
                            groupKeyword[name] = keysList.joinToString("|")
                            mocaDB.saveGroupKeyword(groupId, groupKeyword.toMap())
                            mocaDB.mocaLog("GroupRemoveKey", groupId = groupId, description = key)
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

    /**
     * 提交图片
     *
     * @param groupId 群号
     * @param images 图片列表
     * @param category 分类
     */
    suspend fun submitPictures(groupId: Long, images: List<Image>, category: String): MessageChain {
        var successImageCount = 0
        var exceptionImageCount = 0
        var exceptionMessage = ""
        val imageCategory = if (category == "") {
            "${groupId}/UNDEFINED"
        } else {
            "${groupId}/${category}"
        }
        val downloadPath = "cache" + Slash + "upload" + Slash + imageCategory + Slash
        images.forEach { image ->
            val imageId = image.imageId
            val imageUrl = image.queryUrl()
            val fileName = imageId.substring(imageId.indexOf("{") + 1, imageId.indexOf("}") - 1)
            downloadImage(imageUrl, downloadPath, fileName).also {
                if (it.first == "SUCCESS") {
                    successImageCount++
                } else {
                    exceptionImageCount++
                    exceptionMessage += it.second + "\n"
                }
            }
        }
        var result = "提交图片：成功${successImageCount}张"
        if (exceptionImageCount != 0) {
            result += "，失败${exceptionImageCount}张\n发生错误：\n" + exceptionMessage
        }
        mocaDB.mocaLog("GroupUploadPic", groupId = groupId, description = result)
        return PlainText(result).toMessageChain()
    }

    fun reloadAllGroupKeyword (): String {
        mocaDB.loadAllGroupKeyword()
        mocaDB.mocaLog("GroupReloadAllKeyword", description = "success")
        return "success"
    }

    fun isReachedMessageLimit(groupId: Long): Boolean {
        mocaLogger.debug("$groupId: message limit remain: ${mapGroupFrequencyLimiter[groupId]}")
        return mapGroupFrequencyLimiter[groupId] == 0
    }

    /**
     * 发送语音
     */
    suspend fun sendVoice(event: GroupMessageEvent) {
        val voiceFolder = File("resource" + File.separator + "voice")
        val voiceFiles = voiceFolder.listFiles()
        if (!voiceFiles.isNullOrEmpty()) {
            val voiceFile = File(voiceFiles.random().absolutePath).toExternalResource()
            voiceFile.use {
                event.subject.uploadAudio(voiceFile).sendTo(event.group)
            }
        }
    }

    // fun testFunction(){ }

    fun setUserConfig(userId: Long, arg: String, value: Any): Boolean {
        return mocaDB.setConfig(userId, "USER", arg, value)
    }

    class Pan{
        /**
         * 设置用户面包数量
         *
         * @param userId 用户QQ号
         * @param panNumber 要设置的面包数量
         *
         * @return true/false(成功/失败)
         */
        fun setUserPan(userId: Long, panNumber: Int): Boolean {
            return moca.setUserConfig(userId, "pan", panNumber)
        }

        /**
         * 改变用户面包数量
         *
         * @param userId 用户QQ号
         * @param delta 要增加/减少(以负数)的面包数量
         *
         * @return PanModifyResult
         */
        fun modifyUserPan(userId: Long, delta: Int): PanModifyResult {
            val userPan = getUserPan(userId)
            val modfiyResult = PanModifyResult()
            return if (delta > 0) {
                setUserPan(userId, userPan + delta)
                mocaDB.mocaLog("UserPanNumChange", targetId = userId, description = "$delta")
                modfiyResult.apply {
                    status = true
                    newPanNumber = getUserPan(userId)
                }
            } else {
                if (-delta > userPan) {
                    mocaLogger.warn("$userId: Pan not enough($userPan < ${-delta})")
                    modfiyResult.apply {
                        status = false
                        newPanNumber = userPan
                    }
                } else {
                    setUserPan(userId, userPan + delta)
                    mocaDB.mocaLog("UserPanNumChange", targetId = userId, description = "$delta")
                    modfiyResult.apply {
                        status = true
                        newPanNumber = getUserPan(userId)
                    }
                }
            }
        }

        fun getUserPan(userId: Long): Int {
            return mocaDB.getUserConfigInt(userId, "pan")
        }
    }
}

class GroupPicture{
    /**
     * 提交群图片
     *
     * @param groupId 群号
     * @param images 图片列表
     * @param pic_id 图片id
     *
     * @return 提交结果
     */
    suspend fun submitGroupPictures(groupId: Long, images: List<Image>, pic_id: Int): MessageChain {
        val downloadPath = "resource" + Slash + "group_pic" + Slash + groupId.toString() + Slash
        var dbPicId = pic_id
        if (pic_id == -1) {
            dbPicId = mocaDB.getCurrentPicCount(groupId) + 1
        }
        if (dbPicId < 1 || dbPicId > 999) {
            return PlainText("错误：ID范围：1~999").toMessageChain()
        }
        deleteGroupPicById(groupId, dbPicId)
        val imageUrl = images.first().queryUrl()
        val fileName = "img_${dbPicId}"
        val uploadTime = System.currentTimeMillis() / 1000
        downloadImage(imageUrl, downloadPath, fileName).also {
            return if (it.first == "SUCCESS") {
                mocaDB.updateNewGroupPicture(groupId, it.second, dbPicId, uploadTime)
                PlainText("提交群图片：成功提交，图片ID为${dbPicId}").toMessageChain()
            } else {
                PlainText("提交群图片失败，发生错误：\n" + it.second).toMessageChain()
            }
        }
    }

    /**
     * 根据ID删除图片
     */
    fun deleteGroupPicById(groupId: Long, picId: Int): String {
        val currentPic = getGroupPicById(groupId, picId)
        if (!currentPic.isEmpty()) {
            val picFile = File("resource" + Slash + "group_pic" +
                    Slash + groupId.toString() + Slash + currentPic["name"])
            return try {
                if (picFile.exists()) {
                    picFile.delete()
                }
                val existDocument = mocaDB.getCurrentPics(groupId)
                existDocument.remove(picId.toString())
                mocaDB.saveGroupPicture(groupId, existDocument)
                mocaLogger.debug("$groupId: Delete group pic ID = $picId")
                mocaDB.mocaLog("GroupDeletePic", groupId = groupId,
                    description = "ID = $picId, picFile = $picFile")
                "成功删除了图片ID=$picId."
            } catch (e: Exception) {
                mocaLogger.error("$groupId: Delete file $picFile failed")
                "发生了一些错误."
            }
        } else {
            return "ID=${picId}的图片不存在."
        }

    }

    /**
     * 匹配群关键词
     */
    fun matchGroupPicKey(groupId: Long, messageContent: String): Boolean {
        val groupPicKeys = mocaDB.getGroupPicKeywords(groupId)
        return if (groupPicKeys == "") {
            false
        }else{
            Regex(groupPicKeys).find(messageContent.lowercase()) != null
        }
    }

    /**
     * 编辑群关键词
     */
    fun editGroupPicKeys(groupId: Long, key: String, operation: String = "ADD"): MessageChain {
        val groupPicKeys = mocaDB.getGroupPicKeywords(groupId)
        val keysList = groupPicKeys.split("|").toMutableList()
        when(operation) {
            "ADD" -> {
                if (groupPicKeys == "") {
                    mocaDB.saveGroupPicKeywords(groupId, key)
                    return PlainText("成功添加群关键词【$key】").toMessageChain()
                } else if (Regex(groupPicKeys).find(key.lowercase()) != null) {
                    return PlainText("错误：已存在能识别【${key}】的群关键词，请勿重复添加").toMessageChain()
                }
                keysList.add(key)
                mocaDB.saveGroupPicKeywords(groupId, keysList.joinToString("|"))
                mocaDB.mocaLog("GroupAddPicKey", groupId = groupId, description = key)
                return PlainText("成功添加群关键词【$key】").toMessageChain()
            }
            "REMOVE" -> {
                keysList.remove(key).also {
                    return if (it) {
                        mocaDB.saveGroupPicKeywords(groupId, keysList.joinToString("|"))
                        mocaDB.mocaLog("GroupRemovePicKey", groupId = groupId, description = key)
                        PlainText("成功删除了群关键词【${key}】").toMessageChain()
                    } else {
                        PlainText("未找到群关键词【${key}】").toMessageChain()
                    }
                }
            }
        }
        return PlainText("NO OPERATION").toMessageChain()
    }

    /**
     * 根据ID获取群图片
     */
    private fun getGroupPicById(groupId: Long, picId: Int): Document {
        val currentPics = mocaDB.getCurrentPics(groupId)
        val queryPic = currentPics[picId.toString()]
        return if (queryPic != null) {
            currentPics[picId.toString()] as Document
        } else {
            Document()
        }
    }

    /**
     * 发送群图片.
     */
    suspend fun sendGroupPicture(event: GroupMessageEvent, picId: Int = -1) {
        val groupPicFolderString = "resource${Slash}group_pic${Slash}${event.group.id}${Slash}"
        if (picId == -1) {
            val groupPicFolder = File(groupPicFolderString)
            val groupPics = groupPicFolder.listFiles()
            if (!groupPics.isNullOrEmpty()) {
                File(groupPics.random().absolutePath).sendAsImageTo(event.group)
            } else {
                event.group.sendMessage("群图片为空，请发送【使用说明】查看添加方法.")
            }
        } else {
            val toSendPic = getGroupPicById(event.group.id, picId)
            if (!toSendPic.isEmpty()) {
                File(groupPicFolderString + toSendPic["name"])
                    .sendAsImageTo(event.group)
            } else {
                event.group.sendMessage("指定ID=${picId}的图片不存在.")
            }
        }
    }
}