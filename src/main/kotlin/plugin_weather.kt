package me.swe.main

import okhttp3.*
import java.io.IOException

const val cityLookupUri = "https://geoapi.qweather.com/v2/city/lookup?"
const val cityWeather3DayUri = "https://devapi.qweather.com/v7/weather/3d?"
const val cityWeatherNowUri = "https://devapi.qweather.com/v7/weather/now?"


val client = OkHttpClient()

fun userBindCity(userId: Long, paraList: List<String>): String{
    val cityResult = cityLookup(paraList)
    println(cityResult)
    if (cityResult["code"] != "200") {
        return "查询失败！请检查输入的城市是否正确！\n绑定示例：!bl 苏州 江苏(可选)"
    }
    mocaDB.setConfig(userId, "USER", "loc_name", cityResult["name"] as String)
    mocaDB.setConfig(userId, "USER", "loc_adm", cityResult["adm1"] as String)
    mocaDB.setConfig(userId, "USER", "loc_id", cityResult["id"] as String)
    mocaDB.setConfig(userId, "USER", "loc_con", cityResult["country"] as String)
    return  "绑定成功！\n当前绑定城市：${cityResult["name"]}，${cityResult["adm1"]}，${cityResult["country"]}"
}


fun cityLookup(paraList: List<String>): MutableMap<String, String>{
    var cityLookupParams = "key=${getBotConfig("QWEATHER_KEY")}"
    paraList.let {
        cityLookupParams += when (it.size) {
            1 -> {
                "&location=${paraList[0]}"
            }
            2 -> {
                "&location=${paraList[0]}&adm=${paraList[1]}"
            }
            else -> {
                return mutableMapOf("code" to "400")
            }
        }
    }
    val wpCityLookupUri = cityLookupUri + cityLookupParams
    val reqBuilder = Request.Builder()
    val returnMap: MutableMap<String, String> = mutableMapOf()

    reqBuilder.url(wpCityLookupUri)
    reqBuilder.get()
    val call = client.newCall(reqBuilder.build())
    try {
        val response = call.execute().body!!.string()
        val jsonMap = jsonStringToMap(response)
        returnMap["code"] = jsonMap["code"].toString()
        if (jsonMap["code"].toString() == "200") {
            val locations = jsonMap["location"] as List<*>
            val targetLocation = locations.first() as Map<*, *>
            for ((k, v) in targetLocation) {
                returnMap[k.toString()] = v.toString()
            }
        }
    }

    catch (e: IOException) {
        e.printStackTrace()
    }
    return returnMap
}

/**
 * 返回天气
 */
fun weatherLookup(cityId: String, whichDayIn3Days: Int = 1): WeatherData {
    val wData = WeatherData()

    if (cityId == "") { wData.code = "400" ;return wData }
    val cityWeatherParams = "key=${getBotConfig("QWEATHER_KEY")}&location=${cityId}"

    val wpCity3DWeatherUri = cityWeather3DayUri + cityWeatherParams
    val wpCityNowWeatherUri = cityWeatherNowUri + cityWeatherParams

    val reqBuilder = Request.Builder()
    reqBuilder.url(wpCity3DWeatherUri)
    reqBuilder.get()
    val we3DCall = client.newCall(reqBuilder.build())
    try {
        val response = we3DCall.execute().body!!.string()
        val jsonMap = jsonStringToMap(response)
        wData.code = jsonMap["code"].toString()
        if (jsonMap["code"].toString() == "200") {
            val weather3D = jsonMap["daily"] as List<*>
            val dayIndex = whichDayIn3Days - 1
            val dayWeather = weather3D[dayIndex] as Map<*, *>
            wData.apply {
                fxDate = dayWeather["fxDate"].toString()
                tempMin = dayWeather["tempMin"].toString()
                tempMax = dayWeather["tempMax"].toString()
                humidity = dayWeather["humidity"].toString()
                precipDay = dayWeather["precip"].toString()

                textDay = dayWeather["textDay"].toString()
                textNight = dayWeather["textNight"].toString()

                windDirDay = dayWeather["windDirDay"].toString()
                windDirNight = dayWeather["windDirNight"].toString()
                windScaleDay = dayWeather["windScaleDay"].toString()
                windScaleNight = dayWeather["windScaleNight"].toString()
            }
        }
    }
    catch (e: IOException) {
        e.printStackTrace()
        wData.code = "501"
        return wData
    }

    reqBuilder.url(wpCityNowWeatherUri)
    reqBuilder.get()
    val weNowCall = client.newCall(reqBuilder.build())
    try {
        val response = weNowCall.execute().body!!.string()
        val jsonMap = jsonStringToMap(response)
        wData.code = jsonMap["code"].toString()
        if (jsonMap["code"].toString() == "200") {
            val weatherNow = jsonMap["now"] as Map<*, *>
            wData.apply {
                obsTime = weatherNow["obsTime"].toString()
                textNow = weatherNow["text"].toString()
                tempNow = weatherNow["temp"].toString()
                tempFeelsLike = weatherNow["feelsLike"].toString()
                windDirNow = weatherNow["windDir"].toString()
                windScaleNow = weatherNow["windScale"].toString()
            }

        }
    }
    catch (e: IOException) {
        e.printStackTrace()
        wData.code = "502"
        return wData
    }
    return wData
}


class WeatherData{
    /**
     * 响应状态码(200 OK)
     */
    var code = ""

    /**
     * 预报日期
     */
    var fxDate = ""

    /**
     * 数据观测时间
     */
    var obsTime = ""

    /**
     * 温度，默认单位：摄氏度
     */
    var tempNow = ""

    /**
     * 预报当天最低温度
     */
    var tempMin = ""

    /**
     * 预报当天最高温度
     */
    var tempMax = ""

    /**
     * 体感温度，默认单位：摄氏度
     */
    var tempFeelsLike = ""

    /**
     * 相对湿度，百分比数值
     */
    var humidity = ""

    /**
     * 天气状况的文字描述，包括阴晴雨雪等天气状态的描述
     */
    var textNow = ""

    /**
     * 预报白天天气状况文字描述，包括阴晴雨雪等天气状态的描述
     */
    var textDay = ""

    /**
     * 预报夜间天气状况文字描述，包括阴晴雨雪等天气状态的描述
     */
    var textNight = ""

    /**
     * 预报白天风向
     */
    var windDirDay = ""

    /**
     * 预报白天风力等级
     */
    var windScaleDay = ""

    /**
     * 预报夜间当天风向
     */
    var windDirNight = ""

    /**
     * 预报夜间风力等级
     */
    var windScaleNight = ""

    /**
     * 风向
     */
    var windDirNow = ""

    /**
     * 风力等级
     */
    var windScaleNow = ""

    /**
     * 预报当天总降水量，默认单位：毫米
     */
    var precipDay = ""
}