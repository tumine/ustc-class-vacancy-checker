package com.ustc.vacancychecker.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VacancyNotificationPolicyTest {
    @Test
    fun `first positive vacancy sends a notification`() {
        assertTrue(
            VacancyNotificationPolicy.shouldNotify(
                currentVacancy = 8,
                previousCheckedVacancy = 0,
                lastNotifiedVacancy = null
            )
        )
    }

    @Test
    fun `unchanged vacancy at or above cutoff is suppressed`() {
        assertFalse(
            VacancyNotificationPolicy.shouldNotify(
                currentVacancy = 8,
                previousCheckedVacancy = 8,
                lastNotifiedVacancy = 8
            )
        )
        assertFalse(
            VacancyNotificationPolicy.shouldNotify(
                currentVacancy = VacancyNotificationPolicy.ALWAYS_NOTIFY_BELOW_VACANCY,
                previousCheckedVacancy = VacancyNotificationPolicy.ALWAYS_NOTIFY_BELOW_VACANCY,
                lastNotifiedVacancy = VacancyNotificationPolicy.ALWAYS_NOTIFY_BELOW_VACANCY
            )
        )
    }

    @Test
    fun `vacancy below cutoff always sends a notification`() {
        assertTrue(
            VacancyNotificationPolicy.shouldNotify(
                currentVacancy = VacancyNotificationPolicy.ALWAYS_NOTIFY_BELOW_VACANCY - 1,
                previousCheckedVacancy = VacancyNotificationPolicy.ALWAYS_NOTIFY_BELOW_VACANCY - 1,
                lastNotifiedVacancy = VacancyNotificationPolicy.ALWAYS_NOTIFY_BELOW_VACANCY - 1
            )
        )
    }

    @Test
    fun `changed vacancy sends a notification`() {
        assertTrue(
            VacancyNotificationPolicy.shouldNotify(
                currentVacancy = 9,
                previousCheckedVacancy = 8,
                lastNotifiedVacancy = 8
            )
        )
    }

    @Test
    fun `vacancy returning after a full class sends a notification`() {
        assertTrue(
            VacancyNotificationPolicy.shouldNotify(
                currentVacancy = 8,
                previousCheckedVacancy = 0,
                lastNotifiedVacancy = 8
            )
        )
    }

    @Test
    fun `a full class does not send a vacancy notification`() {
        assertFalse(
            VacancyNotificationPolicy.shouldNotify(
                currentVacancy = 0,
                previousCheckedVacancy = 8,
                lastNotifiedVacancy = 8
            )
        )
    }
}
