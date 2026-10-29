package nl.neerdael.backdrop_launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import nl.neerdael.backdrop_launcher.core.needsSecondTicks
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong

@Composable
internal fun StatusClock(style: HomeAppearance, active: Boolean) {
    if (!style.showDate && !style.showTime) return
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val configuration = LocalConfiguration.current
    @Suppress("DEPRECATION")
    val locale = if (Build.VERSION.SDK_INT >= 24) configuration.locales[0] else configuration.locale
    val zone = TimeZone.getDefault()
    val date = remember(style.dateFormat, locale, zone) { safeDateFormat(style.dateFormat, "EEEE d", locale, zone) }
    val time = remember(style.timeFormat, locale, zone) { safeDateFormat(style.timeFormat, "H:mm", locale, zone) }
    DisposableEffect(context, active) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { now = System.currentTimeMillis() }
        }
        if (active) {
            now = System.currentTimeMillis()
            context.registerReceiver(receiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_LOCALE_CHANGED)
            })
        }
        onDispose { if (active) context.unregisterReceiver(receiver) }
    }
    val secondsVisible = needsSecondTicks(
        if (style.showTime) style.timeFormat else "", if (style.showDate) style.dateFormat else "")
    if (active && secondsVisible) LaunchedEffect(secondsVisible) {
        while (isActive) {
            delay(1000L - System.currentTimeMillis() % 1000L)
            now = System.currentTimeMillis()
        }
    }
    val moment = Date(now)
    Text(listOfNotNull(if (style.showDate) date.format(moment) else null, if (style.showTime) time.format(moment) else null)
        .joinToString("   "), fontSize = 22.sp)
}

private fun safeDateFormat(pattern: String, fallback: String, locale: Locale, zone: TimeZone) =
    runCatching { SimpleDateFormat(pattern, locale) }.getOrElse { SimpleDateFormat(fallback, locale) }.apply { timeZone = zone }

private data class ConnectionLabel(val text: String, val connected: Boolean)

@Composable
internal fun NetworkStatus(active: Boolean, open: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(ConnectionLabel("Network", true)) }
    LaunchedEffect(context, active) {
        if (!active) return@LaunchedEffect
        val observer = this
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val generation = AtomicLong()
        fun update() {
            val request = generation.incrementAndGet()
            observer.launch {
                val next = withContext(Dispatchers.IO) { connectionLabel(context, manager) }
                if (request == generation.get()) state = next
            }
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = update()
            override fun onLost(network: Network) = update()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = update()
        }
        var registered = false
        try {
            withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= 24) manager.registerDefaultNetworkCallback(callback)
                else manager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
                registered = true
            }
            update()
            awaitCancellation()
        } catch (failure: kotlinx.coroutines.CancellationException) { throw failure }
        catch (_: Exception) { state = ConnectionLabel("Network unavailable", false) }
        finally {
            if (registered) withContext(NonCancellable + Dispatchers.IO) { runCatching { manager.unregisterNetworkCallback(callback) } }
        }
    }
    UiButton(open) { Text(state.text, Modifier.padding(8.dp), fontSize = 15.sp, color = if (state.connected) Color.White else Color(0xffff8a80)) }
}

@Suppress("DEPRECATION")
private fun connectionLabel(context: Context, manager: ConnectivityManager): ConnectionLabel = runCatching {
    val network = manager.activeNetwork ?: return ConnectionLabel("No connection", false)
    val caps = manager.getNetworkCapabilities(network) ?: return ConnectionLabel("No connection", false)
    val label = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val strength = wifi?.connectionInfo?.rssi?.let { WifiManager.calculateSignalLevel(it, 5) }
            "Wi-Fi" + (strength?.let { "  " + listOf("▁", "▂", "▂▄", "▂▄▆", "▂▄▆█")[it.coerceIn(0, 4)] } ?: "")
        }
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
        else -> "Connected"
    }
    ConnectionLabel(label, true)
}.getOrDefault(ConnectionLabel("Network unavailable", false))
