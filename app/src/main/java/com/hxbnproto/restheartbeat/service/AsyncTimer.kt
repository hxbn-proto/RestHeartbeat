package com.hxbnproto.restheartbeat.service

import java.time.Duration


class AsyncTimer {
    private var duration: Duration = Duration.ZERO
    var active: Boolean = false

    var onTick: ((duration: Duration) -> Unit)? = null
    var onStart: ((timer: AsyncTimer) -> Unit)? = null
    var onFinish: ((timer: AsyncTimer) -> Unit)? = null

    fun startTimer() {
        if (!active) {
            val timerThread = Thread {
                active = true
                onStart?.invoke(this)

                while (active) {
                    onTick?.invoke(duration)
                    Thread.sleep(1000)
                    duration = duration.plusSeconds(1)
                }

                onFinish?.invoke(this)
            }
            timerThread.isDaemon = true
            timerThread.start()
        }
    }

    fun stopTimer() {
        active = false
        duration = Duration.ZERO
    }
}
