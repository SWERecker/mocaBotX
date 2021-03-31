package me.swe.main

import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.contact.Contact.Companion.uploadImage
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import net.mamoe.mirai.utils.MiraiLogger
import java.io.File

class MocaGroupMessageHandler(
    private val event: GroupMessageEvent,
    private val subj: Group,
    private val mocaInstance: Moca
    ){
    private val logger = MiraiLogger.create("MocaGroupHandler")
    suspend fun exampleOperation(){
        val toSendMessage = buildMessageChain {
            +PlainText("hello!")
        }
        subj.sendMessage(toSendMessage)
    }

    suspend fun adminOperations(): Boolean{

        return false
    }

    suspend fun supermanOperations(): Boolean{
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

    suspend fun sendPicture(imageParameter: Pair<String, Boolean>): Boolean{
        val pictureCount = if (imageParameter.second) 2 else 1
        val pictureFiles = mocaInstance.randomPicture(imageParameter.first, pictureCount)
        val firstImage = subj.uploadImage(File(pictureFiles[0]))
        val toSendMessage = if (pictureCount == 1){
            buildMessageChain {
                +firstImage
            }
        }else{
            val secondImage = subj.uploadImage(File(pictureFiles[1]))
            buildMessageChain {
                +firstImage
                +secondImage
            }
        }
        subj.sendMessage(toSendMessage)
        return true
    }
}