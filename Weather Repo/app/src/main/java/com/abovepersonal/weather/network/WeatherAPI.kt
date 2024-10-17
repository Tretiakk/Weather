package com.abovepersonal.weather.network

import android.content.Context
import androidx.core.content.ContextCompat
import com.abovepersonal.weather.BuildConfig
import com.abovepersonal.weather.R
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class WeatherAPI {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.openweathermap.org/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val weatherService = retrofit.create(WeatherService::class.java)


    // lat 49.8061
    // lon 24.8964
    suspend fun makeRequestWeather(lat: Double, lon: Double): Response<WeatherDataResponse> {
        return weatherService.getWeather(
            lat, lon,
            listOf("minutely","alerts").joinToString(","),
            BuildConfig.API_KEY
        )
    }

    fun convertToWeatherData(context: Context, weatherResponse: WeatherDataResponse): WeatherData {
        val curDay = convertToDayOfWeek(context, weatherResponse.current.dt)
        val curWeather = convertToWeather(context, weatherResponse.current.weather[0].id)
        val curTemp = convertToCelsius(weatherResponse.current.temp)
        val curTempFeelsLike = convertToCelsius(weatherResponse.current.feels_like)
        val curHumidity = weatherResponse.current.humidity
        val sunset = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(weatherResponse.current.sunset),
            ZoneId.systemDefault()
        )
        val sunrise = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(weatherResponse.current.sunrise),
            ZoneId.systemDefault()
        )
        val curHourlyInfo = ArrayList<WeatherHourlyInfo>()
        val curWeekInfo = ArrayList<WeatherDayOfWeekInfo>()

        for (i in 0..24){
            curHourlyInfo.add(convertToHourData(context, weatherResponse.hourly[i]))
        }

        for (i in 0..7){
            curWeekInfo.add(convertToDayOfWeekData(context, weatherResponse.daily[i]))
        }

        val maxTemp = curHourlyInfo.maxByOrNull { it.temp }!!.temp
        val minTemp = curHourlyInfo.minByOrNull { it.temp }!!.temp

        return WeatherData(
            curDay,
            curWeather,
            curTemp.toInt(),
            curTempFeelsLike.toInt(),
            maxTemp,
            minTemp,
            curHumidity,
            sunrise,
            sunset,
            curHourlyInfo,
            curWeekInfo
        )
    }

    private fun convertToHourData(context: Context, hourly: Hourly): WeatherHourlyInfo {
        val hour = convertToHourOfDay(hourly.dt)
        val minutes = convertToMinutesOfDay(hourly.dt)
        val weather = convertToWeather(context, hourly.weather[0].id)
        val temp = convertToCelsius(hourly.temp)
        val humidity = hourly.humidity

        return WeatherHourlyInfo(
            hour,
            minutes,
            weather,
            temp.toInt(),
            humidity
        )
    }

    private fun convertToDayOfWeekData(context: Context, daily: Daily): WeatherDayOfWeekInfo {
        val day = convertToDayOfWeek(context, daily.dt)
        val weather = convertToWeather(context, daily.weather[0].id)
        val tempDay = convertToCelsius(daily.temp.day)
        val tempNight = convertToCelsius(daily.temp.night)
        val humidity = daily.humidity

        return WeatherDayOfWeekInfo(
            day,
            weather,
            tempDay.toInt(),
            tempNight.toInt(),
            humidity
        )
    }

    private fun convertToCelsius(kelvin: Double): Float {
        return (kelvin - 273.15f).toFloat()
    }

    private fun convertToWeather(context: Context, weatherCode: Int): String {

        if (weatherCode >= 100){
            val firstChar = weatherCode.toString().get(0).digitToInt()
            val secondChar = weatherCode.toString().get(1).digitToInt()
            val thirdChar = weatherCode.toString().get(2).digitToInt()

            when(firstChar){
                2 -> {
                    // thunder
                    return if (secondChar in listOf(0, 2, 3)){
                        //rainy
                        ContextCompat.getString(context, R.string.thunderstorm)
                    } else {
                        // only thunder
                        ContextCompat.getString(context, R.string.thunderstorm_no_rain)
                    }
                }

                3 -> {
                    // drizzle
                    return ContextCompat.getString(context, R.string.rain)
                }

                5 -> {
                    // rainy
                    return if (secondChar in listOf(1, 2, 3)){
                        // snow
                        ContextCompat.getString(context, R.string.snowy)
                    } else {
                        // rain
                        ContextCompat.getString(context, R.string.rain)
                    }
                }

                6 -> {
                    // snow
                    return ContextCompat.getString(context, R.string.snowy)
                }

                8 -> {
                    // clear sky
                    return if (thirdChar == 0)
                    {
                        ContextCompat.getString(context, R.string.sunny)
                    }
                    else if (thirdChar in listOf(1, 2))
                    {
                        ContextCompat.getString(context, R.string.sunny_cloudy)
                    } else
                    {
                        ContextCompat.getString(context, R.string.cloudy)
                    }
                }
            }

            return ContextCompat.getString(context, R.string.sunny)

        } else throw IllegalArgumentException()
    }

    private fun convertToHourOfDay(epochSecond: Long): Int{
        val localDateTime = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(epochSecond),
            ZoneId.systemDefault()
        )

        return localDateTime.hour
    }

    private fun convertToMinutesOfDay(epochSecond: Long): Int{
        val localDateTime = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(epochSecond),
            ZoneId.systemDefault()
        )

        return localDateTime.minute
    }

    private fun convertToDayOfWeek(context: Context, epochSecond: Long): String{
        val localDateTime = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(epochSecond),
            ZoneId.systemDefault()
        )

        return when (localDateTime.dayOfWeek){
            DayOfWeek.MONDAY -> ContextCompat.getString(context, R.string.monday)
            DayOfWeek.TUESDAY -> ContextCompat.getString(context, R.string.tuesday)
            DayOfWeek.WEDNESDAY -> ContextCompat.getString(context, R.string.wednesday)
            DayOfWeek.THURSDAY -> ContextCompat.getString(context, R.string.thursday)
            DayOfWeek.FRIDAY -> ContextCompat.getString(context, R.string.friday)
            DayOfWeek.SATURDAY -> ContextCompat.getString(context, R.string.saturday)
            DayOfWeek.SUNDAY -> ContextCompat.getString(context, R.string.sunday)
        }
    }

    interface WeatherService {
        @GET("data/3.0/onecall")
        suspend fun getWeather(
            @Query("lat") lat: Double,
            @Query("lon") lon: Double,
            @Query("exclude") exclude: String,
            @Query("appid") apiKey: String
        ): Response<WeatherDataResponse>
    }
}