package com.ustc.vacancychecker.data.model

/** “单课换班”表格中一行课程的席位快照。 */
data class AdjustmentCourseSnapshot(
    val classCode: String,
    val rawSeatText: String,
    val selectedCount: Int?,
    val selectionLimit: Int?,
    val classroomCapacity: Int?,
    val pendingCount: Int?,
    val hasApplyButton: Boolean,
    val parseError: String? = null
) {
    val isValid: Boolean
        get() = parseError.isNullOrBlank() &&
            listOf(selectedCount, selectionLimit, classroomCapacity, pendingCount)
                .all { it != null && it >= 0 }

    val effectiveVacancy: Int
        get() = if (isValid) {
            maxOf(0, classroomCapacity!! - selectedCount!! - pendingCount!!)
        } else {
            0
        }

    val isAvailable: Boolean
        get() = isValid && selectedCount!! + pendingCount!! < classroomCapacity!!
}

data class AdjustmentCourseTablePayload(
    val courses: List<AdjustmentCourseSnapshot> = emptyList(),
    val error: String? = null
)

data class AdjustmentCourseEvaluation(
    val target: AdjustmentCourseSnapshot? = null,
    val effectiveVacancies: Map<String, Int> = emptyMap(),
    val error: String? = null
)

/** 按追踪队列给出的优先级顺序，在本地统一评估换班页抓取结果。 */
object AdjustmentCourseEvaluator {
    fun evaluate(
        candidateCourseIds: List<String>,
        snapshots: List<AdjustmentCourseSnapshot>
    ): AdjustmentCourseEvaluation {
        val snapshotsByCode = snapshots.groupBy { normalizeCode(it.classCode) }
        val effectiveVacancies = linkedMapOf<String, Int>()
        val candidates = candidateCourseIds.map { candidateId ->
            val matches = snapshotsByCode[normalizeCode(candidateId)].orEmpty()
            if (matches.isEmpty()) {
                EvaluatedCandidate(candidateId, null, "换班页面未返回意向课堂 $candidateId")
            } else if (matches.size != 1) {
                EvaluatedCandidate(
                    candidateId,
                    null,
                    "换班页面中意向课堂 $candidateId 出现 ${matches.size} 次，无法唯一定位"
                )
            } else {
                val snapshot = matches.single()
                if (!snapshot.isValid) {
                    val detail = snapshot.parseError?.takeIf { it.isNotBlank() }
                        ?: "席位字段不是四个非负整数"
                    EvaluatedCandidate(
                        candidateId,
                        snapshot,
                        "意向课堂 $candidateId 的席位信息无法解析：$detail；原始值=${snapshot.rawSeatText}"
                    )
                } else {
                    effectiveVacancies[candidateId] = snapshot.effectiveVacancy
                    EvaluatedCandidate(candidateId, snapshot, null)
                }
            }
        }

        for (candidate in candidates) {
            if (candidate.error != null) {
                return AdjustmentCourseEvaluation(
                    effectiveVacancies = effectiveVacancies,
                    error = candidate.error
                )
            }
            val snapshot = candidate.snapshot ?: continue
            if (!snapshot.isAvailable) continue
            if (!snapshot.hasApplyButton) {
                return AdjustmentCourseEvaluation(
                    effectiveVacancies = effectiveVacancies,
                    error = "意向课堂 ${candidate.courseId} 容量允许换班，但未找到申请按钮"
                )
            }
            return AdjustmentCourseEvaluation(
                target = snapshot,
                effectiveVacancies = effectiveVacancies
            )
        }
        return AdjustmentCourseEvaluation(effectiveVacancies = effectiveVacancies)
    }

    private data class EvaluatedCandidate(
        val courseId: String,
        val snapshot: AdjustmentCourseSnapshot?,
        val error: String?
    )

    private fun normalizeCode(code: String): String = code.trim().uppercase()
}
