package com.groovio.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One process-wide observer; no activity references. */
class Connectivity(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private fun available(): Boolean = manager.getNetworkCapabilities(manager.activeNetwork)?.let {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } == true
    /** Wi-Fi/Ethernet, detected separately from mobile data (including while on VPN). */
    private fun localBroadband(): Boolean = manager.allNetworks.any { network ->
        manager.getNetworkCapabilities(network)?.let { caps ->
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } == true
    }
    private val mutable = MutableStateFlow(available())
    private val wifiMutable = MutableStateFlow(localBroadband())
    val wifiConnected = wifiMutable.asStateFlow()
    private fun refresh() {
        mutable.value = available()
        wifiMutable.value = localBroadband()
    }
    val online = mutable.asStateFlow()
    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { refresh() }
            override fun onLost(network: Network) { refresh() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { refresh() }
        })
    }
}
