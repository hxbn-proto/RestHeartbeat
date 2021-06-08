package com.hxbnproto.restheartbeat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import com.hxbnproto.restheartbeat.service.AsyncTimer
import com.hxbnproto.restheartbeat.service.PingService
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs


class MainActivity : AppCompatActivity() {
    private lateinit var pingService: PingService
    private var timer: AsyncTimer? = null

    // Constants
    private final val DEFAULT_ENDPOINT_URL = "https://jsonplaceholder.typicode.com/todos/1"

    //    https://stackoverflow.com/questions/15491894/regex-to-validate-date-format-dd-mm-yyyy
    private final val DATE_REGEX = "(?:(?:31(\\/|-|\\.)(?:0?[13578]|1[02]))\\1|(?:(?:29|30)(\\/|-|\\.)" +
            "(?:0?[13-9]|1[0-2])" +
            "\\2))(?:(?:1[6-9]|[2-9]\\d)?\\d{2})|(?:29(\\/|-|\\.)0?2\\3(?:(?:(?:1[6-9]|[2-9]\\d)?" +
            "(?:0[48]|[2468][048]|[13579][26])|(?:(?:16|[2468][048]|[3579][26])00))))|(?:0?[1-9]|1\\d|2[0-8])" +
            "(\\/|-|\\.)(?:(?:0?[1-9])|(?:1[0-2]))\\4(?:(?:1[6-9]|[2-9]\\d)?\\d{2})"

    private val TIME_REGEX = "(([01]?[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9])|(([01]?[0-9]|2[0-3]):[0-5][0-9])"

    // View
    private lateinit var startButton: Button
    private lateinit var resetEndpointButton: ImageButton
    private lateinit var stopButton: Button
    private lateinit var delayFromTextField: TextView
    private lateinit var delayToTextField: TextView
    private lateinit var countDownTimer: TextView
    private lateinit var status: TextView
    private lateinit var startDateTimeText: TextView
    private lateinit var timeSpentText: TextView
    private lateinit var requestsCountText: TextView
    private lateinit var endpointUrl: TextView
    private lateinit var targetJsonSearch: TextView
    private lateinit var jsonResponse: TextView
    private lateinit var cyclicRequestsEnabled: Switch
    private lateinit var shouldContainDate: Switch
    private lateinit var shouldContainTime: Switch
    private lateinit var notificationsEnabled: Switch
    private lateinit var requestProgressBar: ProgressBar

    // Service binding
    private var bound: Boolean = false
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as PingService.LocalBinder
            pingService = binder.getService()
            bound = true

            init()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()
        // Bind to LocalService
        Intent(this, PingService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun init() {

        initView()

        initTimer()

        initButtonListeners()

        readTextValues()

        initSwitchListeners()

        updateTextFields()

        initTextViewListeners()
    }

    private fun updateTextFields() {
        jsonResponse.text = pingService.responseText
        timeSpentText.text = pingService.timeSpent
        startDateTimeText.text = pingService.startDateText

        status.text = pingService.statusText
        status.setTextColor(pingService.statusTextColor)

        requestsCountText.text = pingService.requestsCounter.toString()
    }

    private fun readTextValues() {
        // fill up fields when bind
        if (pingService.secondsDelayFrom == 0) {
            pingService.secondsDelayFrom = Integer.parseInt(delayFromTextField.text.toString())
        } else {
            delayFromTextField.text = pingService.secondsDelayFrom.toString()
        }

        if (pingService.secondsDelayTo == 0) {
            pingService.secondsDelayTo = Integer.parseInt(delayToTextField.text.toString())
        } else {
            delayToTextField.text = pingService.secondsDelayTo.toString()
        }

        if (pingService.endpointUrl.isNotBlank()) {
            endpointUrl.text = pingService.endpointUrl
        } else {
            pingService.endpointUrl = endpointUrl.text.toString()
        }

        if (pingService.resultContainText.isNotBlank()) {
            targetJsonSearch.text = pingService.resultContainText
        } else {
            pingService.resultContainText = targetJsonSearch.text.toString()
        }

        if (pingService.timeSpent.isNotBlank()) {
            timeSpentText.text = pingService.timeSpent
        }
    }

    private fun initTextViewListeners() {
        delayFromTextField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null) {
                    val parsed = if (s.isNotBlank()) {
                        Integer.parseInt(s.toString())
                    } else {
                        3
                    }

                    pingService.secondsDelayFrom = if (parsed >= 3) {
                        parsed
                    } else {
                        3
                    }
                }
            }
        })
        delayToTextField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null) {
                    val parsed = if (s.isNotBlank()) {
                        Integer.parseInt(s.toString())
                    } else {
                        3
                    }

                    pingService.secondsDelayTo = if (parsed >= 3) {
                        parsed
                    } else {
                        3
                    }
                }
            }
        })

        endpointUrl.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null) {
                    pingService.endpointUrl = s.toString()
                }
            }
        })
        targetJsonSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null) {
                    pingService.resultContainText = s.toString()
                }
            }
        })
    }

    private fun initSwitchListeners() {
        cyclicRequestsEnabled.setOnCheckedChangeListener(null)
        cyclicRequestsEnabled.isChecked = pingService.infiniteCycles
        cyclicRequestsEnabled.setOnCheckedChangeListener { _, isChecked ->
            pingService.infiniteCycles = isChecked
        }

        shouldContainDate.setOnCheckedChangeListener(null)
        shouldContainDate.isChecked = pingService.resultContainsDate
        shouldContainDate.setOnCheckedChangeListener { _, isChecked ->
            pingService.resultContainsDate = isChecked
        }

        shouldContainTime.setOnCheckedChangeListener(null)
        shouldContainTime.isChecked = pingService.resultContainsTime
        shouldContainTime.setOnCheckedChangeListener { _, isChecked ->
            pingService.resultContainsTime = isChecked
        }

        notificationsEnabled.setOnCheckedChangeListener(null)
        notificationsEnabled.isChecked = pingService.notificationsEnabled
        notificationsEnabled.setOnCheckedChangeListener { _, isChecked ->
            pingService.notificationsEnabled = isChecked
        }
    }

    private fun initButtonListeners() {
        startButton.setOnClickListener {
            startService(Intent(this, PingService::class.java))

            if (!timer!!.active) {
                it.isEnabled = false
                timer!!.startTimer()
            }
        }

        stopButton.setOnClickListener {
            stopService(Intent(this, PingService::class.java))

            if (!pingService.found) {
                setStatus(Status.STOPPED)
            }

            startButton.isEnabled = true
            timer!!.stopTimer()
        }

        resetEndpointButton.setOnClickListener {
            pingService.endpointUrl = DEFAULT_ENDPOINT_URL
            endpointUrl.text = pingService.endpointUrl
        }
    }

    private fun initView() {
        startButton = findViewById(R.id.startButton)
        resetEndpointButton = findViewById(R.id.resetEndpoint)
        stopButton = findViewById(R.id.stopButton)
        delayFromTextField = findViewById(R.id.delayFrom)
        delayToTextField = findViewById(R.id.delayTo)
        countDownTimer = findViewById(R.id.countDownTimer)
        status = findViewById(R.id.statusText)
        startDateTimeText = findViewById(R.id.startDateTime)
        timeSpentText = findViewById(R.id.timeSpent)
        requestsCountText = findViewById(R.id.requestCount)
        endpointUrl = findViewById(R.id.endpointUrl)
        targetJsonSearch = findViewById(R.id.jsonBody)
        jsonResponse = findViewById(R.id.response)
        cyclicRequestsEnabled = findViewById(R.id.cyclicRequestsEnabled)
        shouldContainDate = findViewById(R.id.isContainDate)
        shouldContainTime = findViewById(R.id.isContainTime)
        notificationsEnabled = findViewById(R.id.isNotify)
        requestProgressBar = findViewById(R.id.requestProgressBar)
    }

    private fun initTimer() {
        timer = pingService.timer

        timer!!.onStart = {

            resetCycle()

            runOnUiThread {

                requestProgressBar.setProgress(0, true)
                countDownTimer.text = "0 s"

                setStatus(Status.RUNNING)

                pingService.startDateText = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy hh:mm:ss"))
                startDateTimeText.text = pingService.startDateText

                pingService.timeSpent = formatDuration(Duration.ZERO).toString() + "s"
                timeSpentText.text = pingService.timeSpent

                pingService.requestsCounter = 0
                requestsCountText.text = pingService.requestsCounter.toString()
            }
        }

        timer!!.onTick = {
            println(it)
            runOnUiThread {
                updateCycle(it)
            }
        }

        timer!!.onFinish = {

            runOnUiThread {
                startButton.isEnabled = true
            }
        }

        // buttons and fields
        if (timer!!.active) {
            startButton.isEnabled = false
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        bound = false
    }

    private fun updateCycle(it: Duration) {
        pingService.secondsCycleCounter++
        pingService.secondsCountDown = pingService.secondsRemaining - pingService.secondsCycleCounter

        val progress: Int = 100 * pingService.secondsCycleCounter / pingService.secondsRemaining
        requestProgressBar.setProgress(progress, true)

        countDownTimer.text = pingService.secondsCountDown.toString() + "s"

        pingService.timeSpent = formatDuration(it).toString()
        timeSpentText.text = pingService.timeSpent

        if (pingService.secondsCountDown <= 0) {

            pingService.makeRequest {

                runOnUiThread {
                    pingService.responseText = it
                    jsonResponse.text = pingService.responseText

                    pingService.requestsCounter++
                    requestsCountText.text = pingService.requestsCounter.toString()

                    pingService.found = checkResult(it)

                    // search,
                    // then
                    if (!pingService.found) {
                        if (pingService.endpointUrl.isBlank()) {
                            timer!!.stopTimer()

                            setStatus(Status.NO_ENDPOINT)

                        } else {

                            if (pingService.infiniteCycles) {
                                resetCycle()
                            } else {
                                timer!!.stopTimer()

                                setStatus(Status.NOT_FOUND);

                                if (pingService.notificationsEnabled) {
                                    pingService.sendNotification(
                                            "JSON Data unfortunately not found :'(", NotificationType.BAD)
                                }

                            }
                        }
                    } else {
                        timer!!.stopTimer()

                        setStatus(Status.FOUND)

                        if (pingService.notificationsEnabled) {
                            pingService.sendNotification(
                                    "Found JSON Data!", NotificationType.GOOD)
                        }
                    }
                }
            }
        }
    }

    private fun checkResult(responseStr: String): Boolean {

        if (pingService.resultContainText.isNotBlank() && responseStr.contains(pingService.resultContainText)) {
            return true
        }
        if (pingService.resultContainsDate && responseStr.contains(DATE_REGEX.toRegex())) {
            return true
        }
        if (pingService.resultContainsTime && responseStr.contains(TIME_REGEX.toRegex())) {
            return true
        }
        if (pingService.resultContainText.isBlank()
                && !pingService.resultContainsDate
                && !pingService.resultContainsTime) {

            return true
        }

        return false
    }

    private fun setStatus(newStatus: Status) {
        pingService.statusText = newStatus.text
        status.text = pingService.statusText

        pingService.statusTextColor = newStatus.color
        status.setTextColor(pingService.statusTextColor)
    }

    private fun resetCycle() {
        pingService.secondsCycleCounter = 0
        pingService.secondsRemaining =
                (pingService.secondsDelayFrom.coerceAtMost(pingService.secondsDelayTo)..
                        pingService.secondsDelayTo.coerceAtLeast(pingService.secondsDelayFrom)).random()
    }

    private fun formatDuration(duration: Duration): String? {
        val seconds = duration.seconds
        val absSeconds = abs(seconds)
        val positive = String.format(
                "%02d:%02d:%02d",
                absSeconds / 3600,
                absSeconds % 3600 / 60,
                absSeconds % 60)
        return if (seconds < 0) "-$positive" else positive
    }
}
