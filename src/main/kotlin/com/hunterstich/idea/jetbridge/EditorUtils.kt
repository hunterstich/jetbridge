package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.core.ContextSnapshot
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor

/**
 * Helper to collect all context from the current editor and action event as required by
 * [com.hunterstich.idea.jetbridge.core.Provider].
 */
internal fun Editor.captureContextSnapshot(event: AnActionEvent): ContextSnapshot {
    val selectionModel = selectionModel

    val hasSelection = selectionModel.hasSelection()

    var lineStart: Int
    var columnStart: Int
    var lineEnd: Int
    var columnEnd: Int

    if (hasSelection) {
        val startPos = offsetToLogicalPosition(
            selectionModel.selectionStart.coerceIn(0, document.textLength)
        )
        val endPos = offsetToLogicalPosition(
            selectionModel.selectionEnd.coerceIn(0, document.textLength)
        )
        lineStart = startPos.line + 1
        columnStart = startPos.column + 1
        lineEnd = endPos.line + 1
        columnEnd = endPos.column + 1
    } else {
        val caretPos = offsetToLogicalPosition(
            caretModel.primaryCaret.offset.coerceIn(0, document.textLength)
        )
        lineStart = caretPos.line + 1
        columnStart = caretPos.column + 1
        lineEnd = caretPos.line + 1
        columnEnd = caretPos.column + 1
    }

    return ContextSnapshot(
        event.project?.basePath,
        filePath = virtualFile?.path,
        hasSelection,
        lineStart,
        columnStart,
        lineEnd,
        columnEnd,
    )
}
