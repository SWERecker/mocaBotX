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
fun weatherLookup(cityId: String): WeatherData {
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
            val todayWeather = weather3D.first() as Map<*, *>
            wData.fxDate = todayWeather["fxDate"].toString()
            wData.tempMin = todayWeather["tempMin"].toString()
            wData.tempMax = todayWeather["tempMax"].toString()
            wData.humidity = todayWeather["humidity"].toString()
            wData.textDay = todayWeather["textDay"].toString()
            wData.textNight = todayWeather["textNight"].toString()
            wData.windDirDay = todayWeather["windDirDay"].toString()
            wData.windDirNight = todayWeather["windDirNight"].toString()
            wData.windScaleDay = todayWeather["windScaleDay"].toString()
            wData.windScaleNight = todayWeather["windScaleNight"].toString()
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
            wData.obsTime = weatherNow["obsTime"].toString()
            wData.textNow = weatherNow["text"].toString()
            wData.tempNow = weatherNow["temp"].toString()
            wData.tempFeelsLike = weatherNow["feelsLike"].toString()
            wData.windDirNow = weatherNow["windDir"].toString()
            wData.windScaleNow = weatherNow["windScale"].toString()
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
    var code = ""
    var fxDate = ""
    var obsTime = ""

    var tempNow = ""
    var tempMin = ""
    var tempMax = ""
    var tempFeelsLike = ""
    var humidity = ""

    var textNow = ""
    var textDay = ""
    var textNight = ""

    var windDirDay = ""
    var windScaleDay = ""
    var windDirNight = ""
    var windScaleNight = ""
    var windDirNow = ""
    var windScaleNow = ""

}