package me.swe.main


import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol.ANDROID_PHONE
import net.mamoe.mirai.utils.LoggerAdapters.asMiraiLogger
import net.mamoe.mirai.utils.MiraiExperimentalApi
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val mocaDBLogger: Logger = LoggerFactory.getLogger("MocaDB")
val mocaLogger: Logger = LoggerFactory.getLogger("Moca")

@MiraiExperimentalApi
fun main() {
    MocaWithConfig.main()
}

object MocaWithConfig {
    @MiraiExperimentalApi
    @JvmStatic
    fun main(): Unit = runBlocking {
        val botQQ = getBotConfig("QQ").toLong()
        val botPassword = getBotConfig("PASS")
        picturePath = getBotConfig("PIC_PATH")
        resetGroupFrequencyLimiterTime = System.currentTimeMillis() / 1000

        // moca.testFunction()
        mocaLogger.asMiraiLogger()
        val bot = BotFactory.newBot(botQQ, botPassword) {
            fileBasedDeviceInfo() // 使用 device.json 存储设备信息
            protocol = ANDROID_PHONE // 切换协议
            redirectNetworkLogToDirectory()
            redirectBotLogToDirectory()
        }.alsoLogin()

        bot.eventChannel.subscribeAlways<GroupMessageEvent> {
            val gmHandler = MocaGroupMessage(this)
            gmHandler.messageHandler()
        }

        bot.eventChannel.subscribeAlways<FriendMessageEvent> {
            val fmHandler = MocaFriendMessage(this)
            fmHandler.messageHandler()
        }

        bot.eventChannel.subscribeAlways<BotJoinGroupEvent> {
            mocaLogger.info("${group.id}: Bot join new group. Init group")
            moca.initGroup(group.id)
            group.sendMessage("大家好，我是moca\n使用说明：https://mocabot.cn/\n请仔细查看使用说明并按照格式使用哦！")
        }

        bot.eventChannel.subscribeAlways<MemberLeaveEvent> {
            mocaLogger.debug("${group.id}: Member left")
            if (moca.isSuperman(member.id)) {
                group.sendMessage("开发者${member.id}被移除，自动退出群组...")
                mocaLogger.warn("${group.id}: Superman left")
                mocaLog("SupermanLeaveEvent", groupId = groupId,
                    description = "memberId = ${member.id}")
                group.quit()
            }
        }

        bot.eventChannel.subscribeAlways<MemberJoinEvent> {
            mocaLogger.debug("${group.id}: New member.")
            if(moca.groupConfigEnabled(group.id, "welcomeNewMemberJoin")) {
                group.sendMessage(
                    buildMessageChain {
                        +At(member)
                        +PlainText(" 欢迎加入 ${group.name}！")
                    }
                )
            }
        }

        bot.eventChannel.subscribeAlways<BotInvitedJoinGroupRequestEvent> {
            mocaLogger.debug("$groupId: Invited to join group.")
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





