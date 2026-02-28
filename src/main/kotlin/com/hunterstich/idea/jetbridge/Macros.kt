package com.hunterstich.idea.jetbridge

import com.hunterstich.idea.jetbridge.utils.ContextSnapshot
import com.hunterstich.idea.jetbridge.utils.captureContextSnapshot
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor

internal val allMacros = listOf(
    "@this",
    "@these",
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

fun String.expandInlineMacros(
    providerPath: String,
    snapshot: ContextSnapshot
): String {
    var result = this
    if ((result.contains("@this") || result.contains("@these")) && snapshot.filePath != null) {
        var value = ""
        ApplicationManager.getApplication().runReadAction {
            val filePath = getRelativePath(snapshot.filePath, providerPath)
            value = "@$filePath ${snapshot.selectionDesc}"
        }

        result = result.replace("@this", value)
        result = result.replace("@these", value)
    }

    if (result.contains("@file") && snapshot.filePath != null) {
        ApplicationManager.getApplication().runReadAction {
            val filePath = getRelativePath(snapshot.filePath, providerPath)
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
    var relativePath = fullPath
    if (fullPath.startsWith(providerPath)) {
        relativePath = fullPath.removePrefix(providerPath)
        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            relativePath = relativePath.removePrefix("/").removePrefix("\\")
        }
    }
    return relativePath
}
