package com.ustc.vacancychecker.data.model

/**
 * 选课操作结果数据类
 */
data class SelectResult(
    val success: Boolean,
    val message: String,
    val isAlreadySelected: Boolean = false
)
