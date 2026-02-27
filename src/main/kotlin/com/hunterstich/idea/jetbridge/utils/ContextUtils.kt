package com.hunterstich.idea.jetbridge.utils

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor

data class ContextSnapshot(
    val projectPath: String?,
    val filePath: String?,
    val hasSelection: Boolean,
    val lineStart: Int,
    val columnStart: Int,
    val lineEnd: Int,
    val columnEnd: Int,
    val selectionDesc: String
)

internal fun captureContextSnapshot(editor: Editor, event: AnActionEvent): ContextSnapshot {
    val selectionModel = editor.selectionModel

    val hasSelection = selectionModel.hasSelection()

    var lineStart: Int
    var columnStart: Int
    var lineEnd: Int
    var columnEnd: Int
    var selectionDesc: String

    if (hasSelection) {
        val startPos = editor.offsetToLogicalPosition(
            selectionModel.selectionStart.coerceIn(0, editor.document.textLength)
        )
        val endPos = editor.offsetToLogicalPosition(
            selectionModel.selectionEnd.coerceIn(0, editor.document.textLength)
        )
        lineStart = startPos.line + 1
        columnStart = startPos.column + 1
        lineEnd = endPos.line + 1
        columnEnd = endPos.column + 1
        selectionDesc = "L${lineStart}:C${columnStart}-L${lineEnd}:C${columnEnd}"
    } else {
        val caretPos = editor.offsetToLogicalPosition(
            editor.caretModel.primaryCaret.offset.coerceIn(0, editor.document.textLength)
        )
        lineStart = caretPos.line + 1
        columnStart = caretPos.column + 1
        lineEnd = caretPos.line + 1
        columnEnd = caretPos.column + 1
        selectionDesc = "L${lineStart}"
    }

    return ContextSnapshot(
        event.project?.basePath,
        filePath = editor.virtualFile?.path,
        hasSelection,
        lineStart,
        columnStart,
        lineEnd,
        columnEnd,
        selectionDesc
    )
}
