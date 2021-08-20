package me.swe.main


import io.ktor.util.date.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.contact.isOperator
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol.ANDROID_PHONE
import net.mamoe.mirai.utils.MiraiLogger
import java.lang.NumberFormatException

fun main(args: Array<String>) {
    WithConfiguration.main()
}

object WithConfiguration {
    @JvmStatic
    fun main(): Unit = runBlocking {
        val moca = Moca()
        val mocaLogger = MiraiLogger.create("MocaLogger")
        picturePath = moca.getBotConfig("PIC_PATH")

        val botQQ = moca.getBotConfig("QQ").toLong()
        val botPassword = moca.getBotConfig("PASS")
        resetGroupFrequencyLimiterTime = System.currentTimeMillis() / 1000
        // moca.testFunction()
        // return@runBlocking

        val bot = BotFactory.newBot(botQQ, botPassword) {
            fileBasedDeviceInfo() // 使用 device.json 存储设备信息
            protocol = ANDROID_PHONE // 切换协议
            noNetworkLog()
        }.alsoLogin()

        // bot.getFriend(565379987)?.sendMessage("你好")
        // bot.getGroup(907274961)?.sendMessage("Bot 上线")

        bot.eventChannel.subscribeAlways<GroupMessageEvent> {
            val groupMessageHandler = MocaGroupMessageHandler(this, subject, moca)
            val messageContent = this.message.content
            val senderId = sender.id
            val groupId = group.id
            val messageLimit = 8
            if (groupId !in mapGroupFrequencyLimiter) {
                mapGroupFrequencyLimiter[groupId] = messageLimit
                mocaLogger.info("Init GFL. $groupId MessageLeft = $messageLimit")
            }

            if ((System.currentTimeMillis() / 1000) - resetGroupFrequencyLimiterTime > 60) {
                mocaLogger.info("reset message limit after 60s")
                mapGroupFrequencyLimiter.forEach {
                    mapGroupFrequencyLimiter[it.key] = messageLimit
                }
                resetGroupFrequencyLimiterTime = System.currentTimeMillis() / 1000
            }

            if (moca.isSuperman(senderId)) {
                groupMessageHandler.supermanOperations().also {
                    if (it) {
                        return@subscribeAlways
                    }
                }
            }

            if (moca.isReachedMessageLimit(groupId)) {
                mocaLogger.warning("Group messageLimit reached!!!")
                return@subscribeAlways
            }

            val atMessage: At? = this.message.findIsInstance<At>()
            if (atMessage != null) {
                groupMessageHandler.atOperations().also {
                    if (it) {
                        return@subscribeAlways
                    }
                }
            }

            if (sender.permission.isOperator() || moca.isSuperman(senderId)) {
                groupMessageHandler.adminOperations().also {
                    if (it) {
                        return@subscribeAlways
                    }
                }
            }

            if (moca.groupConfigEnabled(groupId, "pan")) {
                if (moca.isInCd(groupId, "replyCD")) {
                    return@subscribeAlways
                }
                groupMessageHandler.panOperations().also {
                    if (it) {
                        moca.setCd(groupId, "replyCD")
                        return@subscribeAlways
                    }
                }
            }

            if (messageContent
                    .replace("老婆", "lp")
                    .contains("换lp次数")
            ) {
                groupMessageHandler.getSenderChangeLpTimes()
                return@subscribeAlways
            }

            if (messageContent.startsWith("提交图片")) {
                val category = message.filterIsInstance<PlainText>()
                    .firstOrNull()
                    .toString()
                    .substring(4)
                    .replace("\n", "")
                if (!message.contains(Image)) {
                    subject.sendMessage("错误，你至少需要包含一张图片")
                    return@subscribeAlways
                }
                subject.sendMessage(moca.submitPictures(groupId,
                    message.filterIsInstance<Image>(), category)
                )
                return@subscribeAlways
            }

            if (!moca.isInCd(groupId, "replyCD")) {
                when {
                    messageContent.contains("使用说明") -> {
                        subject.sendMessage("使用说明：https://mocabot.cn/")
                        moca.setCd(groupId, "replyCD")
                        return@subscribeAlways
                    }
                    messageContent.contains("青年大学习") -> {
                        subject.sendMessage(moca.getBotConfig("QNDXX"))
                        moca.setCd(groupId, "replyCD")
                        return@subscribeAlways
                    }
                }

                messageContent
                    .replace("摩卡", "moca")
                    .replace("爪巴", "爬")
                    .replace("老婆", "lp")
                    .toLowerCase()
                    .also {
                        if (moca.isInCd(groupId, "keaiPaCD")) {
                            return@subscribeAlways
                        }
                        if (it.contains("moca") && it.contains("爬")) {
                            if (randomDo(50)) {
                                mocaPaPath.listFiles()?.random()?.let { file -> subject.sendImage(file) }
                                moca.setCd(groupId, "keaiPaCD")
                            }

                            return@subscribeAlways
                        }
                        if ((it.contains("moca") && it.contains("可爱")) ||
                            (it.contains("moca") && it.contains("lp"))
                        ) {
                            if (randomDo(50)) {
                                mocaKeaiPath.listFiles()?.random()?.let { file -> subject.sendImage(file) }
                                moca.setCd(groupId, "keaiPaCD")
                            }
                            return@subscribeAlways
                        }
                    }


                val preProcessedContent = messageContent
                    .replace("我", "w")
                    .replace("老婆", "lp")
                    .replace("事", "是")
                if (preProcessedContent.startsWith("wlp是")) {
                    val setLpResult = moca.setUserLp(groupId, senderId, preProcessedContent)
                    subject.sendMessage(setLpResult)
                    return@subscribeAlways
                }

                if (messageContent.contains("来点") &&
                    messageContent
                        .toLowerCase()
                        .replace("老婆", "lp")
                        .contains("lp")
                ) {
                    val lpName = moca.getUserLp(senderId)
                    if (lpName !in moca.getGroupKeyword(groupId).keys) {
                        subject.sendMessage("az，您还没有设置lp或者这个群没有找到nlp呢...")
                        return@subscribeAlways
                    }
                    val doubleLp =
                        messageContent.startsWith("多") && moca.groupConfigEnabled(groupId, "pan")
                    val imageParameter = Pair(lpName, doubleLp)
                    groupMessageHandler.sendPicture(imageParameter)
                    moca.setCd(groupId, "replyCD")
                    return@subscribeAlways
                }

                if (messageContent.startsWith("!") || messageContent.startsWith("！")
                ) {
                    // exclamation mark processor
                    groupMessageHandler.exclamationMarkProcessor().also {
                        if (it) {
                            moca.setCd(groupId, "replyCD")
                            return@subscribeAlways
                        }
                    }
                }

                if (messageContent.startsWith("查看群图片")) {
                    val tempId = message.filterIsInstance<PlainText>()
                        .firstOrNull()
                        .toString()
                        .substring(5)
                        .replace("\n", "")
                    val picId = try {
                        tempId.toInt()
                    }catch (e: NumberFormatException) {
                        subject.sendMessage("错误：ID有误")
                        return@subscribeAlways
                    }
                    if (picId < 1 || picId > 999) {
                        subject.sendMessage("错误：ID范围：1~999")
                        return@subscribeAlways
                    }
                    groupMessageHandler.sendGroupPicture(picId)
                    moca.setCd(groupId, "replyCD")
                    return@subscribeAlways
                }

                moca.matchGroupPicKey(groupId, messageContent).also {
                    if (it) {
                        groupMessageHandler.sendGroupPicture()
                        moca.setCd(groupId, "replyCD")
                        return@subscribeAlways
                    }
                }

                val matchResult = moca.matchKey(groupId, messageContent)
                if (matchResult.first != "") {
                    groupMessageHandler.sendPicture(matchResult)
                    moca.setCd(groupId, "replyCD")
                    return@subscribeAlways
                }
            }

            groupMessageHandler.groupRepeatSaver().also { toRepeat ->
                if (toRepeat) {
                    if (!moca.isInCd(groupId, "repeatCD")) {
                        randomDo(
                            moca.getGroupConfig(groupId, "repeatChance")
                                .toString().toInt()
                        ).also { random ->
                            if (random) {
                                groupMessageHandler.sendRepeatContent()
                                moca.setCd(groupId, "repeatCD")
                            }
                        }
                    }
                }
            }
        }

        bot.eventChannel.subscribeAlways<FriendMessageEvent> {
            if (moca.isSuperman(sender.id)) {
                if (message.content.startsWith("#gg")) {
                    val toSendContent = mutableListOf<SingleMessage>()
                    message.forEach {
                        if (it.content.startsWith("#gg")) {
                            toSendContent.add(it.content.replace("#gg\n", "").toPlainText())
                        } else {
                            toSendContent.add(it)
                        }
                    }
                    bot.groups.forEach {
                        mocaLogger.info("Sending Message to: " + it.id.toString())
                        it.sendMessage(toSendContent.toMessageChain())
                        delay(1000)
                    }
                }
            }
        }

        bot.eventChannel.subscribeAlways<BotJoinGroupEvent> {
            mocaLogger.info("Joining new group. Init group ${group.id}")
            moca.initGroup(group.id)
            group.sendMessage("大家好，我是moca\n使用说明：http://mocabot.cn/\n请仔细查看使用说明并按照格式使用哦！")
        }

        bot.eventChannel.subscribeAlways<MemberLeaveEvent> {
            mocaLogger.info("Member leaving group ${group.id}.")
            if (moca.isSuperman(member.id)) {
                group.sendMessage("开发者${member.id}被移除，自动退出群组...")
                mocaLogger.warning("Superman leaving group: ${group.id}.")
                mocaLog("SupermanLeaveEvent", groupId = groupId,
                    description = "memberId = ${member.id}")
                group.quit()
            }
        }

        bot.eventChannel.subscribeAlways<MemberJoinEvent> {
            mocaLogger.info("Member join group ${group.id}.")
            if (moca.getGroupConfig(group.id, "welcomeNewMemberJoin").toString().toInt() == 1) {
                val toSendMessage = buildMessageChain {
                    +At(member)
                    +PlainText(" 欢迎加入 ${group.name}！")
                }
                group.sendMessage(toSendMessage)
            }
        }

        bot.eventChannel.subscribeAlways<BotInvitedJoinGroupRequestEvent> {
            mocaLogger.info("Moca invited to join group ${groupId}.")
            mocaLog("BotInvitedJoinGroupRequestEvent", groupId = groupId,
                description = "invitorId = $invitorId")
            if (moca.isSuperman(invitorId)) {
                mocaLogger.info("Superman invited to join group. Auto accept.")
                accept()
            }
        }

        bot.eventChannel.subscribeAlways<BotLeaveEvent> {
            mocaLog("BotLeaveEvent", groupId = group.id)
        }

        bot.eventChannel.subscribeAlways<GroupMessagePostSendEvent> {
            val currentLimit: Int? = mapGroupFrequencyLimiter[it.target.id]?.minus(1)
            val newLimit = currentLimit ?: 1
            mapGroupFrequencyLimiter[it.target.id] = newLimit
        }
    }
}





