package me.swe.main

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Projections
import net.mamoe.mirai.utils.MiraiLogger
import org.bson.Document
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.io.File
import java.io.InputStream
import java.lang.NumberFormatException


val mapGroupKeywords = mutableMapOf<Long, Map<String, String>>()
val mapGroupConfig = mutableMapOf<Long, Map<String, Any>>()
val mapMocaCd = mutableMapOf<String, Long>()


val mongoClient: MongoClient = MongoClients.create()
val db: MongoDatabase = mongoClient.getDatabase("moca")
val colGroupKeyword: MongoCollection<Document> = db.getCollection("group_keyword")
val colGroupConfig: MongoCollection<Document> = db.getCollection("group_config")
val colGroupCount: MongoCollection<Document> = db.getCollection("group_count")
val colUserConfig: MongoCollection<Document> = db.getCollection("user_config")
val mocaDBLogger = MiraiLogger.create("MocaDBLogger")
val redisPool = JedisPool(JedisPoolConfig())


class MocaDatabase {

    /**
     * 从数据库中加载所有群组的关键词列表.
     */
    fun loadAllGroupKeyword() {
        val queryResult = colGroupKeyword.find()
            .projection(
                Projections.fields(
                    Projections.excludeId(),
                    Projections.include("group")
                )
            )
        for (groupMap in queryResult) {
            loadGroupKeyword(groupMap["group"].toString())
        }
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
            mocaDBLogger.info("Set $setType $userId $arg => $value")
            if (setType == "GROUP") {
                mocaDBLogger.info("Load $userId config to cache")
                loadGroupConfig(userId.toString())
            }
        } else {
            mocaDBLogger.info("DB not modified")
        }
        return saveResult.modifiedCount > 0
    }

    /**
     * 新加入某个群时，初始化该群的关键词列表和参数.
     *
     * @param groupId 群号
     *
     * @return
     *
     */
    fun dbInitGroup(groupId: Long) {
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
            loadGroupKeyword(groupId.toString())
            loadGroupConfig(groupId.toString())
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
            loadGroupKeyword(groupId.toString())
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
     * 增加统计次数.
     *
     * @param groupId 群号
     * @param name 要update的名称
     * @param delta 增加的次数（默认为1）
     *
     * @return
     *
     */
    fun updateGroupCount(groupId: Long, name: String, delta: Int = 1): Boolean {
        val query = Document()
            .append("group", groupId)
        val queryResult = colGroupCount.find(query)
            .projection(Projections.fields(Projections.excludeId(), Projections.include(name)))
            .first()
        println(queryResult)
        if (queryResult == null){
            mocaDBLogger.info("[$groupId] Initing empty count.")
            val insertResult = colGroupCount.insertOne(query)
            if (insertResult.wasAcknowledged()){
                mocaDBLogger.info("[$groupId] Inited empty count.")
            }
        }
            val operationDocument = Document("${'$'}inc", Document(name, delta))
            val operationResult = colGroupCount.updateOne(query, operationDocument)
            if (operationResult.modifiedCount > 0) {
                mocaDBLogger.info("[$groupId] $name += 1")
            return true
        }
        return false
    }

    /**
     * 从数据库中获取某群组的参数.
     *
     * @param groupIdString 群号 in String
     *
     * @return
     *
     */
    private fun loadGroupConfig(groupIdString: String) {
        if (groupIdString == "config_template") {
            mocaDBLogger.info("loadGroupConfig: Skip $groupIdString")
            return
        }
        val groupId = groupIdString.toLong()
        val query = Document()
            .append("group", groupId)
        val queryResult = colGroupConfig.find(query)
            .projection(Projections.excludeId())
            .first()
        if (!queryResult.isNullOrEmpty()) {
            try {
                queryResult.remove("group")
                mapGroupConfig[groupId] = queryResult.toMap()
            } catch (e: ClassCastException) {
                mocaDBLogger.error("ClassCastException in group: $groupId")
            } catch (e: java.lang.ClassCastException) {
                mocaDBLogger.error("ClassCastException in group: $groupId")
            } catch (e: NumberFormatException) {
                mocaDBLogger.error("NumberFormatException in group: $groupId")
            }

        }
    }

    /**
     * 从数据库中加载所有群组参数.
     */
    fun loadAllGroupConfig() {
        val queryResult = colGroupConfig.find()
            .projection(
                Projections.fields(
                    Projections.excludeId(),
                    Projections.include("group")
                )
            )
        for (groupMap in queryResult) {
            loadGroupConfig(groupMap["group"].toString())
        }
    }

    /**
     * 从数据库中获取某群组的关键词列表.
     *
     * @param groupIdString 群号 in String
     *
     * @return
     *
     */
    private fun loadGroupKeyword(groupIdString: String) {
        if (groupIdString == "key_template") {
            mocaDBLogger.info("loadGroupKeyword: Skip $groupIdString")
            return
        }
        val groupIdLong = groupIdString.toLong()
        val query = Document()
            .append("group", groupIdLong)
        val queryResult = colGroupKeyword.find(query)
            .projection(Projections.excludeId())
            .first()
        if (!queryResult.isNullOrEmpty()) {
            try {
                val groupKeyword = queryResult["keyword"] as Document
                val tempKeyword = mutableMapOf<String, String>()
                groupKeyword.forEach {
                    tempKeyword[it.key.toString()] = it.value.toString()
                }
                mapGroupKeywords[groupIdLong] = tempKeyword
            } catch (e: ClassCastException) {
                mocaDBLogger.error("ClassCastException in group: ${queryResult["group"]}")
            } catch (e: java.lang.ClassCastException) {
                mocaDBLogger.error("ClassCastException in group: ${queryResult["group"]}")
            } catch (e: NumberFormatException) {
                mocaDBLogger.error("NumberFormatException in group: ${queryResult["group"]}")
            }
        }
    }
}

/**
 * 从index.txt加载所有图片路径至Redis数据库
 */
fun loadIndexFile(): Int {
    redisPool.resource.use { r ->
        r.select(3)
        r.flushDB()
        val inStream: InputStream = File(indexFilePath).inputStream()
        inStream.bufferedReader().useLines { lines ->
            lines.forEach {
                val line = it.split('|')
                val filePath = picturePath + line[0]
                val categories = line[1].split(' ')
                categories.forEach { category ->
                    r.sadd(category, filePath)
                }
            }
        }
        var peopleCount = 0
        r.keys("*").forEach { _ ->
            peopleCount++
        }
        return peopleCount
    }
}