package com.ustc.vacancychecker.data.model

import com.google.gson.annotations.SerializedName

data class TrackedCourse(
    @SerializedName("courseId") val courseId: String,
    @SerializedName("courseName") val courseName: String,
    @SerializedName("teacher") val teacher: String = "",
    @SerializedName("vacancy") val vacancy: Int = 0,
    @SerializedName("lastCheckTime") val lastCheckTime: Long = 0L,
    @SerializedName("isMonitoring") val isMonitoring: Boolean = true
)
