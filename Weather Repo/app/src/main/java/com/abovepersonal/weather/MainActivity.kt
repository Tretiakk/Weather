package com.abovepersonal.weather

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Animatable2.AnimationCallback
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.abovepersonal.weather.network.WeatherAPI
import com.abovepersonal.weather.network.WeatherData
import com.abovepersonal.weather.network.WeatherDataResponse
import com.abovepersonal.weather.network.WeatherDayOfWeekInfo
import com.abovepersonal.weather.network.WeatherHourlyInfo
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import java.time.LocalDateTime
import java.time.LocalTime

class MainActivity : ComponentActivity() {

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        enableEdgeToEdge()

        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // Permission granted

                getLocation(this)

                updateWeatherData(this, userLat, userLon)
            } else {
                // Handle permission denial

                showMessage(
                    text = getString(R.string.we_need_this_permission),
                    onConfirm = {
                        requestLocationPermission()
                    },
                    onCancel = {
                        Toast.makeText(this, R.string.sorry_we_cant_help_you, Toast.LENGTH_LONG).show()

                        onDestroy()
                    }
                )
            }
        }

        setContent {
            MainPreview()
        }

        realizeWeather(this, requestPermissionLauncher)
    }

    private fun realizeWeather(activity: Activity,requestPermissionLauncher: ActivityResultLauncher<String>) {
        isNetworkConnected.value = VariousUtils.isNetworkConnected(activity)

        if (isNetworkConnected.value) {
            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // if location permission is granted

                getLocation(activity)
                updateWeatherData(activity, userLat, userLon)
            } else {
                // if location permission isn't provided

                // request permission
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                ) {
                    // Show rationale if needed
                    showMessage(
                        text = activity.getString(R.string.we_need_this_permission),
                        onConfirm = {
                            requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        },
                        onCancel = {
                            Toast.makeText(
                                activity,
                                R.string.sorry_we_cant_help_you,
                                Toast.LENGTH_LONG
                            ).show()

                            activity.finish()
                        }
                    )

                } else {
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
        } else {
            Log.e("Weather update", "Error: no internet connection")
        }
    }

    private fun requestLocationPermission(){
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}

private var userLat: Double = -1.0
private var userLon: Double = -1.0

val isNetworkConnected = MutableStateFlow(true)

var weatherData = MutableStateFlow(
    WeatherData(
        "Wait",
        "Please wait, getting weather",
        0,
        0,
        0,
        0,
        0,
        LocalDateTime.now(),
        LocalDateTime.now(),
        listOf(),
        listOf()
    )
)

val isLoadingInfo = MutableStateFlow(true)
val isLoading = MutableStateFlow(false)

val poppins_bold = FontFamily(
    Font(R.font.poppins_bold)
)
val poppins_regular = FontFamily(
    Font(R.font.poppins_regular)
)

private var isMessageShowState = MutableStateFlow(false)
private var messageTextState = MutableStateFlow("")
private lateinit var onMessageConfirm: () -> Unit
private lateinit var onMessageCancel: () -> Unit

private fun showMessage(
    text: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
){
    onMessageConfirm = onConfirm
    onMessageCancel = onCancel

    messageTextState.value = text

    if (!isMessageShowState.value) isMessageShowState.value = true
}

@OptIn(DelicateCoroutinesApi::class)
private fun updateWeatherData(context: Context, lat: Double, lon: Double) {
    isNetworkConnected.value = VariousUtils.isNetworkConnected(context)

    if (isNetworkConnected.value) {
        GlobalScope.launch(Dispatchers.IO) {

            isLoadingInfo.value = true

            val weatherAPI = WeatherAPI()

            val response: Response<WeatherDataResponse> =
                weatherAPI.makeRequestWeather(lat, lon)

            weatherData.value =
                if (response.body() != null) {
                    weatherAPI.convertToWeatherData(context, response.body()!!)
                } else {
                    WeatherData(
                        "Error",
                        curWeather = "Weather fetch error",
                        0,
                        0,
                        0,
                        0,
                        0,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        listOf(),
                        listOf()
                    )
                }

            isLoadingInfo.value = false
        }
    }
}

@SuppressLint("MissingPermission")
private fun getLocation(activity: Activity){
    val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val location: Location? = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    if (location != null) {
        userLat = location.latitude
        userLon = location.longitude
    }
}

@Composable
private fun MainPreview() {
    val isMessageVisible by isMessageShowState.collectAsState()
    val isLoadingInfoState by isLoadingInfo.collectAsState()
    val isLoadingState by isLoading.collectAsState()
    val isNetworkConnectedState by isNetworkConnected.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize()
    ){
        if (isNetworkConnectedState)
        {
            WeatherPreview()

            if (isLoadingState || isLoadingInfoState) {
                LoadingPreview()

                LaunchedEffect(Unit) {
                    isLoading.value = true
                }
            }
        } else
        {
            InternetErrorPreview()
        }


        AnimatedVisibility(isMessageVisible) {
            Message(
                modifier = Modifier
            )
        }
    }
}

@Preview
@Composable
private fun WeatherPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.whiteF2B15))
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        val context = LocalContext.current
        val weatherInfo by weatherData.collectAsState()

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {

            AVDComposable(
                modifier = Modifier
                    .size(125.dp)
                    .padding(top = 30.dp),
                avd = getWeatherIcon(context, weatherInfo.curWeather, LocalTime.now())
            )

            Spacer(Modifier.width(60.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${weatherInfo.curTemp}°",
                    fontFamily = poppins_regular,
                    fontSize = 60.sp,
                    color = colorResource(R.color.black15F2)
                )

                Text(
                    text = weatherInfo.curWeather,
                    fontFamily = poppins_bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = colorResource(R.color.black15F2)
                )

                Text(
                    text = "${weatherInfo.tempMax}° ${weatherInfo.tempMin}°",
                    fontFamily = poppins_bold,
                    fontSize = 16.sp,
                    color = colorResource(R.color.black15F2)
                )

                Text(
                    text = "${stringResource(R.string.feels_like)} ${weatherInfo.curTempFeelsLike}°",
                    fontFamily = poppins_bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = colorResource(R.color.black15F2)
                )

                Row {
                    Image(
                        modifier = Modifier.size(20.dp),
                        painter = getHumidityIcon(weatherInfo.curHumidity),
                        contentDescription = null
                    )


                    Text(
                        text = "${weatherInfo.curHumidity}%",
                        fontFamily = poppins_bold,
                        fontSize = 16.sp,
                        color = colorResource(R.color.black15F2)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .padding(horizontal = 5.dp)
                .border(
                    2.dp,
                    colorResource(R.color.black15F2),
                    RoundedCornerShape(30.dp)
                )
                .padding(vertical = 10.dp)
                .clip(RoundedCornerShape(20.dp))
                .horizontalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.width(10.dp))

            if (weatherInfo.dayHourlyInfo.isNotEmpty()) {
                val weatherSunset = weatherInfo.sunset
                val weatherSunrise = weatherInfo.sunrise
                for (i in 0..24) {
                    val weatherHourInfo = weatherInfo.dayHourlyInfo[i]

                    HourForecast(
                            modifier = Modifier
                                .padding(horizontal = 10.dp),
                            info = weatherHourInfo
                    )

                    if (weatherSunset.hour == weatherHourInfo.hour){
                        // its sunset
                        HourSunForecast(
                            modifier = Modifier
                                .padding(horizontal = 10.dp),
                            hour = weatherSunset.hour,
                            minutes = weatherSunset.minute,
                            painter = painterResource(R.drawable.sunset)
                        )
                    } else if (weatherSunrise.hour == weatherHourInfo.hour) {
                        // its sunrise
                        HourSunForecast(
                            modifier = Modifier
                                .padding(horizontal = 10.dp),
                            hour = weatherSunrise.hour,
                            minutes = weatherSunrise.minute,
                            painter = painterResource(R.drawable.sunrise)
                        )
                    }
                }
            } else {
                for (i in 0..23) {
                    HourForecast(
                        modifier = Modifier
                            .padding(horizontal = 10.dp),
                        info = WeatherHourlyInfo(
                            i,
                            0,
                            stringResource(R.string.sunny_cloudy),
                            18,
                            0
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))
        }

        Text(
            modifier = Modifier
                .fillMaxWidth(),
            text = stringResource(R.string.forecast_for_the_week),
            fontFamily = poppins_bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = colorResource(R.color.black15F2)
        )

        Column(
            modifier = Modifier.padding(horizontal = 5.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (weatherInfo.weekInfo.isNotEmpty()) {
                for (i in 0..7) {
                    DayForecast(
                        info = weatherInfo.weekInfo[i]
                    )
                }
            } else {
                for (i in 0..7) {
                    DayForecast(
                        info = WeatherDayOfWeekInfo(
                            stringResource(R.string.monday),
                            stringResource(R.string.sunny_cloudy),
                            22,
                            16,
                            5
                        )
                    )
                }
            }
        }

        Column {
            Text(
                modifier = Modifier.padding(start = 10.dp),
                text = "Created by Orest Tretiak©",
                fontFamily = poppins_bold,
                fontSize = 14.sp,
                color = colorResource(R.color.black15F2)
            )
            Text(
                modifier = Modifier.padding(start = 15.dp, bottom = 15.dp),
                text = "for Upwork portfolio 12.08.2024",
                fontFamily = poppins_bold,
                fontSize = 14.sp,
                color = colorResource(R.color.black15F2)
            )
        }
    }
}

@Composable
private fun InternetErrorPreview() {
    val context = LocalContext.current
    Box(modifier = Modifier
        .fillMaxSize()
        .background(colorResource(R.color.whiteF2B15)),
        contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(50.dp)
        ){
            Text(
                modifier = Modifier.width(180.dp),
                text = stringResource(R.string.sorry_but_we_cant_load_the_weather),
                fontFamily = poppins_bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = colorResource(R.color.black15F2)
            )

            Image(
                modifier = Modifier.size(150.dp),
                painter = painterResource(R.drawable.interner_error),
                contentDescription = null
            )

            Text(
                modifier = Modifier.width(220.dp),
                text = stringResource(R.string.no_internet_connection),
                fontFamily = poppins_bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                color = colorResource(R.color.black15F2)
            )

            Box(
                modifier = Modifier
                    .background(colorResource(R.color.black15F2), CircleShape)
                    .clickable (
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ){
                        // update server request
                        updateWeatherData(context, userLat, userLon)
                    }
            ){
                Text(
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 10.dp),
                    text = "Try again",
                    fontFamily = poppins_bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = colorResource(R.color.whiteF2B15)
                )
            }
        }
    }
}

var isAnimate = true

@Composable
fun LoadingPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Box(modifier = modifier
        .fillMaxSize()){

        val backgroundAVD by remember { mutableStateOf(ContextCompat
            .getDrawable(
                context,
                R.drawable.avd_loading_is_complemented
            ) as AnimatedVectorDrawable) }


        var currentAVD by remember { mutableStateOf(ContextCompat
            .getDrawable(
                context,
                R.drawable.avd_logo
            ) as AnimatedVectorDrawable) }

        AVDComposable(
            modifier = Modifier.fillMaxSize(),
            avd = backgroundAVD,
            scaleType = ScaleType.CENTER_CROP
        )

        AVDComposable(
            modifier = Modifier
                .size(165.dp)
                .align(Alignment.Center),
            avd = currentAVD,
        )

        if (isAnimate) {
            val loadingAVD = ContextCompat
                .getDrawable(
                    context,
                    R.drawable.avd_loading
                ) as AnimatedVectorDrawable

            currentAVD.registerAnimationCallback(object : AnimationCallback() {
                override fun onAnimationEnd(drawable: Drawable?) {
                    super.onAnimationEnd(drawable)

                    loadingAVD.registerAnimationCallback(object : AnimationCallback() {
                        override fun onAnimationEnd(drawable: Drawable?) {
                            super.onAnimationEnd(drawable)

                            if (isLoadingInfo.value){
                                currentAVD.reset()
                                currentAVD.start()
                            } else {
                                backgroundAVD.registerAnimationCallback(object: AnimationCallback() {
                                    override fun onAnimationEnd(drawable: Drawable?) {
                                        super.onAnimationEnd(drawable)

                                        if (isLoading.value) {
                                            isLoading.value = false
                                        }
                                    }
                                })

                                currentAVD.stop()
                                currentAVD.clearAnimationCallbacks()

                                backgroundAVD.start()
                            }
                        }
                    })

                    currentAVD = loadingAVD

                    currentAVD.start()
                }
            })

            currentAVD.start()

            isAnimate = false
        }
    }
}

@Composable
private fun HourForecast(
    modifier: Modifier = Modifier,
    info: WeatherHourlyInfo
) {
    val context = LocalContext.current
   Column (
       modifier = modifier,
       horizontalAlignment = Alignment.CenterHorizontally,
       verticalArrangement = Arrangement.spacedBy(15.dp)
   ){
       Text(
           text = "${info.hour}.${String.format("%02d", info.minutes)}",
           fontFamily = poppins_bold,
           fontSize = 16.sp,
           color = colorResource(R.color.black15F2)
       )

       AVDComposable(
           modifier = Modifier.size(28.dp),
           avd = getWeatherIcon(context, info.weather, LocalTime.of(info.hour, 0))
       )

       Text(
           text = "${info.temp}°",
           fontFamily = poppins_bold,
           fontSize = 16.sp,
           color = colorResource(R.color.black15F2)
       )

       Image(
           modifier = Modifier.size(20.dp),
           painter = getHumidityIcon(info.humidity),
           contentDescription = null
       )
   }
}

@Composable
private fun HourSunForecast(
    modifier: Modifier = Modifier,
    hour: Int,
    minutes: Int,
    painter: Painter
) {
    Column (
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Text(
            text = "${hour}.${String.format("%02d", minutes)}",
            fontFamily = poppins_bold,
            fontSize = 16.sp,
            color = colorResource(R.color.black15F2)
        )

        Spacer(Modifier.height(5.dp))

        Image(
            modifier = Modifier.size(45.dp),
            painter = painter,
            contentDescription = null
        )
    }
}

@Composable
private fun DayForecast(
    modifier: Modifier = Modifier,
    info: WeatherDayOfWeekInfo
) {
    val context = LocalContext.current
    Row (
        modifier = modifier
            .border(
                2.dp,
                colorResource(R.color.black15F2),
                RoundedCornerShape(25.dp)
            )
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ){
        Column (
            horizontalAlignment = Alignment.CenterHorizontally
        ){
            Text(
                text = info.day,
                fontFamily = poppins_bold,
                fontSize = 16.sp,
                color = colorResource(R.color.black15F2)
            )

            Spacer(Modifier.height(15.dp))

            Text(
                text = "${info.tempDay}° ${info.tempNight}°",
                fontFamily = poppins_bold,
                fontSize = 16.sp,
                color = colorResource(R.color.black15F2)
            )
        }

        Spacer(Modifier.weight(1f))

        Image(
            modifier = Modifier.size(23.dp),
            painter = getHumidityIcon(info.humidity),
            contentDescription = null
        )

        Spacer(Modifier.width(5.dp))

        Text(
            text = "${info.humidity}%",
            fontFamily = poppins_bold,
            fontSize = 14.sp,
            color = colorResource(R.color.black15F2)
        )

        Spacer(Modifier.width(20.dp))

        AVDComposable(
            modifier = Modifier.size(55.dp),
            avd = getWeatherIcon(context, info.weather, LocalTime.now())
        )
    }
}

@Composable
private fun AVDComposable(
    modifier: Modifier = Modifier,
    avd: AnimatedVectorDrawable,
    scaleType: ScaleType = ScaleType.FIT_CENTER
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                setImageDrawable(avd)
                setScaleType(scaleType)
            }
        },
        update = { imageView ->
            if (avd != imageView.drawable as AnimatedVectorDrawable) {
                imageView.setImageDrawable(avd)
            }
        }
    )
}

@Composable
fun getWeatherIcon(context: Context, weather: String, time: LocalTime): AnimatedVectorDrawable{
    if (weather == stringResource(R.string.sunny)){
        return if (isNight(time)) {
            ContextCompat.getDrawable(context, R.drawable.avd_moon) as AnimatedVectorDrawable
        } else {
            ContextCompat.getDrawable(context, R.drawable.avd_sun) as AnimatedVectorDrawable
        }
    }

    if (weather == stringResource(R.string.sunny_cloudy)){
        return if (isNight(time)){
            ContextCompat.getDrawable(context, R.drawable.avd_moon_cloudy) as AnimatedVectorDrawable
        } else {
            ContextCompat.getDrawable(context, R.drawable.avd_sun_cloudy) as AnimatedVectorDrawable
        }
    }

    return when(weather){
        stringResource(R.string.night) -> ContextCompat.getDrawable(context, R.drawable.avd_moon) as AnimatedVectorDrawable
        stringResource(R.string.night_cloudy) -> ContextCompat.getDrawable(context, R.drawable.avd_moon_cloudy) as AnimatedVectorDrawable
        stringResource(R.string.cloudy) -> ContextCompat.getDrawable(context, R.drawable.avd_cloudy) as AnimatedVectorDrawable
        stringResource(R.string.windy_cloudy) -> ContextCompat.getDrawable(context, R.drawable.avd_cloudy_windy) as AnimatedVectorDrawable
        stringResource(R.string.windy) -> ContextCompat.getDrawable(context, R.drawable.avd_windy) as AnimatedVectorDrawable
        stringResource(R.string.rain) -> ContextCompat.getDrawable(context, R.drawable.avd_rain) as AnimatedVectorDrawable
        stringResource(R.string.thunderstorm) -> ContextCompat.getDrawable(context, R.drawable.avd_rain_thunder) as AnimatedVectorDrawable
        stringResource(R.string.thunderstorm_no_rain) -> ContextCompat.getDrawable(context, R.drawable.avd_thunder) as AnimatedVectorDrawable
        stringResource(R.string.snowy) -> ContextCompat.getDrawable(context, R.drawable.avd_snowy) as AnimatedVectorDrawable
        else -> ContextCompat.getDrawable(context, R.drawable.avd_sun) as AnimatedVectorDrawable
    }
}

private fun isNight(time: LocalTime): Boolean {
    val start = LocalTime.of(weatherData.value.sunset.hour, weatherData.value.sunset.minute)
    val end = LocalTime.of(weatherData.value.sunrise.hour, weatherData.value.sunrise.minute)

    return time.isAfter(start) || time.isBefore(end)
}

@Composable
fun getHumidityIcon(humidity: Int): Painter {
    return if (humidity in 1..25) {
        painterResource(R.drawable.humidity_25)
    } else if (humidity in 26..50) {
        painterResource(R.drawable.humidity_50)
    } else if (humidity in 51..75) {
        painterResource(R.drawable.humidity_75)
    } else if (humidity >= 76) {
        painterResource(R.drawable.humidity_100)
    } else {
        painterResource(R.drawable.humidity_0)
    }
}

@Composable
fun Message(
    modifier: Modifier = Modifier,
) {
    val text by messageTextState.collectAsState()
    var onConfirm = onMessageConfirm
    var onCancel = onMessageCancel

    Box(modifier = Modifier
        .fillMaxSize(),
        contentAlignment = Alignment.Center
    ){
        Box(Modifier
            .fillMaxSize()
            .background(colorResource(R.color.whiteF2B15))
            .alpha(0.5f))
        Box(
            modifier = modifier
                .background(colorResource(R.color.whiteF2B15), RoundedCornerShape(20.dp))
                .border(2.dp, colorResource(R.color.black15F2), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 15.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    modifier = Modifier
                        .width(220.dp)
                        .padding(vertical = 30.dp),
                    text = text,
                    fontFamily = poppins_bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = colorResource(R.color.black15F2)
                )

                Spacer(Modifier.height(10.dp))

                Row {
                    Box(
                        modifier = Modifier
                            .background(colorResource(R.color.black15F2), CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onConfirm()

                                isMessageShowState.value = false
                            }
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 40.dp, vertical = 10.dp),
                            text = "Confirm",
                            fontFamily = poppins_bold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            color = colorResource(R.color.whiteF2B15)
                        )
                    }

                    Spacer(Modifier.width(30.dp))

                    Box(
                        modifier = Modifier
                            .background(colorResource(R.color.black15F2), CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onCancel()

                                isMessageShowState.value = false
                            }
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 40.dp, vertical = 10.dp),
                            text = "Cancel",
                            fontFamily = poppins_bold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            color = colorResource(R.color.whiteF2B15)
                        )
                    }
                }
            }
        }
    }
}