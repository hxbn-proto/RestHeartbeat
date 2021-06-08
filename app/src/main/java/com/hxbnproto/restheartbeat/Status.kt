package com.hxbnproto.restheartbeat

import android.graphics.Color

enum class Status(val text: String, val color: Int){
    RUNNING("Running", Color.parseColor("#ffff8800")),
    NO_ENDPOINT("Error: No Endpoint", Color.RED),
    FOUND("FOUND!", Color.parseColor("#ff669900")),
    NOT_FOUND("Not Found :(", Color.DKGRAY),
    STOPPED("Stopped", Color.BLUE);
}