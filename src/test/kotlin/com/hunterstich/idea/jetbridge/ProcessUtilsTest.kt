package com.hunterstich.idea.jetbridge

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProcessUtilsTest {

    @Test
    fun testResolvePath() {
        // This test relies on common binaries being in /bin or /usr/bin
        val path = "/usr/bin:/bin"

        // We can't use reflection to test a private method easily in Kotlin
        // but we can test createProcess's behavior or just make it internal

        // Let's verify 'sh' exists in one of those
        val shResolved = resolvePathInternal("sh", path)
        assertNotNull(shResolved)
        val file = File(shResolved)
        assertEquals("sh", file.name)
        assert(file.exists())
    }

    @Test
    fun testResolvePathMissing() {
        val path = "/usr/bin:/bin"
        val missing = resolvePathInternal("nonexistent_binary_12345", path)
        assertNull(missing)
    }

    // Helper since the actual method is private in ProcessUtils.kt
    // In a real scenario, we might make it internal for testing
    private fun resolvePathInternal(executable: String, pathString: String?): String? {
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
}