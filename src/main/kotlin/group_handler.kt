package me.swe.main

import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.contact.Contact.Companion.uploadImage
import net.mamoe.mirai.contact.getMember
import net.mamoe.mirai.contact.isOperator
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.code.MiraiCode.deserializeMiraiCode
import net.mamoe.mirai.message.data.*
import java.io.File
import java.lang.NumberFormatException

class MocaGroupMessage(
    private val event: GroupMessageEvent,
) {
    private val messageContent: String = event.message.content
    private val senderId = event.sender.id
    private val groupId = event.group.id
    private val subj = event.subject
    suspend fun messageHandler() {
        val messageLimit = 6

        // 检查并初始化每分钟群组消息限制
        if (groupId !in mapGroupFrequencyLimiter) {
            mapGroupFrequencyLimiter[groupId] = messageLimit
            mocaLogger.debug("$groupId: Init GFL.  MessageLeft = $messageLimit")
        }

        // 每分钟重置消息限制
        if ((System.currentTimeMillis() / 1000) - resetGroupFrequencyLimiterTime > 60) {
            mocaLogger.debug("$groupId: Reset message limit")
            mapGroupFrequencyLimiter.forEach {
                mapGroupFrequencyLimiter[it.key] = messageLimit
            }
            resetGroupFrequencyLimiterTime = System.currentTimeMillis() / 1000
        }

        // Superman 操作
        if (moca.isSuperman(senderId)) {
            supermanOperations().also {
                if (it) {
                    return
                }
            }
        }

        // 若达到每分钟消息上限，停止响应群消息
        if (moca.isReachedMessageLimit(groupId)) {
            mocaLogger.debug("$groupId: Group message limit reached!!!")
            return
        }

        // 提取At操作
        val atMessage: At? = event.message.findIsInstance<At>()
        if (atMessage != null) {
            atOperations().also {
                if (it) {
                    return
                }
            }
        }

        // 管理员/群主操作
        if (event.sender.permission.isOperator() || moca.isSuperman(senderId)) {
            adminOperations().also {
                if (it) {
                    return
                }
            }
        }

        // 面包相关操作
        if (moca.groupConfigEnabled(groupId, "pan")) {
            if (moca.isInCd(groupId, "replyCD")) {
                return
            }
            panOperations().also {
                if (it) {
                    moca.setCd(groupId, "replyCD")
                    return
                }
            }
        }

        // 查询换lp次数
        if (messageContent.replace("老婆", "lp")
                .contains("换lp次数")
        ) {
            getSenderChangeLpTimes()
            return
        }

        // 提交图片
        if (messageContent.startsWith("提交图片")) {
            val category = event.message.filterIsInstance<PlainText>()
                .firstOrNull()
                .toString()
                .substring(4)
                .replace("\n", "")
            if (!event.message.contains(Image)) {
                subj.sendMessage("错误，你至少需要包含一张图片")
                return
            }
            subj.sendMessage(moca.submitPictures(groupId,
                event.message.filterIsInstance<Image>(), category)
            )
            return
        }

        // 使用说明/青年大学习
        if (!moca.isInCd(groupId, "replyCD")) {
            when {
                messageContent.contains("使用说明") -> {
                    subj.sendMessage("使用说明：https://mocabot.cn/")
                    moca.setCd(groupId, "replyCD")
                    return
                }
                messageContent.contains("青年大学习") -> {
                    subj.sendMessage(getBotConfig("QNDXX"))
                    moca.setCd(groupId, "replyCD")
                    return
                }
            }

            // 摩卡爬/老婆
            messageContent
                .replace("摩卡", "moca")
                .replace("爪巴", "爬")
                .replace("老婆", "lp")
                .lowercase()
                .also {
                    if (moca.isInCd(groupId, "keaiPaCD")) {
                        return
                    }
                    if (it.contains("moca") && it.contains("爬")) {
                        if (randomDo(50)) {
                            mocaPaPath.listFiles()?.random()?.let { file -> subj.sendImage(file) }
                            moca.setCd(groupId, "keaiPaCD")
                        }

                        return
                    }
                    if ((it.contains("moca") && it.contains("可爱")) ||
                        (it.contains("moca") && it.contains("lp"))
                    ) {
                        if (randomDo(50)) {
                            mocaKeaiPath.listFiles()?.random()?.let { file -> subj.sendImage(file) }
                            moca.setCd(groupId, "keaiPaCD")
                        }
                        return
                    }
                }

            // 设置老婆
            val preProcessedContent = messageContent
                .replace("我", "w")
                .replace("老婆", "lp")
                .replace("事", "是")
            if (preProcessedContent.startsWith("wlp是")) {
                val setLpResult = moca.setUserLp(groupId, senderId, preProcessedContent)
                subj.sendMessage(setLpResult)
                return
            }

            // 来点老婆
            if (messageContent.contains("来点") &&
                messageContent
                    .lowercase()
                    .replace("老婆", "lp")
                    .contains("lp")
            ) {
                val lpName = moca.getUserLp(senderId)
                if (lpName !in moca.getGroupKeyword(groupId).keys) {
                    subj.sendMessage("az，您还没有设置lp或者这个群没有找到nlp呢...")
                    return
                }
                val doubleLp =
                    messageContent.startsWith("多") && moca.groupConfigEnabled(groupId, "pan")
                val imageParameter = Pair(lpName, doubleLp)
                sendPicture(imageParameter)
                moca.setCd(groupId, "replyCD")
                return
            }

            // “!”消息处理器
            if (messageContent.startsWith("!") || messageContent.startsWith("！")
            ) {
                exclamationMarkProcessor().also {
                    if (it) {
                        moca.setCd(groupId, "replyCD")
                        return
                    }
                }
            }

            // 查看群图片
            if (messageContent.startsWith("查看群图片")) {
                val tempId = event.message.filterIsInstance<PlainText>()
                    .firstOrNull()
                    .toString()
                    .substring(5)
                    .replace("\n", "")
                val picId = try {
                    tempId.toInt()
                }catch (e: NumberFormatException) {
                    subj.sendMessage("错误：ID有误")
                    return
                }
                if (picId < 1 || picId > 999) {
                    subj.sendMessage("错误：ID范围：1~999")
                    return
                }
                moca.groupPicture.sendGroupPicture(event, picId)
                moca.setCd(groupId, "replyCD")
                return
            }

            // 匹配群图片关键词(GroupPicture)
            moca.groupPicture.matchGroupPicKey(groupId, messageContent).also {
                if (it) {
                    moca.groupPicture.sendGroupPicture(event)
                    moca.setCd(groupId, "replyCD")
                    return
                }
            }

            // 匹配群关键词
            val matchResult = moca.matchKey(groupId, messageContent)
            if (matchResult.first != "") {
                sendPicture(matchResult)
                moca.setCd(groupId, "replyCD")
                return
            }
        }

        groupRepeatSaver().also { toRepeat ->
            if (toRepeat) {
                if (!moca.isInCd(groupId, "repeatCD")) {
                    randomDo(
                        moca.getGroupConfig(groupId, "repeatChance")
                            .toString().toInt()
                    ).also { random ->
                        if (random) {
                            sendRepeatContent()
                            moca.setCd(groupId, "repeatCD")
                        }
                    }
                }
            }
        }
    }

    /**
     * 管理员操作
     */
    private suspend fun adminOperations(): Boolean {
        when {
            messageContent.startsWith("设置图片cd") -> {
                try {
                    val toSetParameter = messageContent
                        .substring(6)
                        .trimEnd('秒')
                        .trimEnd('s')
                        .toInt()
                    if (toSetParameter < 10) {
                        subj.sendMessage(buildMessageChain {
                            +PlainText("图片cd最低为10秒，请重新发送.")
                        })
                        return true
                    }
                    moca.setGroupFunction(groupId, "replyCD", toSetParameter)
                    subj.sendMessage(buildMessageChain {
                        +PlainText("图片cd设置为：${toSetParameter}秒")
                    })
                    return true
                } catch (e: NumberFormatException) {
                    subj.sendMessage(buildMessageChain {
                        +PlainText("参数错误。\n设置例：【设置图片cd15秒】")
                    })
                    return true
                } catch (e: Exception) {
                    subj.sendMessage(buildMessageChain {
                        +PlainText("发生了一些错误。\n设置例：【设置图片cd15秒】")
                    })
                }
                return true
            }

            messageContent.startsWith("设置复读cd") -> {
                try {
                    val toSetParameter = messageContent
                        .substring(6)
                        .trimEnd('秒')
                        .trimEnd('s')
                        .toInt()
                    if (toSetParameter < 120) {
                        subj.sendMessage(buildMessageChain {
                            +PlainText("复读cd最低为120秒，请重新发送.")
                        })
                        return true
                    }
                    moca.setGroupFunction(groupId, "repeatCD", toSetParameter)
                    subj.sendMessage(buildMessageChain {
                        +PlainText("复读cd设置为：${toSetParameter}秒")
                    })
                    return true
                } catch (e: NumberFormatException) {
                    subj.sendMessage(buildMessageChain {
                        +PlainText("参数错误。\n设置例：【设置复读cd120秒】")
                    })
                    return true
                } catch (e: Exception) {
                    subj.sendMessage(buildMessageChain {
                        +PlainText("发生了一些错误。\n设置例：【设置复读cd120秒】")
                    })
                    return true
                }
            }

            messageContent.startsWith("设置复读概率") -> {
                try {
                    val toSetParameter = messageContent
                        .substring(6)
                        .trimEnd('%')
                        .toInt()
                    if (toSetParameter < 0 || toSetParameter > 100) {
                        subj.sendMessage(buildMessageChain {
                            +PlainText("复读概率应在0~100%内，请重新发送.")
                        })
                        return true
                    }
                    moca.setGroupFunction(groupId, "repeatChance", toSetParameter)
                    subj.sendMessage(buildMessageChain {
                        +PlainText("复读概率设置为：${toSetParameter}%")
                    })
                    return true
                } catch (e: NumberFormatException) {
                    subj.sendMessage(buildMessageChain {
                        +PlainText("参数错误。\n设置例：【设置复读概率30%】")
                    })
                    return true
                } catch (e: Exception) {
                    subj.sendMessage(buildMessageChain {
                        +PlainText("发生了一些错误。\n设置例：【设置复读概率30%】")
                    })
                    return true
                }
            }

            messageContent.startsWith("查看当前参数") -> {
                subj.sendMessage(
                    "当前参数：\n" +
                            "图片cd=${moca.getGroupConfig(groupId, "replyCD")}秒\n" +
                            "复读cd=${moca.getGroupConfig(groupId, "repeatCD")}秒\n" +
                            "复读概率=${moca.getGroupConfig(groupId, "repeatChance")}%"
                )
                return true
            }

            (messageContent.startsWith("添加关键词") || messageContent.startsWith("删除关键词")) -> {
                val paras = messageContent
                    .substring(5)
                    .replace(" ", "")
                    .replace("，", ",")
                    .split(',')
                if (paras.size != 2) {
                    subj.sendMessage("错误，参数数量有误")
                    return true
                }
                if (paras[0] == "" || paras[1] == "" ||
                    containSpecialChar(paras[0]) || containSpecialChar(paras[1]) ||
                    containProtectedKeys(paras[0]) || containProtectedKeys(paras[1])
                ) {
                    subj.sendMessage("错误，参数为空或包含不允许的特殊字符或包含摩卡的关键词")
                    return true
                }
                if (messageContent.substring(0, 2) == "添加") {
                    subj.sendMessage(moca.keywordEdit(groupId, paras, "ADD"))
                } else {
                    subj.sendMessage(moca.keywordEdit(groupId, paras, "REMOVE"))
                }
                return true
            }

            (messageContent.startsWith("添加群关键词") || messageContent.startsWith("删除群关键词")) -> {
                val toEditKey = messageContent
                    .substring(6)
                    .replace(" ", "")
                return if (toEditKey != "" && !containSpecialChar(toEditKey) && !containProtectedKeys(toEditKey)) {
                    if (messageContent.substring(0, 2) == "添加") {
                        subj.sendMessage(moca.groupPicture.editGroupPicKeys(groupId, toEditKey, "ADD"))
                    } else {
                        subj.sendMessage(moca.groupPicture.editGroupPicKeys(groupId, toEditKey, "REMOVE"))
                    }
                    true
                } else {
                    subj.sendMessage("错误，参数为空或包含不允许的特殊字符或包含摩卡的关键词")
                    true
                }
            }

            messageContent.startsWith("查看群关键词") -> {
                val groupPicKeys = moca.getGroupPicKeyword(groupId)
                return if (groupPicKeys == "") {
                    subj.sendMessage("群关键词为空.")
                    true
                }else {
                    subj.sendMessage("群关键词：$groupPicKeys")
                    true
                }
            }
        }


        if (messageContent.startsWith("打开") || messageContent.startsWith("关闭")) {
            val operationString = messageContent.subSequence(0, 2)
            val operation = if (operationString == "打开") 1 else 0
            when (messageContent.substring(2)) {
                "面包功能" -> {
                    moca.setGroupFunction(groupId, "pan", operation)
                    subj.sendMessage("${groupId}已${operationString}面包功能")
                    return true
                }
                "翻译功能" -> {
                    moca.setGroupFunction(groupId, "trans", operation)
                    subj.sendMessage("${groupId}已${operationString}翻译功能")
                    return true
                }
                "随机选歌" -> {
                    moca.setGroupFunction(groupId, "random", operation)
                    subj.sendMessage("${groupId}已${operationString}随机选歌功能")
                    return true
                }
                "指令功能" -> {
                    moca.setGroupFunction(groupId, "command", operation)
                    subj.sendMessage("${groupId}已${operationString}【!】指令功能")
                    return true
                }
                "实验功能" -> {
                    moca.setGroupFunction(groupId, "exp", operation)
                    subj.sendMessage("${groupId}已${operationString}实验功能")
                    return true
                }
                "欢迎新人" -> {
                    moca.setGroupFunction(groupId, "welcomeNewMemberJoin", operation)
                    subj.sendMessage("${groupId}已${operationString}欢迎新人功能")
                    return true
                }
            }
        }

        if (messageContent.startsWith("提交群图片")) {
            val tempId = event.message.filterIsInstance<PlainText>()
                .firstOrNull()
                .toString()
                .substring(5)
                .replace("\n", "")
            val picId = try {
                tempId.toInt()
            }catch (e: NumberFormatException) {
                -1
            }
            if (!event.message.contains(Image)) {
                subj.sendMessage("错误：你需要包含一张图片")
                return true
            }
            subj.sendMessage(moca.groupPicture.submitGroupPictures(groupId,
                event.message.filterIsInstance<Image>(), picId)
            )
            return true
        }

        if (messageContent.startsWith("删除群图片")) {
            val tempId = event.message.filterIsInstance<PlainText>()
                .firstOrNull()
                .toString()
                .substring(5)
                .replace("\n", "")
            val picId = try {
                tempId.toInt()
            }catch (e: NumberFormatException) {
                -1
            }
            moca.groupPicture.deleteGroupPicById(groupId, picId).also {
                if (it != "") {
                    subj.sendMessage(it)
                }
            }
            return true
        }
        return false
    }

    /**
     * 超级管理员操作
     */
    private suspend fun supermanOperations(): Boolean {
        when (messageContent) {
            "RELOAD_REDIS" -> {
                val reloadResult = mocaDB.loadIndexFile()
                val resultMessage = "Superman: Reload redis, current count=$reloadResult"
                mocaLogger.debug(resultMessage)
                subj.sendMessage(resultMessage)
                return true
            }
            "RELOAD_KEY" -> {
                val reloadResult = moca.reloadAllGroupKeyword()
                subj.sendMessage(reloadResult)
                return true
            }
        }
        when{
            messageContent.startsWith("SET_PAN") -> {
                val paras = messageContent
                    .replace(" ", "")
                    .substring(7)
                    .trim()
                    .split(',')
                if (paras.size == 2) {
                    val qq = paras[0].toLong()
                    val panNum = paras[1].toInt()
                    moca.pan.setUserPan(qq, panNum)
                    subj.sendMessage("Result: $qq pan: ${moca.pan.getUserPan(senderId)}")
                }
                return true
            }
            messageContent.startsWith("RESET") -> {
                var userId = 0L
                var toResetArg = ""
                var initValue: Any = 0L
                val paras = messageContent
                    .replace(" ", "")
                    .substring(5)
                    .trim()
                    .split(',')
                fun argConvert(arg: String): Pair<String, Any>{
                   return when(arg){
                        "SIGNIN" -> {
                            Pair("signin_time", 0L)
                        }
                       "DRAW" -> {
                           Pair("draw_time", 0L)
                       }
                       "BUY_PAN" -> {
                           Pair("last_buy_time", 0L)
                       }
                       "CLP_TIMES" -> {
                           Pair("clp_time", 0)
                       }
                       else -> {
                           Pair("", 0L)
                       }
                    }
                }
                when (paras.size) {
                    1 -> {
                        val resetParam = argConvert(paras[0])
                        if (resetParam.first == "") {
                            subj.sendMessage("WRONG PARAMETER.")
                            return true
                        }
                        userId = senderId
                        toResetArg = resetParam.first
                        initValue = resetParam.second
                        }
                    2 -> {
                        try {
                            userId = paras[0].toLong()
                        } catch (e: NumberFormatException) {
                            subj.sendMessage("WRONG QQ.")
                            return true
                        }
                        val resetParam = argConvert(paras[1])
                        if (resetParam.first == "") {
                            subj.sendMessage("WRONG PARAMETER.")
                            return true
                        }
                        toResetArg = resetParam.first
                        initValue = resetParam.second
                    }
                }
                subj.sendMessage(
                    moca.setUserConfig(userId, toResetArg, initValue).let {
                        if (it) {
                            "RESET OK. $userId $toResetArg = $initValue"
                        } else {
                            "RESET ERROR."
                        }
                    }
                )
                return true
            }
        }
        return false
    }

    /**
     * 获取换lp次数.
     */
    private suspend fun getSenderChangeLpTimes(): Boolean {
        val changeLpTimes = moca.getChangeLpTimes(senderId)
        subj.sendMessage(
            if (changeLpTimes <= 0) {
                buildMessageChain {
                    +At(event.sender)
                    +PlainText(" 你还没有换过lp呢~")
                }
            } else {
                buildMessageChain {
                    +At(event.sender)
                    +PlainText(" 你换了${changeLpTimes}次lp了哦~")
                }
            })
        return true
    }

    /**
     * 发送图片.
     *
     *
     */
    private suspend fun sendPicture(imageParameter: Pair<String, Boolean>): Boolean {
        var panResult = ""
        var pictureCount = 1
        if (imageParameter.second) {
           panResult = moca.eatPan(senderId, 3).let {
                if (it.status) {
                    pictureCount = 2
                    "摩卡吃掉了3个面包，你还剩${it.newPanNumber}个面包哦~"
                } else {
                    "呜呜呜，你的面包不够吃了呢..."
                }
            }
        }
        val toSendMessage = mutableListOf<MessageContent>(
            PlainText(panResult)
        )
        val pictureFiles = moca.randomPicture(imageParameter.first, pictureCount)
        for (filePath in pictureFiles) {
            toSendMessage += subj.uploadImage(File(filePath))
        }
        mocaLogger.debug(toSendMessage.toString())
        mocaDB.updateGroupCount(groupId, imageParameter.first)
        subj.sendMessage(toSendMessage.toMessageChain())
        return true
    }

    /**
     * 包含At的操作
     */
    private suspend fun atOperations(): Boolean {
        val atTarget = event.message.findIsInstance<At>()?.target
        val atTargetName = atTarget?.let { subj.getMember(it)?.nameCardOrNick }
        val messageContent = messageContent
        if (atTarget == event.bot.id) {
            when {
                messageContent.contains("关键词") -> {
                    subj.sendImage(File(moca.buildGroupKeywordImage(groupId)))
                    return true
                }

                (messageContent.contains("图片数量统计") || messageContent.contains("统计图片数量")) -> {
                    subj.sendImage(File(moca.buildGroupPictureCountImage(groupId)))
                    return true
                }

                messageContent.contains("统计次数") -> {
                    subj.sendImage(File(moca.buildGroupCountImage(groupId)))
                    return true
                }
                messageContent.contains("语音") || messageContent.contains("说话") -> {
                    moca.sendVoice(event)
                    return true
                }
            }
            messageContent
                .replace("摩卡", "moca")
                .replace("爪巴", "爬")
                .replace("老婆", "lp")
                .lowercase()
                .also {
                    if (it.contains("爬")) {
                        if (randomDo(50)) {
                            mocaPaPath.listFiles()?.random()?.let { file -> subj.sendImage(file) }
                        }
                        return true
                    }
                    if (it.contains("可爱") || it.contains("lp")) {
                        if (randomDo(50)) {
                            mocaKeaiPath.listFiles()?.random()?.let { file -> subj.sendImage(file) }
                        }
                        return true
                    }
                }
        } else {
            when {
                messageContent
                    .replace("老婆", "lp")
                    .contains("换lp次数")
                -> {
                    val changeLpTimes = atTarget?.let { moca.getChangeLpTimes(it) }
                    subj.sendMessage(
                        if (changeLpTimes == 0) {
                            buildMessageChain {
                                +At(event.sender)
                                +PlainText(" ${atTargetName}还没有换过lp呢~")
                            }
                        } else {
                            buildMessageChain {
                                +At(event.sender)
                                +PlainText(" ${atTargetName}换了${changeLpTimes}次lp了哦~")
                            }
                        })
                    return true
                }
            }
        }
        return false
    }

    /**
     * !指令处理器
     */
    private suspend fun exclamationMarkProcessor(): Boolean {
        val paraList = messageContent
            .replaceFirst("！", "!")
            .trimStart('!')
            .split(' ')
        if (paraList.isEmpty()) {
            return false
        }
        when (paraList[0]) {
            "rd" -> {
                // !rd k m n
                // 掷k个m~n的色子，用空格分隔(0<k<11)
                var resultString = ""
                try {
                    when (paraList.size) {
                        1 -> {
                            resultString += "roll 1 个 1 ~ 6 的骰子\n结果为："
                            resultString += "${(1..6).random()}"
                        }
                        2 -> {
                            val k = if (paraList[1].toInt() > 10) {
                                10
                            } else {
                                paraList[1].toInt()
                            }
                            resultString += "roll $k 个 1 ~ 6 的骰子\n结果为："
                            for (i in 1..k) {
                                resultString += "${(1..6).random()} "
                            }
                        }
                        4 -> {
                            val k = if (paraList[1].toInt() > 10) {
                                10
                            } else {
                                paraList[1].toInt()
                            }
                            val m = paraList[2].toInt()
                            val n = paraList[3].toInt()

                            if (m > n) {
                                subj.sendMessage("错误：参数有误\n使用例：【!rd 2 1 10】")
                                return true
                            }
                            resultString += "roll $k 个 $m ~ $n 的骰子\n结果为："
                            for (i in 1..k) {
                                resultString += "${(m..n).random()} "
                            }
                        }
                        else -> {
                            subj.sendMessage("错误：参数数量有误\n使用例：【!rd 2 1 10】")
                            return true
                        }
                    }
                    resultString.trim()
                    subj.sendMessage(resultString)
                    return true
                } catch (e: NumberFormatException) {
                    subj.sendMessage("错误：参数数量有误\n使用例：【!rd 2 1 10】")
                    return true
                }
            }
            "r" -> {
                if (paraList.size < 3) {
                    subj.sendMessage("错误：参数数量不足\n使用例：【!r 吃饭 睡觉】")
                } else {
                    paraList.drop(1).shuffled()[0].also {
                        subj.sendMessage("那当然是${it}啦~")
                    }
                }
                return true
            }
            "c" -> {
                if (paraList.size != 2) {
                    subj.sendMessage("错误：参数数量有误\n使用例：【!c 小明的出货率】")
                } else {
                    paraList.drop(1)[0].also {
                        subj.sendMessage("${it}为：${(0..100).random()}")
                    }
                }
                return true
            }
            "bl" -> {
                paraList.drop(1).also {
                    subj.sendMessage(
                        buildMessageChain {
                            +At(senderId)
                            +PlainText(userBindCity(senderId, it))
                        }
                    )
                }
                return true
            }
            "wetdy" -> {
                var cityId = ""
                var cityName = ""
                var cityAdm = ""
                var cityCountry = ""
                when(paraList.size) {
                    1 -> {
                        cityId = mocaDB.getUserConfig(senderId, "loc_id")
                        if (cityId == "") {
                            buildMessageChain {
                                +At(senderId)
                                +PlainText(" 错误：尚未绑定城市，请使用【!bl 城市 [省](可选)】进行绑定.")
                            }
                            return true
                        }
                        cityName = mocaDB.getUserConfig(senderId, "loc_name")
                        cityAdm = mocaDB.getUserConfig(senderId, "loc_adm")
                        cityCountry = mocaDB.getUserConfig(senderId, "loc_con")
                    }
                    2, 3 -> {
                        paraList.drop(1).also {
                            val cityData = cityLookup(it)
                            cityId = cityData["id"].toString()
                            cityName = cityData["name"].toString()
                            cityAdm = cityData["adm1"].toString()
                            cityCountry = cityData["country"].toString()
                        }
                    }
                    else -> {
                        buildMessageChain {
                            +At(senderId)
                            +PlainText(" 错误：参数数量有误.")
                        }
                    }
                }
                val wData = weatherLookup(cityId)
                if (wData.code != "200") {
                    buildMessageChain {
                        +At(senderId)
                        +PlainText(" 错误：查询的城市有误，请检查.")
                    }
                    return true
                }
                subj.sendMessage(
                    buildMessageChain {
                        +At(senderId)
                        +PlainText("\n${wData.fxDate} $cityName, $cityAdm, ${cityCountry}的天气\n")
                        +PlainText("当前${wData.textNow}，温度${wData.tempNow}℃，体感温度${wData.tempFeelsLike}℃。\n")
                        +PlainText("今日温度${wData.tempMin}℃ ~ ${wData.tempMax}℃。\n")
                        +PlainText("今日白天${wData.textDay}，${wData.windDirDay}${wData.windScaleDay}级，" +
                                "夜晚${wData.textNight}，${wData.windDirNight}${wData.windScaleNight}级，" +
                                "湿度${wData.humidity}%。\n")
                    }
                )
                return true
            }
        }
        return false
    }

    private suspend fun panOperations(): Boolean {
        when (messageContent) {
            "我的面包" -> {
                subj.sendMessage(
                    buildMessageChain {
                        +At(senderId)
                        +PlainText(" 您现在有${moca.pan.getUserPan(senderId)}个面包呢~")
                    }
                )
                return true
            }
            in arrayListOf("买面包", "来点面包") -> {
                val buyResult = moca.buyPan(senderId)
                if (buyResult.status) {
                    if (buyResult.isFirstTime) {
                        // first time
                        subj.sendMessage(
                            buildMessageChain {
                                +At(senderId)
                                +PlainText("\n初次成功购买了${buyResult.buyNumber}个面包！以后每小时可以购买一次哦~")
                                +PlainText("\n有空的话，常来买哦~")
                                +PlainText("\n你现在有${buyResult.newPanNumber}个面包啦~")
                            }
                        )
                        return true
                    } else {
                        subj.sendMessage(
                            buildMessageChain {
                                +At(senderId)
                                +PlainText(" 成功购买了${buyResult.buyNumber}个面包，" +
                                        "你现在有${buyResult.newPanNumber}个面包啦~")
                            }
                        )
                        return true
                    }
                } else {
                    val toWaitTimeString = if (buyResult.secondsToWait < 60) {
                        "${buyResult.secondsToWait}秒"
                    } else {
                        "${buyResult.secondsToWait / 60}分钟"
                    }
                    subj.sendMessage(
                        buildMessageChain {
                            +At(senderId)
                            +PlainText(" 还不能买面包呢~还要等${toWaitTimeString}呢~")
                        }
                    )
                    return true
                }
            }
            in arrayListOf("吃面包", "恰面包") -> {
                subj.sendMessage(
                    buildMessageChain {
                        +At(senderId)
                        +PlainText(
                            moca.eatPan(senderId, 1).let {
                            if (it.status) {
                                " 你吃掉了1个面包，还剩${it.newPanNumber}个面包了哦~"
                            } else {
                                " 呜呜呜，面包不够吃了呢..."
                            }
                        })
                    }
                )
                return true
            }
            "签到" -> {
                val signIn = moca.userSignIn(senderId)
                val signInHour = signIn.signInTime.toDateStr("HH").toInt()
                val cityId = mocaDB.getUserConfig(senderId, "loc_id")
                var weatherText = ""
                if(cityId != "NOT_FOUND") {
                    val location = mocaDB.getUserConfig(senderId, "loc_name")
                    weatherText = when {
                        (signInHour in 0..17) -> {
                            val wData = weatherLookup(cityId)
                            "今天${location}${wData.textDay}，温度${wData.tempMin}℃ ~ ${wData.tempMax}℃，" +
                                    "总降雨量为${wData.precipDay}mm"
                        }
                        else -> {
                            val wData = weatherLookup(cityId, 2)
                            "明天${location}${wData.textDay}，温度${wData.tempMin}℃ ~ ${wData.tempMax}℃，" +
                                    "总降雨量为${wData.precipDay}mm"
                        }
                    }
                }
                val greetWord: String = when(signInHour){
                    in (0..3) -> {
                        "夜深了..."
                    }
                    in (4..6) -> {
                         "清晨了~"
                    }
                    in (7..10) -> {
                        "上午好！"
                    }
                    in (11..13) -> {
                        "中午好！"
                    }
                    in (13..16) -> {
                        "下午好~"
                    }
                    in (17..18) -> {
                        "傍晚好~"
                    }
                    else -> {
                        "晚上好~"
                    }
                }
                subj.sendMessage(
                    when (signIn.signInCode) {
                        -1 -> {
                            buildMessageChain {
                                +At(senderId)
                                +PlainText("\n你已经在今天的 ${signIn.signInTime.toDateStr("HH:mm:ss")} 签过到了哦~")
                                +PlainText("\n一天只能签到一次哦，明天再来吧~")
                            }
                        }
                        0 -> {
                            buildMessageChain {
                                +At(senderId)
                                +PlainText(" ${greetWord}\n${signIn.signInTime.toDateStr()} 签到成功！")
                                +PlainText("\n你是今天第${signIn.numberOfDay}个签到的呢~")
                                +PlainText("\n累计签到${signIn.sumSignInDays}天，你现在有${signIn.newPanNumber}个面包啦~\n")
                                +PlainText(weatherText)
                            }
                        }
                        1 -> {
                            buildMessageChain {
                                +At(senderId)
                                +PlainText(" ${greetWord}\n${signIn.signInTime.toDateStr()} 签到成功~")
                                +PlainText("\n你是今天第${signIn.numberOfDay}个签到的呢~")
                                +PlainText("\n初次签到，摩卡给你5个面包哦~")
                                +PlainText("\n以后每天都可以签一次到哦~有空的话每天都来签到吧~")
                                +PlainText("\n你现在有${signIn.newPanNumber}个面包啦~")
                            }
                        }
                        else -> {
                            buildMessageChain {}
                        }
                    }
                )
                return true
            }
            "抽签" -> {
                val drawResult = moca.userDraw(senderId, groupId)
                subj.sendMessage(
                    when (drawResult.drawCode) {
                        -2 -> {
                            buildMessageChain {
                                +At(senderId)
                                +PlainText(" 呜呜呜，面包不够了呢...抽不了签了...")
                            }
                        }
                        -1 -> {
                            val lastDrawTime = drawResult.drawTime
                            buildMessageChain {
                                +At(senderId)
                                +PlainText(
                                    "\n你已经在今天的 ${lastDrawTime.toDateStr("HH:mm:ss")}抽过签啦~，" +
                                            "\n一天只能抽一次签哦~明天再来吧~" +
                                            "\n今日运势：${drawResult.luckString}" +
                                            "\n今日幸运数字：${drawResult.luckyNumber}"
                                )
                            }
                        }
                        0 -> {
                            val toSendMessage = mutableListOf(
                                At(senderId),
                                PlainText(
                                    "\n${drawResult.drawTime.toDateStr()} 抽签成功！" +
                                            "\n今日运势：${drawResult.luckString}" +
                                            "\n今日幸运数字：${drawResult.luckyNumber}"
                                )
                            )
                            if (drawResult.pictureFile != "") {
                                toSendMessage += subj.uploadImage(File(drawResult.pictureFile))
                            }
                            mocaLogger.debug(toSendMessage.toString())
                            toSendMessage.toMessageChain()
                        }
                        else -> {
                            buildMessageChain {}
                        }
                    }
                )
                return true
            }
        }
        return false
    }

    /**
     * 复读机
     *
     * @return 是否需要复读
     */
    private fun groupRepeatSaver(): Boolean {
        // mutableMapOf<Long, MutableMap<Int, String>>()
        if (mapGroupRepeater[groupId].isNullOrEmpty()) {
            mapGroupRepeater[groupId] = mutableMapOf()
            mapGroupRepeater[groupId]?.set(0, "0")
            mapGroupRepeater[groupId]?.set(1, "")
            mapGroupRepeater[groupId]?.set(2, "")
            mapGroupRepeater[groupId]?.set(3, "")
        }
        when (mapGroupRepeater[groupId]?.get(0) ?: String) {
            "0" -> {
                mapGroupRepeater[groupId]?.set(0, "1")
                mapGroupRepeater[groupId]?.set(1, event.message.serializeToMiraiCode())
            }
            "1" -> {
                mapGroupRepeater[groupId]?.set(0, "2")
                mapGroupRepeater[groupId]?.set(2, event.message.serializeToMiraiCode())
            }
            "2" -> {
                mapGroupRepeater[groupId]?.set(1, mapGroupRepeater[groupId]?.get(2) as String)
                mapGroupRepeater[groupId]?.set(2, event.message.serializeToMiraiCode())
            }
        }
        if (mapGroupRepeater[groupId]?.get(1) == "" || mapGroupRepeater[groupId]?.get(2) == "") {
            return false
        }
        if (mapGroupRepeater[groupId]?.get(1) == mapGroupRepeater[groupId]?.get(2) &&
            mapGroupRepeater[groupId]?.get(2) != mapGroupRepeater[groupId]?.get(3)
        ) {
            return true
        }
        return false
    }

    /**
     * 发送复读内容.
     */
    private suspend fun sendRepeatContent() {
        val toSendMessage = mapGroupRepeater[groupId]?.get(1).toString().deserializeMiraiCode()
        subj.sendMessage(toSendMessage)
        mapGroupRepeater[groupId]?.set(3, event.message.serializeToMiraiCode())
    }
}
