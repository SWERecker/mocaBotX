package me.swe.main

import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.contact.Contact.Companion.uploadImage
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.getMember
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.MiraiLogger
import java.io.File
import java.lang.NumberFormatException

class MocaGroupMessageHandler(
    private val event: GroupMessageEvent,
    private val subj: Group,
    private val moca: Moca,
) {
    private val logger = MiraiLogger.create("MocaGroupHandler")
    private val messageContent = event.message.content
    private val senderId = event.sender.id
    private val groupId = event.group.id

    /**
     * 管理员操作
     */
    suspend fun adminOperations(): Boolean {
        when {
            messageContent.startsWith("设置图片cd") -> {
                try {
                    val toSetParameter = messageContent
                        .substring(6)
                        .trimEnd('秒')
                        .trimEnd('s')
                        .toInt()
                    if (toSetParameter < 5) {
                        subj.sendMessage(buildMessageChain {
                            +PlainText("图片cd最低为5秒，请重新发送.")
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
        return false
    }

    /**
     * 超级管理员操作
     */
    suspend fun supermanOperations(): Boolean {
        when (messageContent) {
            "RELOAD_REDIS" -> {
                val reloadResult = loadIndexFile()
                val resultMessage = "Reload redis database, current people count=$reloadResult"
                logger.info(resultMessage)
                subj.sendMessage(resultMessage)
                return true
            }
        }
        return false
    }

    /**
     * 获取换lp次数.
     */
    suspend fun getSenderChangeLpTimes(): Boolean {
        val changeLpTimes = moca.getChangeLpTimes(senderId)
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

    /**
     * 发送图片.
     *
     *
     */
    suspend fun sendPicture(imageParameter: Pair<String, Boolean>): Boolean {
        var panResult = ""
        var pictureCount = 1
        if (imageParameter.second) {
            moca.eatPan(senderId, 3).also {
                if (it.first) {
                    panResult = "摩卡吃掉了3个面包，你还剩${it.second}个面包哦~"
                    pictureCount = 2
                } else {
                    panResult = "呜呜呜，你的面包不够吃了呢..."
                }
            }
        }
        val pictureFiles = moca.randomPicture(imageParameter.first, pictureCount)
        val firstImage = subj.uploadImage(File(pictureFiles[0]))
        val toSendMessage = if (pictureCount == 1) {
            buildMessageChain {
                +PlainText(panResult)
                +firstImage
            }
        } else {
            val secondImage = subj.uploadImage(File(pictureFiles[1]))
            buildMessageChain {
                +PlainText(panResult)
                +firstImage
                +secondImage
            }
        }
        moca.updateCount(groupId, imageParameter.first)
        subj.sendMessage(toSendMessage)
        return true
    }

    /**
     * 包含At的操作
     */
    suspend fun atOperations(): Boolean {
        val atTarget = event.message.findIsInstance<At>()?.target
        val atTargetName = atTarget?.let { subj.getMember(it)?.nameCardOrNick }
        val messageContent = messageContent
        if (atTarget == event.bot.id) {
            println("At bot $atTarget")
            when {
                messageContent.contains("关键词") -> {
                    subj.sendImage(File(moca.buildGroupKeywordPicture(groupId)))
                    return true
                }

                (messageContent.contains("图片数量统计") || messageContent.contains("统计图片数量")) -> {
                    subj.sendImage(File(moca.buildGroupPictureCount(groupId)))
                    return true
                }

                messageContent.contains("统计次数") -> {
                    subj.sendImage(File(moca.buildGroupCountPicture(groupId)))
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
     * 发送语音
     */
    private suspend fun sendVoice(): MessageChain {
        val voiceFolder = File("resource" + File.separator + "voice")
        val voiceFiles = voiceFolder.listFiles()
        if (!voiceFiles.isNullOrEmpty()) {
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

    /**
     * !指令处理器
     */
    suspend fun exclamationMarkProcessor(): Boolean {
        val paraList = messageContent
            .replaceFirst("！", "!")
            .trimStart('!')
            .split(' ')
        if (paraList.isNullOrEmpty()) {
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
                            val k = paraList[1].toInt()
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
            }
        }
        return false
    }

    suspend fun panOperations(): Boolean {
        when (messageContent) {
            "我的面包" -> {
                subj.sendMessage(
                    buildMessageChain {
                        +At(senderId)
                        +PlainText(" 您现在有${moca.getUserPan(senderId)}个面包呢~")
                    }
                )
                return true
            }
            in arrayListOf("买面包", "来点面包") -> {
                val buyResult = moca.buyPan(senderId)
                when (buyResult.first) {
                    -1 -> {
                        var toWaitTimeString = ""
                        toWaitTimeString += if (buyResult.second < 60) {
                            "${buyResult.second}秒"
                        } else {
                            "${buyResult.second / 60}分钟"
                        }
                        subj.sendMessage(
                            buildMessageChain {
                                +At(senderId)
                                +PlainText(" 还不能买面包呢~还要等${toWaitTimeString}呢~")
                            }
                        )
                        return true
                    }
                    in (1..10) -> {
                        subj.sendMessage(
                            buildMessageChain {
                                +At(senderId)
                                +PlainText("\n初次成功购买了${buyResult.first}个面包！以后每小时可以购买一次哦~")
                                +PlainText("\n有空的话，常来买哦~")
                                +PlainText("\n你现在有${buyResult.second}个面包啦~")
                            }
                        )
                        return true
                    }
                    in (11..20) -> {
                        subj.sendMessage(
                            buildMessageChain {
                                +At(senderId)
                                +PlainText(" 成功购买了${buyResult.first - 10}个面包，你现在有${buyResult.second}个面包啦~")
                            }
                        )
                        return true
                    }
                }
            }
            in arrayListOf("吃面包", "恰面包") -> {
                val eatResult = moca.eatPan(senderId, 1)
                val resultText = if (eatResult.first) {
                    "你吃掉了1个面包，还剩${eatResult.second}个面包了哦~"
                } else {
                    "呜呜呜，面包不够吃了呢..."
                }
                subj.sendMessage(
                    buildMessageChain {
                        +At(senderId)
                        +PlainText(resultText)
                    }
                )
                return true
            }
        }
        return false
    }
}
