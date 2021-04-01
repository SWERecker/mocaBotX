package me.swe.main

import net.mamoe.mirai.contact.Contact.Companion.uploadImage
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.getMember
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.MiraiLogger
import java.io.File

class MocaGroupMessageHandler(
    private val event: GroupMessageEvent,
    private val subj: Group,
    private val mocaInstance: Moca
) {
    private val logger = MiraiLogger.create("MocaGroupHandler")
    suspend fun exampleOperation() {
        val toSendMessage = buildMessageChain {
            +PlainText("hello!")
        }
        subj.sendMessage(toSendMessage)
    }

    fun adminOperations(): Boolean {

        return false
    }

    suspend fun supermanOperations(): Boolean {
        when (event.message.content) {
            "RELOAD_REDIS" -> {
                val reloadResult = mocaInstance.loadIndexFile()
                val resultMessage = "Reload redis database, current people count=$reloadResult"
                logger.info(resultMessage)
                subj.sendMessage(resultMessage)
                return true
            }
        }
        return false
    }

    suspend fun getSenderChangeLpTimes(): Boolean {
        val changeLpTimes = mocaInstance.getChangeLpTimes(event.sender.id)
        subj.sendMessage(
            if (changeLpTimes == 0) {
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

    suspend fun sendPicture(imageParameter: Pair<String, Boolean>): Boolean {
        val pictureCount = if (imageParameter.second) 2 else 1
        val pictureFiles = mocaInstance.randomPicture(imageParameter.first, pictureCount)
        val firstImage = subj.uploadImage(File(pictureFiles[0]))
        val toSendMessage = if (pictureCount == 1) {
            buildMessageChain {
                +firstImage
            }
        } else {
            val secondImage = subj.uploadImage(File(pictureFiles[1]))
            buildMessageChain {
                +firstImage
                +secondImage
            }
        }
        subj.sendMessage(toSendMessage)
        return true
    }

    suspend fun atOperations(): Boolean {
        val atTarget = event.message.findIsInstance<At>()?.target
        val atTargetName = atTarget?.let { subj.getMember(it)?.nameCardOrNick }
        val messageContent = event.message.content
        if (atTarget == event.bot.id) {
            println("At bot $atTarget")
            when {
                messageContent.contains("统计图片数量") -> {
                    subj.sendMessage(mocaInstance.buildGroupKeywordPicture())
                    return true
                }

                messageContent.contains("图片数量统计") -> {
                    subj.sendMessage(mocaInstance.buildAllPictureCountPicture())
                    return true
                }

                messageContent.contains("统计次数") -> {
                    subj.sendMessage(mocaInstance.buildGroupCountPicture())
                    return true
                }
                messageContent.contains("语音") -> {
                    subj.sendMessage(mocaInstance.sendVoice())
                    return true
                }
            }
        } else {
            println("At $atTarget")
            when {
                messageContent
                    .replace("老婆", "lp")
                    .contains("换lp次数")
                -> {
                    val changeLpTimes = atTarget?.let { mocaInstance.getChangeLpTimes(it) }
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
}
