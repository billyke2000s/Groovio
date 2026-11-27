package com.groovio.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One process-wide observer; no activity references. */
class Connectivity(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Suppress("DEPRECATION")
    private fun available(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return manager.activeNetworkInfo?.isConnected == true
        }
        return manager.getNetworkCapabilities(manager.activeNetwork)?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } == true
    }

    /** Use the active route's metered status, so VPN/mobile traffic doesn't trigger HD. */
    @Suppress("DEPRECATION")
    private fun localBroadband(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            val network = manager.activeNetworkInfo ?: return false
            return network.isConnected && !manager.isActiveNetworkMetered &&
                (network.type == ConnectivityManager.TYPE_WIFI ||
                    network.type == ConnectivityManager.TYPE_ETHERNET ||
                    network.type == ConnectivityManager.TYPE_VPN)
        }
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            !manager.isActiveNetworkMetered &&
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
    }

    private val mutable = MutableStateFlow(available())
    private val wifiMutable = MutableStateFlow(localBroadband())
    val wifiConnected = wifiMutable.asStateFlow()
    val online = mutable.asStateFlow()

    private fun refresh() {
        mutable.value = available()
        wifiMutable.value = localBroadband()
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { refresh() }
                override fun onLost(network: Network) { refresh() }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { refresh() }
            })
        } else {
            @Suppress("DEPRECATION")
            val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            appContext.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) { refresh() }
            }, filter)
        }
    }
}
