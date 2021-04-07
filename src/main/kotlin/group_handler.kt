package me.swe.main

import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.contact.Contact.Companion.uploadImage
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.getMember
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.ExternalResource
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.MiraiLogger
import java.io.File
import java.lang.NumberFormatException

class MocaGroupMessageHandler(
    private val event: GroupMessageEvent,
    private val subj: Group,
    private val mocaInstance: Moca
) {
    private val logger = MiraiLogger.create("MocaGroupHandler")

    suspend fun adminOperations(): Boolean {
        val messageContent = event.message.content
        when {
            messageContent.startsWith("设置图片cd") -> {
                try {
                    val toSetParameter = messageContent
                        .substring(6)
                        .trimEnd('秒')
                        .trimEnd('s')
                        .toInt()
                    if (toSetParameter < 5){
                        subj.sendMessage(buildMessageChain {
                            +PlainText("图片cd最低为5秒，请重新发送.")
                        })
                        return true
                    }
                    mocaInstance.setGroupFunction(event.group.id, "replyCD", toSetParameter)
                    subj.sendMessage(buildMessageChain {
                        +PlainText("图片cd设置为：${toSetParameter}秒")
                    })
                } catch (e: NumberFormatException) {
                    subj.sendMessage(buildMessageChain {
                        +PlainText("参数错误。\n设置例：【设置图片cd15秒】")
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
                    mocaInstance.setGroupFunction(event.group.id, "repeatCD", toSetParameter)
                    subj.sendMessage(buildMessageChain {
                        +PlainText("复读cd设置为：${toSetParameter}秒")
                    })
                } catch (e: NumberFormatException) {
                    subj.sendMessage(buildMessageChain {
                        +PlainText("参数错误。\n设置例：【设置复读cd120秒】")
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
                    mocaInstance.setGroupFunction(event.group.id, "repeatChance", toSetParameter)
                    subj.sendMessage(buildMessageChain {
                        +PlainText("复读概率设置为：${toSetParameter}%")
                    })
                } catch (e: NumberFormatException) {
                    subj.sendMessage(buildMessageChain {
                        +PlainText("参数错误。\n设置例：【设置复读概率30%】")
                    })
                    return true
                }
            }

            messageContent.startsWith("查看当前参数") -> {
                subj.sendMessage("当前参数：\n" +
                        "图片cd=${mocaInstance.getGroupConfig(event.group.id, "replyCD")}秒\n" +
                        "复读cd=${mocaInstance.getGroupConfig(event.group.id, "repeatCD")}秒\n" +
                        "复读概率=${mocaInstance.getGroupConfig(event.group.id, "repeatChance")}%"
                )
                return true
            }
        }

        if (messageContent.startsWith("打开") || messageContent.startsWith("关闭")) {
            val operationString = messageContent.subSequence(0, 2)
            val operation = if (operationString == "打开") 1 else 0
            when (messageContent.substring(2)) {
                "面包功能" -> {
                    mocaInstance.setGroupFunction(event.group.id, "pan", operation)
                    subj.sendMessage("${event.group.id}已${operationString}面包功能")
                    return true
                }
                "翻译功能" -> {
                    mocaInstance.setGroupFunction(event.group.id, "trans", operation)
                    subj.sendMessage("${event.group.id}已${operationString}翻译功能")
                    return true
                }
                "随机选歌" -> {
                    mocaInstance.setGroupFunction(event.group.id, "random", operation)
                    subj.sendMessage("${event.group.id}已${operationString}随机选歌功能")
                    return true
                }
                "指令功能" -> {
                    mocaInstance.setGroupFunction(event.group.id, "command", operation)
                    subj.sendMessage("${event.group.id}已${operationString}【!】指令功能")
                    return true
                }
                "实验功能" -> {
                    mocaInstance.setGroupFunction(event.group.id, "exp", operation)
                    subj.sendMessage("${event.group.id}已${operationString}实验功能")
                    return true
                }
                "欢迎新人" -> {
                    mocaInstance.setGroupFunction(event.group.id, "welcomeNewMemberJoin", operation)
                    subj.sendMessage("${event.group.id}已${operationString}欢迎新人功能")
                    return true
                }
            }
        }
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
                messageContent.contains("关键词") -> {
                    subj.sendImage(File(mocaInstance.buildGroupKeywordPicture(event.group.id)))
                    return true
                }

                (messageContent.contains("图片数量统计") || messageContent.contains("统计图片数量")) -> {
                    subj.sendImage(File(mocaInstance.buildGroupPictureCount(event.group.id)))
                    return true
                }

                messageContent.contains("统计次数") -> {
                    subj.sendImage(File(mocaInstance.buildGroupCountPicture(event.group.id)))
                    return true
                }
                messageContent.contains("语音") -> {
                    subj.sendMessage(sendVoice())
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

    private suspend fun sendVoice(): MessageChain{
        val voiceFolder = File("resource" + File.separator + "voice")
        val voiceFiles = voiceFolder.listFiles()
        if(!voiceFiles.isNullOrEmpty()){
            val voiceFile = File(voiceFiles.random().absolutePath).toExternalResource()
            voiceFile.use {
                val uploadVoice = subj.uploadVoice(voiceFile)
                return buildMessageChain {
                    +uploadVoice
                }
            }

        }

        return buildMessageChain {
        }
    }
}
