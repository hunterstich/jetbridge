package com.hunterstich.idea.jetbridge

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor

internal val allMacros = listOf(
    "@this",
    "@file",
    "@buffer",
    "@dir",
    "@plan",
    "@build",
)

internal val allMacroRegex = listOf(
    // Match for any agent specifier
    """@a:\w+""".toRegex()
)

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

    // TODO: Expand @dir

    return result
}

fun String.cleanAllMacros(): String {
    var result = this
    for (macro in allMacros) {
        result = result.replace(macro, "")
    }
    for (regex in allMacroRegex) {
        result = result.replace(regex, "")
    }
    return result.trim()
}

private fun getRelativePath(fullPath: String, providerPath: String): String {
    println("trimming path. full: $fullPath, providerPath: $providerPath")
    var relativePath = fullPath
    if (fullPath.startsWith(providerPath)) {
        relativePath = fullPath.removePrefix(providerPath)
        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            relativePath = relativePath.removePrefix("/").removePrefix("\\")
        }
    }
    return relativePath
}
