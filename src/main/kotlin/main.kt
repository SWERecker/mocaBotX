package me.swe.main


import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.contact.isOperator
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol.ANDROID_PAD
import net.mamoe.mirai.utils.MiraiLogger
import java.io.File


fun main() {
    WithConfiguration.main()
}

object WithConfiguration {
    @JvmStatic
    fun main(): Unit = runBlocking {
        val mocaDB = MocaDatabase()
        val moca = Moca(mocaDB)
        val mocaLogger = MiraiLogger.create("MocaLogger")

        val botQQ = moca.getBotConfig("QQ").toLong()
        val botPassword = moca.getBotConfig("PASS")

        val bot = BotFactory.newBot(botQQ, botPassword) {
            fileBasedDeviceInfo() // 使用 device.json 存储设备信息
            protocol = ANDROID_PAD // 切换协议
            noNetworkLog()
        }.alsoLogin()

        // bot.getFriend(565379987)?.sendMessage("你好")
        // bot.getGroup(907274961)?.sendMessage("Bot 上线")

        bot.eventChannel.subscribeAlways<GroupMessageEvent> { event ->
            // this: GroupMessageEvent
            // event: GroupMessageEvent
            val groupMessageHandler = MocaGroupMessageHandler(event, subject, moca)
            val messageContent = this.message.content
            var operationResult = false

            if (event.sender.id in moca.supermanId) {
                operationResult = groupMessageHandler.supermanOperations()
            }

            if (!operationResult) {
                if (event.sender.permission.isOperator() || event.sender.id in moca.supermanId) {
                    operationResult = groupMessageHandler.adminOperations()
                }
            }
            if (moca.isInCd(group.id, "replyCD")){
                operationResult = true
                mocaLogger.info("Group in cd ${group.id}")
            }

            if (!operationResult) {
                when {
                    messageContent.contains("使用说明") -> {
                        subject.sendMessage("使用说明：https://mocabot.cn/")
                        operationResult = true
                        moca.setCd(group.id, "replyCD")
                    }
                    messageContent.contains("青年大学习") -> {
                        val qndxxContent = File(moca.qndxxPath).readText()
                        subject.sendMessage(qndxxContent)
                        operationResult = true
                        moca.setCd(group.id, "replyCD")
                    }
                }
            }

            if (!operationResult){
                val matchResult = mocaDB.matchKey(group.id, messageContent)
                if (matchResult.first != "") {
                    operationResult = groupMessageHandler.sendPicture(matchResult)
                    moca.setCd(group.id, "replyCD")
                }
            }
        }

        bot.eventChannel.subscribeAlways<FriendMessageEvent> { event ->
            if (event.sender.id == 565379987L) {
                subject.sendMessage("Hello Master.")
            }
        }

        bot.eventChannel.subscribeAlways<BotJoinGroupEvent> { event ->
            mocaLogger.info("Joining new group. Init group ${event.group.id}")
            mocaDB.initGroup(event.group.id)
        }

        bot.eventChannel.subscribeAlways<MemberLeaveEvent> { event ->
            mocaLogger.info("Member leaving group ${event.group.id}.")
            if (event.member.id in moca.supermanId) {
                event.group.sendMessage("开发者${event.member.id}被移除，自动退出群组...")
                mocaLogger.warning("Superman leaving group: ${event.group.id}.")
                event.group.quit()
            }
        }

        bot.eventChannel.subscribeAlways<MemberJoinEvent> { event ->
            mocaLogger.info("Member join group ${event.group.id}.")
            if (mocaDB.getGroupConfig(event.group.id, "welcomeNewMemberJoin") == 1) {
                val toSendMessage = buildMessageChain {
                    +At(event.member)
                    +PlainText(" 欢迎加入 ${event.group.name}！")
                }
                event.group.sendMessage(toSendMessage)
            }
        }

        bot.eventChannel.subscribeAlways<BotInvitedJoinGroupRequestEvent> { event ->
            mocaLogger.info("Moca invited to join group ${event.groupId}.")
            if (event.invitorId in moca.supermanId) {
                mocaLogger.info("Superman invited to join group. Auto accept.")
                event.accept()
            }
        }
    }
}





