package com.hxbnproto.restheartbeat.service

import android.app.*
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import com.hxbnproto.restheartbeat.MainActivity
import com.hxbnproto.restheartbeat.NotificationType
import com.hxbnproto.restheartbeat.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.*
import java.net.MalformedURLException
import java.net.URL
import java.net.URLConnection
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


class PingService : Service() {
    private val APP_PACKAGE: String = "com.hxbnproto.restheartbeat"

    private var backgrChannelId: String = ""
    private var notificationChannelId: String = ""

    var startDateText: String = "-"
    var statusText: String = "-"
    var statusTextColor: Int = Color.BLACK
    var timeSpent: String = "0s"

    var timer: AsyncTimer = AsyncTimer()
    private val binder = LocalBinder()

    // temp variables
    var secondsRemaining: Int = 0
    var secondsCountDown: Int = 0
    var secondsCycleCounter: Int = 0
    var requestsCounter: Int = 0
    var found: Boolean = false
    var responseText: String = ""

    // user variables
    var endpointUrl: String = ""
        set(value) {
            getSharedPreferences(APP_PACKAGE, Context.MODE_PRIVATE)
                    .edit()
                    .putString("$APP_PACKAGE.endpoint", value)
                    .apply()
            field = value
        }
    var secondsDelayFrom: Int = 0
        set(value) {
            getSharedPreferences(APP_PACKAGE, Context.MODE_PRIVATE)
                    .edit()
                    .putInt("$APP_PACKAGE.delay.from", value)
                    .apply()
            field = value
        }
    var secondsDelayTo: Int = 0
        set(value) {
            getSharedPreferences(APP_PACKAGE, Context.MODE_PRIVATE)
                    .edit()
                    .putInt("$APP_PACKAGE.delay.to", value)
                    .apply()
            field = value
        }
    var resultContainText: String = ""
        set(value) {
            getSharedPreferences(APP_PACKAGE, Context.MODE_PRIVATE)
                    .edit()
                    .putString("$APP_PACKAGE.result.contains.text", value)
                    .apply()
            field = value
        }
    var resultContainsDate: Boolean = false
        set(value) {
            getSharedPreferences(APP_PACKAGE, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("$APP_PACKAGE.result.contains.date", value)
                    .apply()
            field = value
        }
    var resultContainsTime: Boolean = false
        set(value) {
            getSharedPreferences(APP_PACKAGE, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("$APP_PACKAGE.result.contains.time", value)
                    .apply()
            field = value
        }
    var notificationsEnabled: Boolean = true
        set(value) {
            getSharedPreferences(APP_PACKAGE, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("$APP_PACKAGE.notifications", value)
                    .apply()
            field = value
        }
    var infiniteCycles: Boolean = true
        set(value) {
            getSharedPreferences(APP_PACKAGE, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("$APP_PACKAGE.infinite", value)
                    .apply()
            field = value
        }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): PingService = this@PingService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(APP_PACKAGE, Context.MODE_PRIVATE)

        endpointUrl = prefs.getString("$APP_PACKAGE.endpoint", "").toString()
        secondsDelayFrom = prefs.getInt("$APP_PACKAGE.delay.from", 0)
        secondsDelayTo = prefs.getInt("$APP_PACKAGE.delay.to", 0)
        resultContainText = prefs.getString("$APP_PACKAGE.result.contains.text", "").toString()
        resultContainsDate = prefs.getBoolean("$APP_PACKAGE.result.contains.date", false)
        resultContainsTime = prefs.getBoolean("$APP_PACKAGE.result.contains.time", false)
        notificationsEnabled = prefs.getBoolean("$APP_PACKAGE.notifications", true)
        infiniteCycles = prefs.getBoolean("$APP_PACKAGE.infinite", true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { // do your jobs here
        startForeground()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startForeground() {
        backgrChannelId = createDefaultNotificationChannel("rest_hb_backgr_service", "RESTHeartbeat Background Service")
        notificationChannelId = createActiveNotificationChannel(
                "rest_hb_notif_channel", "RESTHeartbeat Active Notifications")

        val notificationIntent = Intent(this, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

        val notificationBuilder = NotificationCompat.Builder(this, backgrChannelId)
        val notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.ic_icon_white)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        startForeground(101, notification)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createDefaultNotificationChannel(channelId: String, channelName: String): String {
        val chan = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createActiveNotificationChannel(channelId: String, channelName: String): String {
        val sound: Uri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
                + applicationContext.packageName + "/" + R.raw.notification_sound)

        val mChannel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH)

        val attributes:AudioAttributes = AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

        // Configure the notification channel.
        mChannel.description = channelName
        mChannel.enableLights(true)
        mChannel.enableVibration(true)
        mChannel.setSound(sound, attributes)

        mChannel.lightColor = Color.BLUE
        mChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(mChannel)

        return channelId
    }

    // Function that makes the network request, blocking the current thread
    fun makeRequest(unit: ((result: String) -> Unit)?) {
        GlobalScope.launch {
            var result = ""

            if (endpointUrl.isBlank()) {
                result = "Error: endpoint is blank"
            }

            try {

                val trustAllCerts: Array<TrustManager> = arrayOf(object : X509TrustManager {

                    override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
                    override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate?>? {
                        return null
                    }
                })

                val sc: SSLContext = SSLContext.getInstance("SSL")
                sc.init(null, trustAllCerts, SecureRandom())
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)

                val endpoint = URL(endpointUrl)


                withContext(Dispatchers.IO) {
                    val bufferedReader: BufferedReader

                    val tc: URLConnection = endpoint.openConnection()

                    bufferedReader = try {
                        BufferedReader(InputStreamReader(tc.getInputStream()))
                    } catch (e: FileNotFoundException) {
                        BufferedReader(InputStreamReader((tc as HttpsURLConnection).errorStream))
                    }
                    result = readAll(bufferedReader)
                }

            } catch (e: MalformedURLException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: JSONException) {
                e.printStackTrace()
            }

            unit?.invoke(result)
        }
    }

    private fun readAll(rd: Reader): String {
        val stringBuilder = java.lang.StringBuilder()
        var copy: Int
        while (rd.read().also { copy = it } != -1) {
            stringBuilder.append(copy.toChar())
        }
        return stringBuilder.toString()
    }

    fun sendNotification(message: String, notificationType: NotificationType) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

//        var soundUri: Uri? = null
//        if (notificationType == NotificationType.GOOD) {
//            val soundResId = R.raw.notification_sound
//            val packageName: String = applicationContext.packageName
//
//            soundUri = Uri.parse("android.resource://$packageName/$soundResId")
//        }

        val notificationBuilder = NotificationCompat.Builder(this, notificationChannelId)
        val notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(notificationType.icon)
                .setContentText(message)
//                .setSound(soundUri)
//                .setDefaults(Notification.DEFAULT_VIBRATE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_ALARM)
                .build()
        startForeground(101, notification)
    }
}
