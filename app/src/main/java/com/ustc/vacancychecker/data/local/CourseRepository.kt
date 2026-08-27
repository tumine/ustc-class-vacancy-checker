package com.ustc.vacancychecker.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ustc.vacancychecker.data.model.TrackedCourse
import com.ustc.vacancychecker.data.model.SelectedCourseBehavior
import com.ustc.vacancychecker.data.model.VerificationCodeMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "course_tracking")

@Singleton
class CourseRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val trackedCoursesType = object : TypeToken<MutableList<TrackedCourse>>() {}.type
    
    companion object {
        private val TRACKED_COURSES_KEY = stringPreferencesKey("tracked_courses")
        private val MONITORING_INTERVAL_KEY = intPreferencesKey("monitoring_interval")
        private val AUTO_SELECT_ENABLED_KEY = booleanPreferencesKey("auto_select_enabled")
        private val VERIFICATION_CODE_METHOD_KEY = stringPreferencesKey("verification_code_method")
    }

    val trackedCoursesFlow: Flow<List<TrackedCourse>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[TRACKED_COURSES_KEY] ?: "[]"
        safeParseTrackedCourses(jsonString)
    }
    val monitoringIntervalFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MONITORING_INTERVAL_KEY] ?: 60
    }.distinctUntilChanged()
    
    val autoSelectEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_SELECT_ENABLED_KEY] ?: false
    }.distinctUntilChanged()

    val verificationCodeMethodFlow: Flow<VerificationCodeMethod> = context.dataStore.data.map { preferences ->
        preferences[VERIFICATION_CODE_METHOD_KEY]
            ?.let { storedValue ->
                runCatching { VerificationCodeMethod.valueOf(storedValue) }.getOrNull()
            }
            ?: VerificationCodeMethod.SMS
    }.distinctUntilChanged()

    suspend fun updateMonitoringInterval(intervalMinutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[MONITORING_INTERVAL_KEY] = intervalMinutes
        }
    }
    
    suspend fun updateAutoSelectEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_SELECT_ENABLED_KEY] = enabled
        }
    }

    suspend fun updateVerificationCodeMethod(method: VerificationCodeMethod) {
        context.dataStore.edit { preferences ->
            preferences[VERIFICATION_CODE_METHOD_KEY] = method.name
        }
    }
    
    suspend fun isAutoSelectEnabled(): Boolean {
        return autoSelectEnabledFlow.first()
    }

    suspend fun getTrackedCourses(): List<TrackedCourse> {
        return trackedCoursesFlow.first()
    }

    private fun safeParseTrackedCourses(jsonString: String): MutableList<TrackedCourse> {
        return try {
            gson.fromJson(jsonString, trackedCoursesType) ?: mutableListOf()
        } catch (e: Exception) {
            android.util.Log.e("CourseRepository", "Failed to parse tracked courses, resetting to empty list", e)
            mutableListOf()
        }
    }

    suspend fun addTrackedCourses(courses: List<TrackedCourse>) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[TRACKED_COURSES_KEY] ?: "[]"
            val currentList = safeParseTrackedCourses(jsonString)
            
            for (newCourse in courses) {
                val index = currentList.indexOfFirst { it.courseId == newCourse.courseId }
                if (index != -1) {
                    val old = currentList[index]
                    currentList[index] = newCourse.copy(
                        vacancy = old.vacancy,
                        lastCheckTime = old.lastCheckTime,
                        isMonitoring = old.isMonitoring,
                        autoSelectEnabled = old.autoSelectEnabled,
                        lastSelectMessage = old.lastSelectMessage,
                        groupMonitoringEnabled = old.groupMonitoringEnabled
                            ?: groupTemplate(currentList, newCourse.courseKey)?.groupMonitoringEnabled,
                        selectedCourseBehavior = old.selectedCourseBehavior
                            ?: groupTemplate(currentList, newCourse.courseKey)?.selectedCourseBehavior,
                        priority = old.priority ?: nextPriority(currentList, newCourse.courseKey),
                        isAlreadySelected = old.isAlreadySelected
                    )
                } else {
                    val template = groupTemplate(currentList, newCourse.courseKey)
                    currentList.add(
                        newCourse.copy(
                            groupMonitoringEnabled = template?.groupMonitoringEnabled ?: true,
                            selectedCourseBehavior = template?.selectedCourseBehavior
                                ?: SelectedCourseBehavior.DISABLE_GROUP,
                            priority = nextPriority(currentList, newCourse.courseKey),
                            isAlreadySelected = newCourse.isAlreadySelected ?: false
                        )
                    )
                }
            }
            preferences[TRACKED_COURSES_KEY] = gson.toJson(currentList)
        }
    }
    
    suspend fun addTrackedCourse(course: TrackedCourse) {
        addTrackedCourses(listOf(course))
    }

    suspend fun removeTrackedCourse(courseId: String) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[TRACKED_COURSES_KEY] ?: "[]"
            val currentList = safeParseTrackedCourses(jsonString)
            
            currentList.removeAll { it.courseId == courseId }
            preferences[TRACKED_COURSES_KEY] = gson.toJson(currentList)
        }
    }

    suspend fun updateCourseStatus(
        courseId: String,
        vacancy: Int? = null,
        isMonitoring: Boolean? = null,
        autoSelectEnabled: Boolean? = null,
        lastSelectMessage: String? = null,
        isAlreadySelected: Boolean? = null
    ) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[TRACKED_COURSES_KEY] ?: "[]"
            val currentList = safeParseTrackedCourses(jsonString)

            val index = currentList.indexOfFirst { it.courseId == courseId }
            if (index != -1) {
                val item = currentList[index]
                currentList[index] = item.copy(
                    vacancy = vacancy ?: item.vacancy,
                    lastCheckTime = System.currentTimeMillis(),
                    isMonitoring = isMonitoring ?: item.isMonitoring,
                    autoSelectEnabled = autoSelectEnabled ?: item.autoSelectEnabled,
                    lastSelectMessage = lastSelectMessage ?: item.lastSelectMessage,
                    isAlreadySelected = isAlreadySelected ?: item.isAlreadySelected
                )
                preferences[TRACKED_COURSES_KEY] = gson.toJson(currentList)
            }
        }
    }

    suspend fun updateCheckTimeForCourses(courseIds: List<String>) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[TRACKED_COURSES_KEY] ?: "[]"
            val currentList = safeParseTrackedCourses(jsonString)
            var changed = false
            
            for (courseId in courseIds) {
                val index = currentList.indexOfFirst { it.courseId == courseId }
                if (index != -1) {
                    val item = currentList[index]
                    currentList[index] = item.copy(
                        lastCheckTime = System.currentTimeMillis()
                    )
                    changed = true
                }
            }
            
            if (changed) {
                preferences[TRACKED_COURSES_KEY] = gson.toJson(currentList)
            }
        }
    }
    
    suspend fun toggleMonitoringStatus(courseId: String, isMonitoring: Boolean) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[TRACKED_COURSES_KEY] ?: "[]"
            val currentList = safeParseTrackedCourses(jsonString)
            
            val index = currentList.indexOfFirst { it.courseId == courseId }
            if (index != -1) {
                val item = currentList[index]
                currentList[index] = item.copy(
                    isMonitoring = isMonitoring
                )
                preferences[TRACKED_COURSES_KEY] = gson.toJson(currentList)
            }
        }
    }

    suspend fun updateCourseMetadata(
        courseId: String,
        courseKey: String,
        courseNumber: String? = null
    ) {
        context.dataStore.edit { preferences ->
            val currentList = readCourses(preferences)
            val index = currentList.indexOfFirst { it.courseId == courseId }
            if (index == -1) return@edit

            val item = currentList[index]
            val template = groupTemplate(currentList, courseKey)
            currentList[index] = item.copy(
                courseKey = courseKey,
                courseNumber = courseNumber ?: item.courseNumber,
                groupMonitoringEnabled = template?.groupMonitoringEnabled
                    ?: item.groupMonitoringEnabled
                    ?: true,
                selectedCourseBehavior = template?.selectedCourseBehavior
                    ?: item.selectedCourseBehavior
                    ?: SelectedCourseBehavior.DISABLE_GROUP,
                priority = template?.let { nextPriority(currentList, courseKey) }
                    ?: item.priority
                    ?: 0
            )
            writeCourses(preferences, currentList)
        }
    }

    suspend fun setGroupMonitoring(groupId: String, enabled: Boolean) {
        updateGroup(groupId) { it.copy(groupMonitoringEnabled = enabled) }
    }

    suspend fun setGroupBehavior(groupId: String, behavior: SelectedCourseBehavior) {
        updateGroup(groupId) { it.copy(selectedCourseBehavior = behavior) }
    }

    suspend fun removeTrackedGroup(groupId: String) {
        context.dataStore.edit { preferences ->
            val currentList = readCourses(preferences)
            currentList.removeAll { it.trackingGroupId == groupId }
            writeCourses(preferences, currentList)
        }
    }

    suspend fun moveCourseWithinGroup(groupId: String, courseId: String, direction: Int) {
        if (direction == 0) return
        context.dataStore.edit { preferences ->
            val currentList = readCourses(preferences)
            val orderedGroup = currentList
                .withIndex()
                .filter { it.value.trackingGroupId == groupId }
                .sortedWith(compareBy<IndexedValue<TrackedCourse>> { it.value.priority ?: it.index }.thenBy { it.index })
                .map { it.value }
                .toMutableList()
            val from = orderedGroup.indexOfFirst { it.courseId == courseId }
            val to = (from + direction.coerceIn(-1, 1)).coerceIn(0, orderedGroup.lastIndex)
            if (from < 0 || from == to) return@edit
            val moved = orderedGroup.removeAt(from)
            orderedGroup.add(to, moved)
            val priorities = orderedGroup.mapIndexed { priority, item -> item.courseId to priority }.toMap()
            for (index in currentList.indices) {
                priorities[currentList[index].courseId]?.let { priority ->
                    currentList[index] = currentList[index].copy(priority = priority)
                }
            }
            writeCourses(preferences, currentList)
        }
    }

    /**
     * 切换单个课程的自动选课开关
     */
    suspend fun toggleAutoSelectEnabled(courseId: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[TRACKED_COURSES_KEY] ?: "[]"
            val currentList = safeParseTrackedCourses(jsonString)

            val index = currentList.indexOfFirst { it.courseId == courseId }
            if (index != -1) {
                val item = currentList[index]
                currentList[index] = item.copy(
                    autoSelectEnabled = enabled
                )
                preferences[TRACKED_COURSES_KEY] = gson.toJson(currentList)
            }
        }
    }

    /**
     * 清除单个课程的选课反馈信息
     */
    suspend fun clearSelectMessage(courseId: String) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[TRACKED_COURSES_KEY] ?: "[]"
            val currentList = safeParseTrackedCourses(jsonString)

            val index = currentList.indexOfFirst { it.courseId == courseId }
            if (index != -1) {
                val item = currentList[index]
                currentList[index] = item.copy(
                    lastSelectMessage = ""
                )
                preferences[TRACKED_COURSES_KEY] = gson.toJson(currentList)
            }
        }
    }

    /**
     * 清除所有追踪课程数据
     */
    suspend fun clearAllCourses() {
        context.dataStore.edit { preferences ->
            preferences[TRACKED_COURSES_KEY] = "[]"
        }
    }

    private fun readCourses(preferences: Preferences): MutableList<TrackedCourse> =
        safeParseTrackedCourses(preferences[TRACKED_COURSES_KEY] ?: "[]")

    private fun writeCourses(preferences: androidx.datastore.preferences.core.MutablePreferences, courses: List<TrackedCourse>) {
        preferences[TRACKED_COURSES_KEY] = gson.toJson(courses)
    }

    private fun groupTemplate(courses: List<TrackedCourse>, courseKey: String?): TrackedCourse? =
        courseKey?.let { key -> courses.firstOrNull { it.courseKey == key } }

    private fun nextPriority(courses: List<TrackedCourse>, courseKey: String?): Int {
        if (courseKey == null) return 0
        return (courses.filter { it.courseKey == courseKey }.mapNotNull { it.priority }.maxOrNull() ?: -1) + 1
    }

    private suspend fun updateGroup(groupId: String, transform: (TrackedCourse) -> TrackedCourse) {
        context.dataStore.edit { preferences ->
            val currentList = readCourses(preferences)
            var changed = false
            for (index in currentList.indices) {
                if (currentList[index].trackingGroupId == groupId) {
                    currentList[index] = transform(currentList[index])
                    changed = true
                }
            }
            if (changed) writeCourses(preferences, currentList)
        }
    }
}
