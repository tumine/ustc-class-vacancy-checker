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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

import androidx.datastore.preferences.core.intPreferencesKey

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "course_tracking")

@Singleton
class CourseRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    
    companion object {
        private val TRACKED_COURSES_KEY = stringPreferencesKey("tracked_courses")
        private val MONITORING_INTERVAL_KEY = intPreferencesKey("monitoring_interval")
    }

    val trackedCoursesFlow: Flow<List<TrackedCourse>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[TRACKED_COURSES_KEY] ?: "[]"
        val type = object : TypeToken<List<TrackedCourse>>() {}.type
        try {
            gson.fromJson(jsonString, type)
        } catch (e: com.google.gson.JsonSyntaxException) {
            android.util.Log.e("CourseRepository", "Failed to parse tracked courses, resetting", e)
            emptyList()
        }
    }
    val monitoringIntervalFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MONITORING_INTERVAL_KEY] ?: 15
    }.distinctUntilChanged()

    suspend fun updateMonitoringInterval(intervalMinutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[MONITORING_INTERVAL_KEY] = intervalMinutes
        }
    }

    suspend fun getTrackedCourses(): List<TrackedCourse> {
        return trackedCoursesFlow.first()
    }

    suspend fun addTrackedCourses(courses: List<TrackedCourse>) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[TRACKED_COURSES_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<TrackedCourse>>() {}.type
            val currentList: MutableList<TrackedCourse> = gson.fromJson(jsonString, type)
            
            for (newCourse in courses) {
                val index = currentList.indexOfFirst { it.courseId == newCourse.courseId }
                if (index != -1) {
                    currentList[index] = newCourse
                } else {
                    currentList.add(newCourse)
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
            val type = object : TypeToken<MutableList<TrackedCourse>>() {}.type
            val currentList: MutableList<TrackedCourse> = gson.fromJson(jsonString, type)
            
            currentList.removeAll { it.courseId == courseId }
            preferences[TRACKED_COURSES_KEY] = gson.toJson(currentList)
        }
    }

    suspend fun updateCourseStatus(courseId: String, vacancy: Int? = null, isMonitoring: Boolean? = null) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[TRACKED_COURSES_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<TrackedCourse>>() {}.type
            val currentList: MutableList<TrackedCourse> = gson.fromJson(jsonString, type)
            
            val index = currentList.indexOfFirst { it.courseId == courseId }
            if (index != -1) {
                val item = currentList[index]
                currentList[index] = item.copy(
                    vacancy = vacancy ?: item.vacancy,
                    lastCheckTime = System.currentTimeMillis(),
                    isMonitoring = isMonitoring ?: item.isMonitoring
                )
                preferences[TRACKED_COURSES_KEY] = gson.toJson(currentList)
            }
        }
    }

    suspend fun updateCheckTimeForCourses(courseIds: List<String>) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[TRACKED_COURSES_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<TrackedCourse>>() {}.type
            val currentList: MutableList<TrackedCourse> = gson.fromJson(jsonString, type)
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
            val type = object : TypeToken<MutableList<TrackedCourse>>() {}.type
            val currentList: MutableList<TrackedCourse> = gson.fromJson(jsonString, type)
            
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
}
