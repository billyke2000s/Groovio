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
    private val mutable = MutableStateFlow(available())
    val online = mutable.asStateFlow()
    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { mutable.value = available() }
            override fun onLost(network: Network) { mutable.value = available() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { mutable.value = available() }
        })
    }
}
