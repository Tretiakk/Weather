package com.abovepersonal.weather.network

import java.time.LocalDateTime

data class WeatherData(
    val curDay: String,
    val curWeather: String,
    val curTemp: Int,
    val curTempFeelsLike: Int,
    val tempMax: Int,
    val tempMin: Int,
    val curHumidity: Int,
    val sunrise: LocalDateTime,
    val sunset: LocalDateTime,
    val dayHourlyInfo: List<WeatherHourlyInfo>,
    val weekInfo: List<WeatherDayOfWeekInfo>
)

data class WeatherHourlyInfo(
    val hour: Int,
    val minutes: Int,
    val weather: String,
    val temp: Int,
    val humidity: Int
)

data class WeatherDayOfWeekInfo(
    val day: String,
    val weather: String,
    val tempDay: Int,
    val tempNight: Int,
    val humidity: Int
)


