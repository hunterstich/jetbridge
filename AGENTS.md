# AGENTS.md - jetbridge IntelliJ plugin

## Project overview

jetbridge is an IntelliJ IDEA / Android Studio plugin written in **pure Kotlin** that
provides IDE actions for interacting with agentic coding harnesses (OpenCode, gemini-cli) 
directly from the editor. 

- **Plugin ID:** `com.hunterstich.idea.jetbridge`
- **Root package:** `com.hunterstich.idea.jetbridge`
- **Build system:** Gradle 9.0.0 with Kotlin DSL
- **Kotlin:** 2.3.0 (language version 2.0)
- **JVM target:** 17
- **IntelliJ Platform:** 2025.1 (Community)
- **Serialization:** kotlinx-serialization-json 1.7.3
- **Test framework:** JUnit 4 + kotlin-test

## Build / lint / test commands

```bash
# Build the plugin (produces ZIP in build/distributions/)
./gradlew buildPlugin

# Run a sandboxed IDE with the plugin installed
./gradlew runIde

# Compile only (fast check for errors)
./gradlew compileKotlin

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.hunterstich.idea.jetbridge.OpenCodeProviderTest"

# Run a single test method
./gradlew test --tests "com.hunterstich.idea.jetbridge.OpenCodeProviderTest.regexLsofPortAndIp"

# Clean build
./gradlew clean buildPlugin

# Verify plugin compatibility
./gradlew verifyPlugin
```

### Code structure
* `src/main/kotlin/com/hunterstich/idea/jetbridge/provider` Should be a pure kotlin, portable 
  with no dependencies on the intellij platform

### Imports

- No wildcard imports; import each symbol individually.
- Ordering: project imports, IntelliJ platform, kotlinx/kotlin stdlib, java stdlib.
- Alphabetically sorted within each group (no blank-line separators between groups).

### Types and nullability

- Use `val` by default; `var` only when mutation is necessary.
- Nullable types (`?`) used deliberately with safe calls (`?.`), elvis (`?:`), and
  `!!` only when null has been ruled out by prior logic.
- Null-guard early returns: `val x = expr ?: return`.
- Kotlin `Result<T>` with `runCatching {}` for functions that can fail without throwing.
- Explicit type annotations on properties when not obvious from RHS.
- kotlinx-serialization `@Serializable` on all data classes that go over the wire.
- `Json { ignoreUnknownKeys = true }` for lenient deserialization.

### Error handling

- `runCatching { ... }` returning `Result<T>` for fallible operations; callers use
  `.getOrNull()` to safely extract values.
- `Result.failure(Throwable("descriptive message"))` for explicit failure construction.
- `try/catch(e: Exception)` at coroutine boundaries; emit `ProviderEvent.Error(...)` via
  the `Bus` to surface errors to the user through IDE notifications.
- `e.printStackTrace()` alongside Bus emissions for debugging.
- Never silently swallow errors.

### Async patterns

- `CoroutineScope(Dispatchers.IO)` for background work.
- `scope.launch { ... }` to fire async operations.
- `withContext(Dispatchers.Main)` for UI thread work (notifications, dialogs).
- `SharedFlow` / `MutableSharedFlow` for event bus communication.
- `ProcessBuilder` for system command execution (`pgrep`, `lsof`), reading output
  via `.inputStream.bufferedReader().lines()`.

### Code organization

- One primary class per file; file named after the class.
- Multiple small related classes in one file when tightly coupled (e.g., `Provider`
  interface + `ProviderEvent` sealed class + `Bus` object in `Provider.kt`).
- Private data classes scoped to the file for implementation details.
- File-level `private val` / `private fun` for module-private utilities.
- Extension functions defined at file level.
- No companion objects; use top-level `object` declarations for singletons.

### Comments and documentation

- KDoc (`/** */`) for class-level and non-obvious method documentation.
- Inline `//` comments for contextual explanations.
- `TODO:` comments for unfinished work.
- Commented-out code retained for reference during early development.

### Testing

- Test class naming: `{SubjectClass}Test` (e.g., `OpenCodeProviderTest`).
- Annotations: `@Test` from JUnit 4.
- Assertions: `assertEquals` from `kotlin.test`.
- Tests are plain unit tests; no IntelliJ `BasePlatformTestCase` integration tests yet.

### Scope functions

- `apply { }` for inline configuration of newly created objects.
- `also { }` for side effects.
- Default parameter values preferred over method overloads.
- `when` expressions for exhaustive matching on sealed classes and string conditions.
- String templates (`"$var"` / `"${expr}"`) over concatenation.
- Inline `.toRegex()` on string literals for regex patterns.
