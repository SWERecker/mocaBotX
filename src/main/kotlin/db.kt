package me.swe.main

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Projections
import net.mamoe.mirai.utils.MiraiLogger
import org.bson.Document


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
     * @param groupId 群号
     *
     * @return 群组的关键词列表 Map<String, String>
     *
     */
    private fun getGroupKeyword(groupId: Long): Map<String, String> {
        if(groupId !in mapGroupKeywords.keys){
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
        if(!queryKeyCheckResult.isNullOrEmpty()){
            mocaDBLogger.info("Group $groupIdInt keyword exist, skip init keyword")
            keyInited = true
        }

        val queryConfigCheckResult = colGroupConfig.find(query).first()
        if(!queryConfigCheckResult.isNullOrEmpty()){
            mocaDBLogger.info("Group $groupIdInt config exist, skip init config")
            configInited = true
        }

        if(!keyInited) {
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

        if(!configInited){
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
        if (keyInited && configInited){
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
        if(saveResult.modifiedCount > 0){
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
        if (!queryResult.isNullOrEmpty()){
            loadKeywordToCache(queryResult)
        }
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
     * @param groupId 群号
     * @param arg 参数名称
     *
     * @return 参数值
     *
     */
    fun getGroupConfig(groupId: Long, arg: String): Any? {
        if(groupId !in mapGroupConfig.keys){
            initGroup(groupId)
        }
        val groupConfig = mapGroupConfig[groupId] as Map<*, *>
        return groupConfig[arg]
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