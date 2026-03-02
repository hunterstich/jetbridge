package com.hunterstich.idea.jetbridge.core

data class ContextSnapshot(
    val projectPath: String? = null,
    val filePath: String? = null,
    val hasSelection: Boolean = false,
    val lineStart: Int = 0,
    val columnStart: Int = 0,
    val lineEnd: Int = 0,
    val columnEnd: Int = 0,
) {
    val selectionDesc: String
        get() = if (hasSelection) {
            "L${lineStart}:C${columnStart}-L${lineEnd}:C${columnEnd}"
        } else {
            "L${lineStart}"
        }
}
