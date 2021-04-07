package me.swe.main

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Projections
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.buildMessageChain
import net.mamoe.mirai.utils.MiraiLogger
import org.bson.Document
import javax.print.Doc
import java.util.Collections

import java.util.ArrayList

import java.util.Comparator

import java.util.TreeMap





class MocaDatabase {
    private val mongoClient: MongoClient = MongoClients.create()
    private val db: MongoDatabase = mongoClient.getDatabase("moca")
    private val colGroupKeyword: MongoCollection<Document> = db.getCollection("group_keyword")
    private val colGroupConfig: MongoCollection<Document> = db.getCollection("group_config")
    val colGroupCount: MongoCollection<Document> = db.getCollection("group_count")
    val colUserConfig: MongoCollection<Document> = db.getCollection("user_config")
    private val mapGroupKeywords = mutableMapOf<Long, Map<String, String>>()
    val mapGroupConfig = mutableMapOf<Long, Any>()
    private val mocaDBLogger = MiraiLogger.create("MocaDBLogger")

    /**
     * 初始化，从数据库中加载所有群组的关键词列表至内存.
     */
    init {
        loadAllGroupKeyword()
        loadAllGroupConfig()
    }

    /**
     * 从数据库中加载所有群组的关键词列表.
     */
    private fun loadAllGroupKeyword() {
        val queryResult = colGroupKeyword.find()
            .projection(Projections.excludeId())
        for (groupMap in queryResult) {
            loadKeywordToCache(groupMap)
        }
    }

    /**
     * 从数据库中加载所有群组参数.
     */
    private fun loadAllGroupConfig() {
        val queryResult = colGroupConfig.find()
            .projection(Projections.excludeId())
        for (groupMap in queryResult) {
            loadConfigToCache(groupMap)
        }
    }

    /**
     * 从缓存中获取某群组的关键词列表.
     *
     * @param groupId 群号
     *
     * @return 群组的关键词列表 Map<String, String>
     *
     */
    fun getGroupKeyword(groupId: Long): Map<String, String> {
        if (groupId !in mapGroupKeywords.keys) {
            initGroup(groupId)
        }
        return mapGroupKeywords[groupId] as Map<String, String>
    }

    /**
     * 新加入某个群时，初始化该群的关键词列表和参数.
     *
     * @param groupId 群号
     *
     * @return
     *
     */
    fun initGroup(groupId: Long) {
        val query = Document()
        var keyInited = false
        var configInited = false
        val groupIdInt = groupId.toInt()
        query["group"] = groupId

        // Check if the group keyword and config exists.
        val queryKeyCheckResult = colGroupKeyword.find(query).first()
        if (!queryKeyCheckResult.isNullOrEmpty()) {
            mocaDBLogger.info("Group $groupIdInt keyword exist, skip init keyword")
            keyInited = true
        }

        val queryConfigCheckResult = colGroupConfig.find(query).first()
        if (!queryConfigCheckResult.isNullOrEmpty()) {
            mocaDBLogger.info("Group $groupIdInt config exist, skip init config")
            configInited = true
        }

        if (!keyInited) {
            mocaDBLogger.info("========Init group $groupIdInt keyword========")
            query["group"] = "key_template"
            val queryResult = colGroupKeyword.find(query).first()
            if (queryResult.isNullOrEmpty()) {
                mocaDBLogger.error("[ERROR] Template keyword not found.")
                return
            }
            val templateGroupKeyword = queryResult["keyword"]
            val toInsertDocument = Document("group", groupIdInt)
                .append("keyword", templateGroupKeyword)
            val insertResult = colGroupKeyword.insertOne(toInsertDocument)
            if (insertResult.wasAcknowledged()) {
                mocaDBLogger.info("Inserted, id=" + insertResult.insertedId)
                keyInited = true
            }
        }

        if (!configInited) {
            mocaDBLogger.info("========Init group $groupIdInt config========")
            query["group"] = "config_template"
            val queryResult = colGroupConfig.find(query)
                .projection(Projections.excludeId())
                .first()
            if (queryResult.isNullOrEmpty()) {
                mocaDBLogger.error("[ERROR] Template config not found.")
                return
            }
            queryResult["group"] = groupIdInt
            val insertResult = colGroupConfig.insertOne(queryResult)
            if (insertResult.wasAcknowledged()) {
                mocaDBLogger.info("Inserted, id=" + insertResult.insertedId)
                configInited = true
            }
        }
        if (keyInited && configInited) {
            mocaDBLogger.info("Successfully initialized group $groupIdInt")
            loadGroupKeyword(groupId)
            loadGroupConfig(groupId)
        }
    }

    /**
     * 将关键词列表保存至缓存和数据库.
     *
     * @param groupId 群号
     * @param newGroupKeyword: 要存储的groupKeyword
     *
     * @return
     *
     */
    fun saveGroupKeyword(groupId: Long, newGroupKeyword: Map<String, String>) {
        val query = Document()
            .append("group", groupId)
        val operationDocument = Document("${'$'}set", Document("keyword", newGroupKeyword))
        val saveResult = colGroupKeyword.updateOne(query, operationDocument)
        if (saveResult.modifiedCount > 0) {
            mocaDBLogger.info("[$groupId] Reloading groupKeyword ")
            loadGroupKeyword(groupId)
        }
    }

    /**
     * 从数据库中获取某群组的关键词列表.
     *
     * @param groupId 群号
     *
     * @return
     *
     */
    private fun loadGroupKeyword(groupId: Long) {
        val query = Document()
            .append("group", groupId)
        val queryResult = colGroupKeyword.find(query)
            .projection(Projections.excludeId())
            .first()
        if (!queryResult.isNullOrEmpty()) {
            loadKeywordToCache(queryResult)
        }
    }

    /**
     * 从数据库中获取某群组的次数统计.
     *
     * @param groupId 群号
     *
     * @return
     *
     */
    fun getGroupCount(groupId: Long): Map<String, String> {
        val query = Document()
            .append("group", groupId)
        val mapSorter = MapSort()
        val queryResult = colGroupCount.find(query)
            .projection(Projections.fields(Projections.excludeId(), Projections.exclude("group")))
            .first()
        val groupCountMap = mutableMapOf<String, Int>()

        if (!queryResult.isNullOrEmpty()) {
            queryResult.forEach {
                groupCountMap[it.key] = it.value as Int
            }
        }
        return mapSorter.mapSortByValue(groupCountMap.toMap())
    }

    /**
     * 从数据库中获取某群组的参数.
     *
     * @param groupId 群号
     *
     * @return
     *
     */
    private fun loadGroupConfig(groupId: Long) {
        val query = Document()
            .append("group", groupId)
        val queryResult = colGroupConfig.find(query)
            .projection(Projections.excludeId())
            .first()
        if (!queryResult.isNullOrEmpty()) {
            loadConfigToCache(queryResult)
        }
    }

    /**
     * 将相应参数设置到数据库
     *
     * @param userId QQ号/群号
     * @param arg 参数名称
     * @param value 要设置的值
     *
     * @return 返回修改成功/失败(true/false)
     *
     */
    fun setConfig(userId: Long, setType: String, arg: String, value: Any): Boolean {
        val query = if (setType == "GROUP") {
            Document()
                .append("group", userId)
        } else {
            Document()
                .append("qq", userId)
        }
        val operationDocument = Document("${'$'}set", Document(arg, value))
        val saveResult = if (setType == "GROUP") {
            colGroupConfig.updateOne(query, operationDocument)
        } else {
            colUserConfig.updateOne(query, operationDocument)
        }
        if (saveResult.modifiedCount > 0) {
            mocaDBLogger.info("Set $arg => $value")
            if (setType == "GROUP") {
                mocaDBLogger.info("Load $userId config to cache")
                loadGroupConfig(userId)
            }
        } else {
            mocaDBLogger.info("DB not modified")
        }
        return saveResult.modifiedCount > 0
    }

    /**
     * 从数据库中获取相应用户参数.
     *
     * @param userId 群号
     * @param arg 参数名称
     *
     * @return 参数值
     *
     */
    fun getUserConfig(userId: Long, arg: String): Any? {
        val query = Document()
            .append("qq", userId)
        val queryResult = colUserConfig.find(query)
            .projection(Projections.fields(Projections.excludeId(), Projections.include(arg)))
            .first()
        if (!queryResult.isNullOrEmpty()) {
            return queryResult[arg]
        }
        return "NOT_FOUND"
    }

    /**
     * 匹配关键词，同时返回是否含双倍关键词
     *
     * @param groupId 群号
     * @param key 用户消息内容
     *
     * @return Pair(name, isDouble)
     *
     */
    fun matchKey(groupId: Long, key: String): Pair<String, Boolean> {
        var name = ""
        var isDouble = false
        val groupKeyword = getGroupKeyword(groupId)
        for ((k, v) in groupKeyword) {
            if (Regex(v).find(key.toLowerCase()) != null) {
                name = k
                break
            }
        }
        if (key.startsWith("多")) {
            isDouble = true
        }
        return Pair(name, isDouble)
    }

    /**
     * 将keywordMap存储进mapGroupKeyword的具体实现.
     *
     * @param toLoadMap 从数据库中取出的数据.
     *
     */
    private fun loadKeywordToCache(toLoadMap: Map<*, *>) {
        try {
            val groupId = toLoadMap["group"] as Int
            val groupKeyword = toLoadMap["keyword"] as Document
            val tempKeyword = mutableMapOf<String, String>()
            groupKeyword.forEach {
                tempKeyword[it.key.toString()] = it.value.toString()
            }
            mapGroupKeywords[groupId.toLong()] = tempKeyword
        } catch (e: ClassCastException) {
            mocaDBLogger.error("Kotlin Error group: ${toLoadMap["group"]}")
        } catch (e: java.lang.ClassCastException) {
            mocaDBLogger.error("Java Error in group: ${toLoadMap["group"]}")
        }
    }

    /**
     * 将configMap存储进mapGroupConfig的具体实现.
     *
     * @param toLoadDocument 从数据库中取出的数据.
     *
     */
    private fun loadConfigToCache(toLoadDocument: Document) {
        try {
            val groupId = toLoadDocument["group"] as Int
            toLoadDocument.remove("group")
            mapGroupConfig[groupId.toLong()] = toLoadDocument.toMap()
        } catch (e: ClassCastException) {
            mocaDBLogger.error("Kotlin Error group: ${toLoadDocument["group"]}")
        } catch (e: java.lang.ClassCastException) {
            mocaDBLogger.error("Java Error in group: ${toLoadDocument["group"]}")
        }
    }

    /**
     * 设置用户的lp
     *
     * @param userId 用户QQ号
     * @param preProcessedContent 用户消息
     *
     */
    fun setUserLp(groupId: Long, userId: Long, preProcessedContent: String): MessageChain {
        val toSetLpName = preProcessedContent.substringAfter("是")
        val existUserLp = getUserConfig(userId, "lp") as String
        if (toSetLpName.replace("？", "?")
                .contains("?") ||
            toSetLpName.contains("谁")
        ) {
            return if (existUserLp == "NOT_SET") {
                buildMessageChain {
                    +At(userId)
                    +PlainText(" 您还没有设置lp呢，用【wlp是xxx】来设置一个吧~")
                }

            } else {
                buildMessageChain {
                    +At(userId)
                    +PlainText(" 您设置的lp是：${existUserLp}")
                }
            }
        }
        if (existUserLp == toSetLpName) {
            return buildMessageChain {
                +At(userId)
                +PlainText(" 您已经将${existUserLp}设置为您的lp了，请勿重复设置哦~")
            }
        }
        val groupKeyword = getGroupKeyword(groupId)
        return if (toSetLpName in groupKeyword.keys) {
            mocaDBLogger.info("set $userId lp to $toSetLpName")
            setConfig(userId, "USER", "lp", toSetLpName)
            buildMessageChain {
                +At(userId)
                +PlainText(" 设置lp为：$toSetLpName")
            }
        } else {
            val matchResult = matchLp(toSetLpName, groupKeyword)
            if (matchResult.isNullOrEmpty()) {
                buildMessageChain {
                    +At(userId)
                    +PlainText("\n没有在此群找到您要设置的lp哦~\n")
                    +PlainText("请发送【@摩卡 关键词列表】查看可设置的关键词列表哦~")
                }
            } else {
                var resultString = "\n未找到${toSetLpName}，您要设置的可能是：\n"
                matchResult.forEach { (name, _) ->
                    resultString += name + "\n"
                }
                buildMessageChain {
                    +At(userId)
                    +PlainText(resultString)
                }
            }
        }
    }

    /**
     * 匹配lp
     *
     * @param lpName lp名称
     * @param groupKeyword 群关键词列表
     *
     */
    private fun matchLp(lpName: String, groupKeyword: Map<String, String>): MutableMap<String, Float> {
        val matchString = StringSimilarity()
        val matchResult = mutableMapOf<String, Float>()
        groupKeyword.forEach { (name, keys) ->
            val keyList = keys.split('|')
            keyList.forEach {
                val similarity = matchString.compareString(it, lpName)
                if (similarity > 0.5) {
                    println("$it, $lpName 相似度：$similarity")
                    matchResult[name] = similarity
                }
            }
        }
        return matchResult
    }


}

class StringSimilarity {
    private fun min(vararg `is`: Int): Int {
        var min = Int.MAX_VALUE
        for (i in `is`) {
            if (min > i) {
                min = i
            }
        }
        return min
    }

    fun compareString(str1: String, str2: String): Float {

        val len1 = str1.length
        val len2 = str2.length
        val dif = Array(len1 + 1) { IntArray(len2 + 1) }
        for (a in 0..len1) {
            dif[a][0] = a
        }
        for (a in 0..len2) {
            dif[0][a] = a
        }
        var temp: Int
        for (i in 1..len1) {
            for (j in 1..len2) {
                temp = if (str1[i - 1] == str2[j - 1]) {
                    0
                } else {
                    1
                }
                dif[i][j] = min(
                    dif[i - 1][j - 1] + temp, dif[i][j - 1] + 1,
                    dif[i - 1][j] + 1
                )
            }
        }
        return 1 - dif[len1][len2].toFloat() / str1.length.coerceAtLeast(str2.length)
    }
}
class MapSort{
    fun mapSortByValue(toSortMap: Map<String, Int>): Map<String, String> {

        //自定义比较器
        val valCmp: Comparator<Map.Entry<String?, Int>> =
            Comparator<Map.Entry<String?, Int>> { o1, o2 ->
                // TODO Auto-generated method stub
                o2.value - o1.value
            }
        val list: List<Map.Entry<String, Int>> = ArrayList(toSortMap.entries) //传入maps实体
        Collections.sort(list, valCmp)
        val sortedMap = mutableMapOf<String, String>()
        for (i in list.indices) {
            sortedMap[list[i].key] = list[i].value.toString()
        }
        return sortedMap.toMap()
    }
}