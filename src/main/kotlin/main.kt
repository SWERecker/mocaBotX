package me.swe.main


import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.contact.isOperator
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol.ANDROID_PAD
import net.mamoe.mirai.utils.MiraiLogger

fun main() {
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

        val bot = BotFactory.newBot(botQQ, botPassword) {
            fileBasedDeviceInfo() // 使用 device.json 存储设备信息
            protocol = ANDROID_PAD // 切换协议
            noNetworkLog()
        }.alsoLogin()

        // bot.getFriend(565379987)?.sendMessage("你好")
        // bot.getGroup(907274961)?.sendMessage("Bot 上线")

        bot.eventChannel.subscribeAlways<GroupMessageEvent> {
            val groupMessageHandler = MocaGroupMessageHandler(this, subject, moca)
            val messageContent = this.message.content
            val senderId = sender.id
            val groupId = group.id

            if (moca.isSuperman(senderId)) {
                groupMessageHandler.supermanOperations().also {
                    if (it) {
                        return@subscribeAlways
                    }
                }
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
                groupMessageHandler.panOperations().also {
                    if (it) {
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


            if (moca.isInCd(groupId, "replyCD")) {
                mocaLogger.info("Group $groupId in cd")
                return@subscribeAlways
            }

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
                        return@subscribeAlways
                    }
                }
            }

            val matchResult = moca.matchKey(groupId, messageContent)
            if (matchResult.first != "") {
                groupMessageHandler.sendPicture(matchResult)
                moca.setCd(groupId, "replyCD")
                return@subscribeAlways
            }
        }


        bot.eventChannel.subscribeAlways<FriendMessageEvent> {
            if (moca.isSuperman(sender.id)) {
                subject.sendMessage("Hello Master.")
            }
        }

        bot.eventChannel.subscribeAlways<BotJoinGroupEvent> {
            mocaLogger.info("Joining new group. Init group ${group.id}")
            moca.initGroup(group.id)
        }

        bot.eventChannel.subscribeAlways<MemberLeaveEvent> {
            mocaLogger.info("Member leaving group ${group.id}.")
            if (moca.isSuperman(member.id)) {
                group.sendMessage("开发者${member.id}被移除，自动退出群组...")
                mocaLogger.warning("Superman leaving group: ${group.id}.")
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
            if (moca.isSuperman(invitorId)) {
                mocaLogger.info("Superman invited to join group. Auto accept.")
                accept()
            }
        }
    }
}





