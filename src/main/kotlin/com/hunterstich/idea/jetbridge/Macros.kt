package com.hunterstich.idea.jetbridge

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor

fun String.expandInlineMacros(editor: Editor, providerPath: String): String {
    var result = this
    if (result.contains("@this")) {
        var value = ""
        ApplicationManager.getApplication().runReadAction {
            val filePath = getRelativePath(editor.virtualFile.path, providerPath)
            value = "@$filePath"
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

    if (result.contains("@file")) {
        ApplicationManager.getApplication().runReadAction {
            val filePath = getRelativePath(editor.virtualFile.path, providerPath)
            result = result.replace("@file", "@$filePath")
        }
    }

    // TODO: Expand @buffer
    // if (result.contains("@buffer")) {

    // }

    return result
}

fun String.cleanAllMacros(): String {
    val result = this.replace("""@\w+""".toRegex(), "").trim()
    return result
}

private fun getRelativePath(fullPath: String, providerPath: String): String {
    var relativePath = fullPath
    if (fullPath.startsWith(providerPath)) {
        relativePath = fullPath.removePrefix(providerPath)
        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            relativePath = relativePath.removePrefix("/").removePrefix("\\")
        }
    }
    return relativePath
}
