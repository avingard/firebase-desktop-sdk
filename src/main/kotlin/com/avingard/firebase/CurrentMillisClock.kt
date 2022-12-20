package com.avingard.firebase

import com.google.api.core.ApiClock
import java.io.Serializable
import java.util.concurrent.TimeUnit

class CurrentMillisClock : ApiClock, Serializable {

    override fun nanoTime(): Long {
        return TimeUnit.NANOSECONDS.convert(millisTime(), TimeUnit.MILLISECONDS)
    }

    override fun millisTime(): Long {
        return System.currentTimeMillis();
    }
}