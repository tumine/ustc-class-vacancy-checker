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
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "course_tracking")

@Singleton
class CourseRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    
    companion object {
        private val TRACKED_COURSES_KEY = stringPreferencesKey("tracked_courses")
    }

    val trackedCoursesFlow: Flow<List<TrackedCourse>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[TRACKED_COURSES_KEY] ?: "[]"
        val type = object : TypeToken<List<TrackedCourse>>() {}.type
        gson.fromJson(jsonString, type)
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

    suspend fun updateCourseStatus(courseId: String, vacancy: Int, isMonitoring: Boolean? = null) {
        context.dataStore.edit { preferences ->
            val jsonString = preferences[TRACKED_COURSES_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<TrackedCourse>>() {}.type
            val currentList: MutableList<TrackedCourse> = gson.fromJson(jsonString, type)
            
            val index = currentList.indexOfFirst { it.courseId == courseId }
            if (index != -1) {
                val item = currentList[index]
                currentList[index] = item.copy(
                    vacancy = vacancy,
                    lastCheckTime = System.currentTimeMillis(),
                    isMonitoring = isMonitoring ?: item.isMonitoring
                )
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
