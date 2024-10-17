package com.abovepersonal.weather

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class VariousUtils {
    companion object {
        fun isNetworkConnected(context: Context): Boolean{
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?

            if (connectivityManager != null) {
                val network = connectivityManager.activeNetwork
                if (network != null) {
                    val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                    if (networkCapabilities != null) {
                        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    }
                }
            }
            return false
        }
    }
}