package me.swe.main

import kotlinx.coroutines.delay
import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.message.data.SingleMessage
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.message.data.toMessageChain
import net.mamoe.mirai.message.data.toPlainText

class MocaFriendMessage(
    private val event: FriendMessageEvent,
){
    private val messageContent = event.message.content
    private val senderId = event.sender.id


    suspend fun messageHandler() {
        supermanOperation()
    }

    private suspend fun supermanOperation() {
        if (moca.isSuperman(senderId)) {
            if (messageContent.startsWith("#gg")) {
                val toSendContent = mutableListOf<SingleMessage>()
                event.message.forEach {
                    if (it.content.startsWith("#gg")) {
                        toSendContent.add(it.content.replace("#gg\n", "").toPlainText())
                    } else {
                        toSendContent.add(it)
                    }
                }
                val ignoreGroups = mutableListOf<Long>()
                getBotConfig("GG_IGNORE_GROUPS").split(",").also {
                    for (str in it) {
                        ignoreGroups.add(str.toLong())
                    }
                }
                event.bot.groups.forEach {
                    if (it.id !in ignoreGroups) {
                        mocaLogger.info("Superman: Sending Message to: ${it.id}")
                        it.sendMessage(toSendContent.toMessageChain())
                    }
                    delay(1000)
                }
            }
        }
    }
}