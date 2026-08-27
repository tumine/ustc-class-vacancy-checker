package com.ustc.vacancychecker.data.model

import com.google.gson.annotations.SerializedName

data class TrackedCourse(
    @SerializedName("courseId") val courseId: String,
    @SerializedName("courseName") val courseName: String,
    /**
     * 权威课程标识。它来自 USTC 课程目录的 courseId，绝不由课堂号截断得到。
     * 历史记录没有该字段时保持为 null，并作为独立课堂组展示。
     */
    @SerializedName("courseKey") val courseKey: String? = null,
    @SerializedName("courseNumber") val courseNumber: String? = null,
    @SerializedName("teacher") val teacher: String = "",
    @SerializedName("vacancy") val vacancy: Int = 0,
    @SerializedName("lastCheckTime") val lastCheckTime: Long = 0L,
    @SerializedName("isMonitoring") val isMonitoring: Boolean = true,
    @SerializedName("autoSelectEnabled") val autoSelectEnabled: Boolean? = false,
    @SerializedName("lastSelectMessage") val lastSelectMessage: String? = "",
    /** 新字段使用可空类型，确保 Gson 读取旧 JSON 时能够区分“缺失”与 false/0。 */
    @SerializedName("groupMonitoringEnabled") val groupMonitoringEnabled: Boolean? = null,
    @SerializedName("selectedCourseBehavior") val selectedCourseBehavior: SelectedCourseBehavior? = null,
    @SerializedName("priority") val priority: Int? = null,
    @SerializedName("isAlreadySelected") val isAlreadySelected: Boolean? = null
) {
    val isGroupMonitoringEnabled: Boolean
        get() = groupMonitoringEnabled != false

    val effectiveSelectedCourseBehavior: SelectedCourseBehavior
        get() = selectedCourseBehavior ?: SelectedCourseBehavior.DISABLE_GROUP

    val isEffectivelyMonitoring: Boolean
        get() = isGroupMonitoringEnabled && isMonitoring

    /** 未分组历史课堂必须各自拥有独立分组，不能从 courseId 猜测课程。 */
    val trackingGroupId: String
        get() = courseKey?.let { "course:$it" } ?: "legacy:$courseId"
}

enum class SelectedCourseBehavior {
    /** 仅继续关注优先级更高的课堂；自动换班实现完成前不会执行退课。 */
    PRIORITY_UPGRADE,

    /** 安全默认值：暂停当前课程组，保留课堂开关和顺序。 */
    DISABLE_GROUP,

    /** 删除当前课程组内全部课堂。 */
    DELETE_GROUP
}
