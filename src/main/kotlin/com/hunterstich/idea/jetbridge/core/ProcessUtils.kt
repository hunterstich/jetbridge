package com.hunterstich.idea.jetbridge.core

import java.io.File

/**
 * Create a [ProcessBuilder] that inherits the environment from the [ConfigStore].
 *
 * This is necessary because on macOS, GUI applications do not inherit the shell's environment
 * (like PATH) when launched. [ConfigStore.config.env] is expected to provide
 * the correct environment (e.g. via EnvironmentUtil in the Intellij platform).
 *
 * We also manually resolve the absolute path of the command if it's not provided,
 * as [ProcessBuilder] does not always reliably use the injected PATH for finding
 * the executable.
 */
fun createProcess(vararg command: String): ProcessBuilder {
    if (command.isEmpty()) {
        return ProcessBuilder(*command)
    }

    val env = ConfigStore.config.env
    val executable = command[0]
    val resolvedCommand = if (!executable.startsWith("/") && !executable.startsWith("./")) {
        resolvePath(executable, env["PATH"]) ?: executable
    } else {
        executable
    }

    val fullCommand = listOf(resolvedCommand) + command.drop(1)
    val pb = ProcessBuilder(fullCommand)
    pb.environment().putAll(env)
    return pb
}

/**
 * Manually resolve the absolute path of an executable using a provided PATH string.
 */
private fun resolvePath(executable: String, pathString: String?): String? {
    if (pathString == null) return null
    
    val paths = pathString.split(File.pathSeparator)
    for (path in paths) {
        val file = File(path, executable)
        if (file.exists() && file.canExecute()) {
            return file.absolutePath
        }
    }
    return null
}
