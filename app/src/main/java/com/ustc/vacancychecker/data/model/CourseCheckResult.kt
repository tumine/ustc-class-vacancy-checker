package com.ustc.vacancychecker.data.model

/** 后台单课堂检查结果。选中状态与余量、自动选课动作彼此独立。 */
data class CourseCheckResult(
    val vacancy: Int,
    val isAlreadySelected: Boolean,
    val selectResult: SelectResult? = null,
    val switchResult: CourseSwitchResult? = null
) {
    val selectionConfirmed: Boolean
        get() = isAlreadySelected || selectResult?.success == true
}

data class CourseCheckRequest(
    val courseId: String,
    val groupId: String,
    val priority: Int,
    val autoSelectEnabled: Boolean,
    val selectedCourseBehavior: SelectedCourseBehavior,
    val pendingSwitchSourceId: String? = null,
    val pendingSwitchTargetId: String? = null
)

data class CourseSwitchResult(
    val state: CourseSwitchState,
    val sourceCourseId: String,
    val targetCourseId: String,
    val message: String,
    val actionLog: List<String>
)
