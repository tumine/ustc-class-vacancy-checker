package com.ustc.vacancychecker.data.model

import kotlin.math.abs

/** 决定一次有余量的检测是否需要再次提醒用户。 */
object VacancyNotificationPolicy {
    /**
     * 与上次通知相比，余量至少变化这么多才再次通知。
     * 当前值 1 表示只抑制余量完全相同的重复通知。
     */
    const val MIN_VACANCY_CHANGE_TO_NOTIFY = 1

    /** 当前余量低于此值时属于紧急提醒，不进行重复通知抑制。 */
    const val ALWAYS_NOTIFY_BELOW_VACANCY = 5

    fun shouldNotify(
        currentVacancy: Int,
        previousCheckedVacancy: Int,
        lastNotifiedVacancy: Int?
    ): Boolean {
        if (currentVacancy <= 0) return false
        if (currentVacancy < ALWAYS_NOTIFY_BELOW_VACANCY) return true
        if (lastNotifiedVacancy == null) return true

        // 课堂曾经满员后重新出现余量时，即使数值与更早的通知相同，也应重新提醒。
        if (previousCheckedVacancy <= 0) return true

        val vacancyChange = abs(currentVacancy.toLong() - lastNotifiedVacancy.toLong())
        return vacancyChange >= MIN_VACANCY_CHANGE_TO_NOTIFY
    }
}
