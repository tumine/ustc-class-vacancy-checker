package com.ustc.vacancychecker.data.model

/** 拖动期间使用的纯内存换序规则；允许同一次手势中反复正向、反向换位。 */
object CourseOrderEditor {
    fun <T> move(items: List<T>, fromIndex: Int, direction: Int): List<T> {
        if (fromIndex !in items.indices || direction == 0) return items
        val targetIndex = (fromIndex + direction.coerceIn(-1, 1)).coerceIn(items.indices)
        if (fromIndex == targetIndex) return items
        return items.toMutableList().apply {
            add(targetIndex, removeAt(fromIndex))
        }
    }
}
