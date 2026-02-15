package com.hunterstich.idea.jetbridge

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor

fun String.expandInlineMacros(editor: Editor): String {
    var result = this
    if (result.contains("@this")) {
        var value = ""
        ApplicationManager.getApplication().runReadAction {
            value = "@${editor.virtualFile.path}"
            val caret = editor.caretModel.primaryCaret
            if (caret.hasSelection()) {
                val startPos = caret.selectionStartPosition
                val endPos = caret.selectionEndPosition
                value += " L${startPos.line + 1}:C${startPos.column}-L${endPos.line + 1}:C${endPos.column}"
            } else {
                value += " L${caret.selectionStartPosition.line}"
            }
        }

        result = result.replace("@this", value)
    }

    // TODO: Expand @buffer
    // if (result.contains("@buffer")) {
    //     editor.text()
    //     val bufferText = editor.text().toString()
    //     result = result.replace("@buffer", bufferText)
    // }

    return result
}

fun String.cleanAllMacros(): String {
    val result = this.replace("""@\w+""".toRegex(), "").trim()
    return result
}