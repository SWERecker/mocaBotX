package me.swe.main

import com.mongodb.BasicDBObject
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Projections
import net.mamoe.mirai.utils.MiraiLogger
import org.bson.Document
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

class MocaDatabase {
    private val mongoClient: MongoClient = MongoClients.create()
    private val db: MongoDatabase = mongoClient.getDatabase("moca")
    private val colGroupKeyword: MongoCollection<Document> = db.getCollection("group_keyword")
    private val colGroupConfig: MongoCollection<Document> = db.getCollection("group_config")
    private val mapGroupKeywords = mutableMapOf<Long, Map<String, String>>()
    private val mapGroupConfig = mutableMapOf<Long, Any>()
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
    private fun loadAllGroupKeyword(){
        val queryResult = colGroupKeyword.find()
            .projection(Projections.excludeId())
        for(groupMap in queryResult){
            loadKeywordToCache(groupMap)
        }
    }

    /**
     * 从数据库中加载所有群组参数.
     */
    private fun loadAllGroupConfig(){
        val queryResult = colGroupConfig.find()
            .projection(Projections.excludeId())
        for(groupMap in queryResult){
            loadConfigToCache(groupMap)
        }
    }
    /**
     * 从缓存中获取某群组的关键词列表.
     *
     * @param gid 群号
     *
     * @return 群组的关键词列表 Map<String, String>
     *
     */
    private fun getGroupKeyword(gid: Long): Map<String, String> {
        if(gid !in mapGroupKeywords.keys){
            initGroup(gid)
        }
        return mapGroupKeywords[gid] as Map<String, String>
    }

    /**
     * 新加入某个群时，初始化该群的关键词列表和参数.
     *
     * @param gid 群号
     *
     * @return
     *
     */
    fun initGroup(gid: Long) {
        val query = Document()
        var keyInited = false
        var configInited = false
        val groupId = gid.toInt()
        query["group"] = groupId

        // Check if the group keyword and config exists.
        val queryKeyCheckResult = colGroupKeyword.find(query).first()
        if(!queryKeyCheckResult.isNullOrEmpty()){
            mocaDBLogger.info("Group $groupId keyword exist, skip init keyword")
            keyInited = true
        }

        val queryConfigCheckResult = colGroupConfig.find(query).first()
        if(!queryConfigCheckResult.isNullOrEmpty()){
            mocaDBLogger.info("Group $groupId config exist, skip init config")
            configInited = true
        }

        if(!keyInited) {
            mocaDBLogger.info("========Init group $groupId keyword========")
            query["group"] = "key_template"
            val queryResult = colGroupKeyword.find(query).first()
            if (queryResult.isNullOrEmpty()) {
                mocaDBLogger.error("[ERROR] Template keyword not found.")
                return
            }
            val templateGroupKeyword = queryResult["keyword"]
            val toInsertDocument = Document("group", groupId)
                .append("keyword", templateGroupKeyword)
            val insertResult = colGroupKeyword.insertOne(toInsertDocument)
            if (insertResult.wasAcknowledged()) {
                mocaDBLogger.info("Inserted, id=" + insertResult.insertedId)
                keyInited = true
            }
        }

        if(!configInited){
            mocaDBLogger.info("========Init group $groupId config========")
            query["group"] = "config_template"
            val queryResult = colGroupConfig.find(query)
                .projection(Projections.excludeId())
                .first()
            if (queryResult.isNullOrEmpty()) {
                mocaDBLogger.error("[ERROR] Template config not found.")
                return
            }
            queryResult["group"] = groupId
            val insertResult = colGroupConfig.insertOne(queryResult)
            if (insertResult.wasAcknowledged()) {
                mocaDBLogger.info("Inserted, id=" + insertResult.insertedId)
                configInited = true
            }
        }
        if (keyInited && configInited){
            mocaDBLogger.info("Successfully initialized group $groupId")
            loadGroupKeyword(gid)
            loadGroupConfig(gid)
        }
    }

    /**
     * 将关键词列表保存至缓存和数据库.
     *
     * @param gid 群号
     * @param newGroupKeyword: 要存储的groupKeyword
     *
     * @return
     *
     */
    fun saveGroupKeyword(gid: Long, newGroupKeyword: Map<String, String>) {
        val query = Document()
            .append("group", gid)
        val operationDocument = Document("${'$'}set", Document("keyword", newGroupKeyword))
        val saveResult = colGroupKeyword.updateOne(query, operationDocument)
        if(saveResult.modifiedCount > 0){
            mocaDBLogger.info("[$gid] Reloading groupKeyword ")
            loadGroupKeyword(gid)
        }
    }

    /**
     * 从数据库中获取某群组的关键词列表.
     *
     * @param gid 群号
     *
     * @return
     *
     */
    private fun loadGroupKeyword(gid: Long) {
        val query = Document()
            .append("group", gid)
        val queryResult = colGroupKeyword.find(query)
            .projection(Projections.excludeId())
            .first()
        if (!queryResult.isNullOrEmpty()){
            loadKeywordToCache(queryResult)
        }
    }

    /**
     * 从数据库中获取某群组的参数.
     *
     * @param gid 群号
     *
     * @return
     *
     */
    private fun loadGroupConfig(gid: Long) {
        val query = Document()
            .append("group", gid)
        val queryResult = colGroupKeyword.find(query)
            .projection(Projections.excludeId())
            .first()
        if (!queryResult.isNullOrEmpty()){
            loadConfigToCache(queryResult)
        }
    }
    /**
     * 从数据库中获取相应参数.
     *
     * @param gid 群号
     * @param arg 参数名称
     *
     * @return 参数值
     *
     */
    fun getGroupConfig(gid: Long, arg: String): Any? {
        if(gid !in mapGroupConfig.keys){
            initGroup(gid)
        }
        val groupConfig = mapGroupConfig[gid] as Map<*, *>
        return groupConfig[arg]
    }

    /**
     * 匹配关键词，同时返回是否含双倍关键词
     *
     * @param gid 群组的关键词
     * @param key 用户消息内容
     *
     * @return Pair(name, isDouble)
     *
     */
    fun matchKey(gid: Long, key: String): Pair<String, Boolean> {
        var name = ""
        var isDouble = false
        val groupKeyword = getGroupKeyword(gid)
        for((k, v) in groupKeyword){
            if(Regex(v).find(key) != null){
                name = k
                break
            }
        }
        if(key.startsWith("多")) {
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
    private fun loadKeywordToCache(toLoadMap: Map<*, *>){
        try {
            val groupId = toLoadMap["group"] as Int
            val groupKeyword = toLoadMap["keyword"] as Document
            val tempKeyword = mutableMapOf<String, String>()
            groupKeyword.forEach{
                tempKeyword[it.key.toString()] = it.value.toString()
            }
            mapGroupKeywords[groupId.toLong()] = tempKeyword
        }catch (e: ClassCastException){
            mocaDBLogger.error("Kotlin Error group: ${toLoadMap["group"]}")
        }catch (e: java.lang.ClassCastException){
            mocaDBLogger.error("Java Error in group: ${toLoadMap["group"]}")
        }
    }

    /**
     * 将configMap存储进mapGroupConfig的具体实现.
     *
     * @param toLoadDocument 从数据库中取出的数据.
     *
     */
    private fun loadConfigToCache(toLoadDocument: Document){
        try {
            val groupId = toLoadDocument["group"] as Int
            toLoadDocument.remove("group")
            mapGroupConfig[groupId.toLong()] = toLoadDocument.toMap()
        }catch (e: ClassCastException){
            mocaDBLogger.error("Kotlin Error group: ${toLoadDocument["group"]}")
        }catch (e: java.lang.ClassCastException){
            mocaDBLogger.error("Java Error in group: ${toLoadDocument["group"]}")
        }
    }
}