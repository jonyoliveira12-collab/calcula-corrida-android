package br.com.calculacorrida

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/** Acesso online obrigatório: exige internet validada, não apenas Wi-Fi/dados conectados. */
object NetworkGate {
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun register(context: Context, callback: ConnectivityManager.NetworkCallback) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try { manager.registerDefaultNetworkCallback(callback) } catch (_: Exception) {}
    }

    fun unregister(context: Context, callback: ConnectivityManager.NetworkCallback) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try { manager.unregisterNetworkCallback(callback) } catch (_: Exception) {}
    }
}