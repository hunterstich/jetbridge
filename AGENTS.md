# AGENTS.md - jetbridge IntelliJ plugin

## Project overview

jetbridge is an IntelliJ IDEA / Android Studio plugin written in **pure Kotlin** that
provides IDE actions for interacting with AI code assistants (OpenCode) directly from
the editor. It communicates with a running OpenCode instance via HTTP REST and SSE.

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

There is no linter or formatter configured. The project uses `kotlin.code.style=official`
in `gradle.properties`, which tells IntelliJ to use the official Kotlin code style.
There is no CI/CD pipeline.

## Project structure

```
src/main/kotlin/com/hunterstich/idea/jetbridge/
  Jetbridge.kt                  # ProjectActivity: startup, event bus listener, notifications
  JetbridgePromptAction.kt      # AnAction subclasses: PromptAction and AskAction
  JetbridgeDialog.kt            # WIP multi-line vim-enabled prompt dialog
  Macros.kt                     # String extension functions for macro expansion
  provider/
    Provider.kt                 # Provider interface, ProviderEvent sealed class, Bus object
    OpenCodeProvider.kt          # HTTP + SSE communication with OpenCode
    GeminiCliProvider.kt         # Placeholder (commented-out) tmux-based provider

src/test/kotlin/com/hunterstich/idea/jetbridge/
  OpenCodeProviderTest.kt        # Unit tests

src/main/resources/META-INF/
  plugin.xml                     # Plugin descriptor (actions, extensions, dependencies)
```

## Code style guidelines

### Language and formatting

- Pure Kotlin; no Java source files.
- 4-space indentation, no tabs.
- Opening braces on the same line (K&R style).
- Trailing commas in multi-line parameter lists and collections.
- Lines up to ~120 characters; no strict enforcement.
- Single blank line between functions; double blank lines acceptable between classes.

### Naming conventions

| Element              | Style       | Example                                    |
|----------------------|-------------|---------------------------------------------|
| Classes / objects    | PascalCase  | `OpenCodeProvider`, `Bus`                   |
| Interfaces           | PascalCase, no `I` prefix | `Provider`                     |
| Sealed subclasses    | PascalCase  | `ProviderEvent.Status`                      |
| Functions            | camelCase   | `ensureConnected()`, `sendPromptAsync()`    |
| Extension functions  | camelCase   | `String.expandInlineMacros()`               |
| Variables/properties | camelCase   | `serverAddress`, `isConnected`              |
| Backing fields       | `_` prefix  | `_messages` backing `messages`              |
| Constants/top-level vals | camelCase (not SCREAMING_SNAKE) | `isDebug`          |
| Files                | PascalCase, match primary class | `OpenCodeProvider.kt`       |
| Packages             | all lowercase | `com.hunterstich.idea.jetbridge.provider` |

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
