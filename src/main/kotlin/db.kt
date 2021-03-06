package me.swe.main

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Projections
import com.mongodb.client.model.UpdateOptions
import org.bson.Document
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.io.File
import java.io.InputStream
import java.lang.NumberFormatException


val mapGroupKeywords = mutableMapOf<Long, Map<String, String>>()
val mapGroupConfig = mutableMapOf<Long, Map<String, Any>>()
val mapMocaCd = mutableMapOf<String, Long>()
var mapGroupRepeater = mutableMapOf<Long, MutableMap<Int, String>>()
var mapGroupFrequencyLimiter = mutableMapOf<Long, Int>()
var resetGroupFrequencyLimiterTime: Long = 0L

val mongoClient: MongoClient = MongoClients.create()
val db: MongoDatabase = mongoClient.getDatabase("moca")
val colGroupKeyword: MongoCollection<Document> = db.getCollection("group_keyword")
val colGroupConfig: MongoCollection<Document> = db.getCollection("group_config")
val colGroupCount: MongoCollection<Document> = db.getCollection("group_count")
val colUserConfig: MongoCollection<Document> = db.getCollection("user_config")
val colGroupPic: MongoCollection<Document> = db.getCollection("group_pic")
val colMocaLog: MongoCollection<Document> = db.getCollection("moca_log")

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
            loadGroupKeyword(groupMap["group"].toString().toLong())

        }
    }

    /**
     * 从数据库中获取相应用户参数.
     *
     * @param userId 群号
     * @param arg 参数名称
     *
     * @return 参数值/"NOT_FOUND"
     *
     */
    fun getUserConfig(userId: Long, arg: String): String {
        val query = Document()
            .append("qq", userId)
        val queryResult = colUserConfig.find(query)
            .projection(Projections.fields(Projections.excludeId(), Projections.include(arg)))
            .first()
        if (!queryResult.isNullOrEmpty()) {
            return queryResult[arg].toString()
        }
        return "NOT_FOUND"
    }

    /**
     * 获取用户参数，以Int返回，不存在为0，存在为参数值
     *
     * @param userId 用户QQ号
     * @param arg 参数名称
     *
     * @return 面包数量(Int)
     */
    fun getUserConfigInt(userId: Long, arg: String): Int {
        return getUserConfig(userId, arg).let {
            if (it.isNotFound()) {
                0
            }else{
                it.toInt()
            }
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
        val enableUpsert = UpdateOptions().upsert(true)
        val saveResult = if (setType == "GROUP") {
            colGroupConfig.updateOne(query, operationDocument, enableUpsert)
        } else {
            colUserConfig.updateOne(query, operationDocument, enableUpsert)
        }
        if (saveResult.upsertedId != null || saveResult.modifiedCount > 0) {
            mocaDBLogger.info("OK. Set $setType $userId $arg => $value")
            mocaLog("DbSetConfig", targetId = userId, description = "type = $setType, arg = $arg, value = $value")
            if (setType == "GROUP") {
                mocaDBLogger.info("Load $userId config to cache")
                loadGroupConfig(userId)
            }
        } else {
            mocaDBLogger.info("Unmodified, trying to Set $setType $userId $arg => $value")
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
            query["group"] = 0
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
                mocaLog("GroupInitKeyword", groupId = groupId)
                mocaDBLogger.info("Inserted, id=" + insertResult.insertedId)
                keyInited = true
            }
        }

        if (!configInited) {
            mocaDBLogger.info("========Init group $groupIdInt config========")
            query["group"] = 0
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
                mocaLog("GroupInitConfig", groupId = groupId)
                mocaDBLogger.info("Inserted, id=" + insertResult.insertedId)
                configInited = true
            }
        }
        if (keyInited && configInited) {
            mocaLog("GroupInit", groupId = groupId, description = "success")
            mocaDBLogger.info("Successfully initialized group $groupIdInt")
            loadGroupKeyword(groupId)
            loadGroupConfig(groupId)
        }
    }


    /**
     * 将关键词列表保存至缓存和数据库.
     *
     * @param groupId 群号
     * @param newGroupKeyword 要存储的groupKeyword
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
     * 将群关键词保存至数据库.
     *
     * @param groupId 群号
     * @param newGroupPicKeywords 要存储的群关键词
     *
     * @return
     *
     */
    fun saveGroupPicKeywords(groupId: Long, newGroupPicKeywords: String) {
        val query = Document()
            .append("group", groupId)
        val operationDocument = Document("${'$'}set", Document("key", newGroupPicKeywords))
        val saveResult = colGroupPic.updateOne(query, operationDocument)
        if (saveResult.modifiedCount > 0) {
            mocaDBLogger.info("[$groupId] GroupPicDb modified count=${saveResult.modifiedCount}")
        }else {
            query.append("key", newGroupPicKeywords)
            val insertResult = colGroupPic.insertOne(query)
            mocaDBLogger.info("[$groupId] GroupPicDb inserted: ${insertResult.insertedId}")
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
                mocaLog("GroupCountUpdate", groupId = groupId, description = name)
                mocaDBLogger.info("$groupId: $name += 1")
            return true
        }
        return false
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
            loadGroupConfig(groupMap["group"].toString().toLong())
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
            try {
                val groupKeyword = queryResult["keyword"] as Document
                val tempKeyword = mutableMapOf<String, String>()
                groupKeyword.forEach {
                    tempKeyword[it.key.toString()] = it.value.toString()
                }
                mapGroupKeywords[groupId] = tempKeyword
            } catch (e: ClassCastException) {
                mocaDBLogger.error("ClassCastException in group: ${queryResult["group"]}")
            } catch (e: java.lang.ClassCastException) {
                mocaDBLogger.error("ClassCastException in group: ${queryResult["group"]}")
            } catch (e: NumberFormatException) {
                mocaDBLogger.error("NumberFormatException in group: ${queryResult["group"]}")
            }
        }
    }

    fun getGroupPicKeywords(groupId: Long): String{
        val query = Document()
            .append("group", groupId)
        val queryResult = colGroupPic.find(query)
            .projection(
                Projections.fields(Projections.excludeId(),
                Projections.include("key"))
            )
            .first()
        if (!queryResult.isNullOrEmpty()){
            return queryResult["key"].toString()
        }
        return ""
    }

    fun getCurrentPicCount(groupId: Long): Int{
        getCurrentPics(groupId).size.also {
            mocaDBLogger.info("Current pic count: $it")
            return it
        }
    }

    fun getCurrentPics(groupId: Long): Document {
        val query = Document()
            .append("group", groupId)
        val queryResult = colGroupPic.find(query)
            .projection(Projections.fields(
                Projections.excludeId(),
                Projections.include("pics")))
            .first()
        if (!queryResult.isNullOrEmpty()) {
            return queryResult["pics"] as Document
        }
        return Document()
    }

    fun updateNewGroupPicture(groupId: Long, fileName: String, imgId: Int, saveTime: Long){
        val picDocument = Document()
            .append("name", fileName)
            .append("time", saveTime)
        val saveDocument = getCurrentPics(groupId)
            .append(imgId.toString(), picDocument)
        saveGroupPicture(groupId, saveDocument)
        mocaLog("GroupUpdateGroupPic", groupId = groupId,
            description = "ID = $imgId, fileName = $fileName")
    }

    fun saveGroupPicture(groupId: Long, newDocument: Document): String{
        val query = Document()
            .append("group", groupId)
        val operationDocument = Document("${'$'}set", Document("pics", newDocument))
        val saveResult = colGroupPic.updateOne(query, operationDocument)
        if (saveResult.modifiedCount > 0) {
            mocaDBLogger.info("[$groupId] updated group pic")
        } else {
            query.append("pics", newDocument)
            val insertResult = colGroupPic.insertOne(query)
            mocaDBLogger.info("[$groupId] insert new group pic ${insertResult.insertedId}")
        }
        return ""
    }

    /**
     * 换lp次数++
     *
     * @param userId 用户QQ
     *
     */
    fun updateUserChangeLpTimes(userId: Long) {
        val query = Document()
            .append("qq", userId)
        var delta = 1
        val queryResult = colUserConfig.find(query)
            .projection(Projections.fields(Projections.excludeId()))
            .first()
        if (!queryResult.isNullOrEmpty()) {
            if ("clp_time" in queryResult) {
                if (queryResult["clp_time"].toString().toInt() == -1) {
                    delta = 2
                }
            }
            val operationDocument = Document("${'$'}inc", Document("clp_time", delta))
            val updateResult = colUserConfig.updateOne(query, operationDocument)
            mocaLog("UserUpdateClpTimes", userId)
            mocaDBLogger.info("User $userId clp_time + $delta, modified count = ${updateResult.modifiedCount}")
        }
    }
    /**
     * 从index.txt加载所有图片路径至Redis数据库
     */
    fun loadIndexFile(): Long? {
        redisPool.resource.use { r ->
            r.select(3)
            r.flushDB()
            val indexFilePath = picturePath + "index.txt"
            val inStream: InputStream = File(indexFilePath).inputStream()
            inStream.bufferedReader().useLines { lines ->
                lines.forEach {
                    if(!it.startsWith("-")) {
                        val line = it.split('|')
                        val filePath = picturePath + line[0]
                        val categories = line[1].split(' ')
                        categories.forEach { category ->
                            r.sadd(category, filePath)
                        }
                    }
                }
            }
            return r.dbSize()
        }
    }

    /**
     * 今日签到人数+1
     */
    fun incUserSignInCount(): Int {
        val dateString = (System.currentTimeMillis() / 1000).toDateStr("yyyyMMdd")
        redisPool.resource.use { r ->
            r.select(4)
            return r.incr("${dateString}_signin").toString().toInt()
        }
    }

    /**
     * 记录日志
     */
    fun mocaLog(eventName: String, targetId: Long = 0L, groupId: Long = 0L, description: String = "-") {
        val logTime = System.currentTimeMillis() / 1000
        val query = Document()
            .append("time", logTime)
            .append("time_string", logTime.toDateStr())
            .append("event", eventName)
            .append("group", groupId)
            .append("target", targetId)
            .append("description", description)
        colMocaLog.insertOne(query)
    }
}