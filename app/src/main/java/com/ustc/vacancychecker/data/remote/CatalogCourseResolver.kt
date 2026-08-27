package com.ustc.vacancychecker.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class CatalogCourseMetadata(
    val classCode: String,
    val courseKey: String,
    val courseNumber: String? = null,
    val courseName: String? = null
)

/**
 * 通过公开课程目录按课堂号查询权威 courseId。
 * 不对课堂号做任何截断或格式推断；查不到时由调用方保留为未分组课堂。
 */
@Singleton
class CatalogCourseResolver @Inject constructor(
    private val client: OkHttpClient
) {
    companion object {
        private const val BASE_URL = "https://catalog.ustc.edu.cn"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    suspend fun resolve(classCodes: Collection<String>): Map<String, CatalogCourseMetadata> =
        withContext(Dispatchers.IO) {
            val unresolved = classCodes.filter { it.isNotBlank() }.distinct().toMutableSet()
            if (unresolved.isEmpty()) return@withContext emptyMap()

            val semesters = runCatching { loadCandidateSemesters() }.getOrElse { return@withContext emptyMap() }
            val resolved = linkedMapOf<String, CatalogCourseMetadata>()
            for (semesterId in semesters) {
                if (unresolved.isEmpty()) break
                val batch = runCatching { loadLessonInfo(unresolved, semesterId) }.getOrDefault(emptyList())
                for (metadata in batch) {
                    if (metadata.classCode in unresolved) {
                        resolved[metadata.classCode] = metadata
                        unresolved.remove(metadata.classCode)
                    }
                }
            }
            resolved
        }

    private fun loadCandidateSemesters(): List<Long> {
        val request = Request.Builder().url("$BASE_URL/api/teach/semester/list").build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("课程目录学期请求失败: HTTP ${response.code}")
            response.body?.string() ?: error("课程目录学期响应为空")
        }
        val today = LocalDate.now()
        return JSONArray(body).let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val start = runCatching { LocalDate.parse(item.getString("start")) }.getOrNull() ?: continue
                    val end = runCatching { LocalDate.parse(item.getString("end")) }.getOrNull() ?: continue
                    if (end >= today.minusYears(1) && start <= today.plusMonths(6)) {
                        val distance = when {
                            today < start -> ChronoUnit.DAYS.between(today, start)
                            today > end -> ChronoUnit.DAYS.between(end, today)
                            else -> 0L
                        }
                        add(Semester(item.getLong("id"), start, distance, item.optBoolean("isLast")))
                    }
                }
            }
        }.sortedWith(
            compareBy<Semester> { it.distanceDays }
                .thenByDescending { it.start }
                .thenByDescending { it.isLast }
        ).take(6).map { it.id }
    }

    private fun loadLessonInfo(classCodes: Collection<String>, semesterId: Long): List<CatalogCourseMetadata> {
        val requestJson = JSONObject()
            .put("codes", JSONArray(classCodes.toList()))
            .put("semester", semesterId)
            .toString()
        val request = Request.Builder()
            .url("$BASE_URL/api/teach/lesson/infos")
            .post(requestJson.toRequestBody(JSON))
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("课程目录课堂请求失败: HTTP ${response.code}")
            response.body?.string() ?: error("课程目录课堂响应为空")
        }
        val array = when {
            body.trimStart().startsWith("[") -> JSONArray(body)
            else -> JSONObject(body).optJSONArray("value") ?: JSONArray()
        }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val classCode = item.optString("code").trim()
                val authorityId = item.optLong("courseId", -1L)
                if (classCode.isBlank() || authorityId <= 0L) continue
                val name = item.optJSONObject("name")?.optString("cn")?.takeIf { it.isNotBlank() }
                add(
                    CatalogCourseMetadata(
                        classCode = classCode,
                        courseKey = "catalog-course:$authorityId",
                        courseName = name
                    )
                )
            }
        }
    }

    private data class Semester(
        val id: Long,
        val start: LocalDate,
        val distanceDays: Long,
        val isLast: Boolean
    )
}
