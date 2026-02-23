# Checkpoint 1: JetBridge Dialog & Macro Implementation

## Project Context
**jetbridge** is an IntelliJ IDEA / Android Studio plugin written in pure Kotlin that provides IDE actions for interacting with Claude Code AI assistant directly from the editor. The codebase lives at `/Users/laptop/Code/android/jetbridge`.

### Key Project Files & Structure
- **Build:** `./gradlew compileKotlin` to compile, `./gradlew test` to run tests
- **Code style:** Kotlin, IntelliJ Platform SDK, no wildcard imports, `internal` visibility for cross-file access within the module
- **IdeaVIM:** Optional sandbox dependency (`IdeaVIM:2.24.0` via `plugins(...)` in `build.gradle.kts`), declared as optional runtime dependency in `plugin.xml`. The `plugins(...)` declaration does NOT put IdeaVIM on the compile classpath — only on the sandbox runtime. IdeaVIM classes must therefore be accessed via reflection using `vimPlugin.pluginClassLoader`.

## Work Completed

### 1. Mockito Test Infrastructure (`MacrosTest.kt`)
- Added `org.mockito.kotlin:mockito-kotlin:5.1.0` dependency to `build.gradle.kts`
- Created `setupMockEditor()` helper method in `MacrosTest.kt` with mocks for `Editor`, `VirtualFile`, `CaretModel`, and `Caret` using mockito-kotlin's `mock { }` DSL and `whenever()`
- Added `@Before` setup to mock `ApplicationManager.getApplication().runReadAction()` so `expandInlineMacros` tests work outside an IDE environment
- Completed `testRelativePath_expandsThis` test that verifies `@this` macro expansion using the mock editor
- Fixed test expectation: `@this` expands to `@README.md L0` (with the `@` prefix), not `README.md L0`

### 2. Macro Cleanup (`Macros.kt`)
- Updated `cleanAllMacros()` to iterate over the `allMacros` list and remove each known macro string, instead of using a regex. This prevents expanded paths like `@README.md` from being incorrectly stripped.
- Changed `allMacros` and `allMacroRegex` from `private` to `internal` so `JetbridgeDialog` can reference them for highlighting.
- Current macro lists:
  - `allMacros`: `@this`, `@file`, `@buffer`, `@dir`, `@plan`, `@build`
  - `allMacroRegex`: `"""@a:\w+""".toRegex()` (agent specifiers like `@a:build`)

### 3. JetbridgeDialog Implementation (`JetbridgeDialog.kt`)
This was the main body of work. Built a full `DialogWrapper` subclass replacing a TODO comment. Key features:

- **Multi-line `EditorTextField`** with `oneLineMode = false`, using `EditorFactory.getInstance().createDocument()` for the document
- **Enter → submit (OK):** Implemented via `DumbAwareAction` registered with `CustomShortcutSet.fromString("ENTER")` on `editor.contentComponent`. This runs at the IntelliJ action layer (before the editor's default `EnterAction`), so bare Enter submits while Shift+Enter falls through to insert a newline.
- **Escape handling:** See section below — this required significant investigation and a non-trivial implementation.
- **Macro highlighting:** `DocumentListener` + `MarkupModel` approach. On every document change, clears highlighters and re-scans for `allMacros` (string matches) and `allMacroRegex` (regex matches), applying `RangeHighlighter`s with bold + `DefaultLanguageHighlighterColors.LABEL` color from the editor's color scheme.
- **Editor color scheme matching:** `setFontInheritedFromLAF(false)` + `editor.backgroundColor = EditorColorsManager.getInstance().globalScheme.defaultBackground` so the dialog editor matches the IDE's main editor font and background.
- **IdeaVIM support:** Works automatically when user has `set ideavimsupport+=dialog` in `.ideavimrc`. Required adding optional plugin dependency (see below).

### 4. IdeaVIM Optional Dependency
- Added `<depends optional="true" config-file="jetbridge-ideavim.xml">IdeaVIM</depends>` to `plugin.xml`
- Created `src/main/resources/META-INF/jetbridge-ideavim.xml` (minimal empty descriptor)
- This was critical: without it, jetbridge's `PluginClassLoader` had no visibility into IdeaVIM's classes due to IntelliJ's per-plugin classloader isolation. The `ClassNotFoundException` for `VimPlugin` was the symptom.

### 5. Wired Dialog into Actions (`JetbridgeActions.kt`)
- Updated `captureDialogInput()` to create a `JetbridgeDialog` instead of `Messages.InputDialog`
- Changed signature to accept `project: Project?` parameter
- Updated `JetbridgePromptAction` and `JetbridgeAskAction` to pass `event.project`
- Cleaned up unused imports (removed `ComboBox`, `Messages`, `KeyAdapter`, `KeyEvent`, `DefaultComboBoxModel`, `JTextComponent`, etc.)

### 6. IdeaVIM Escape Handling (`JetbridgeDialog.kt`)

This required deep investigation into the IntelliJ key event dispatch pipeline.

#### The Problem
When IdeaVIM is active with `ideavimsupport+=dialog`, pressing Escape should:
1. Switch insert mode → normal mode (first press)
2. Close the dialog (second press, from normal mode)

The naive assumption — that IdeaVIM would consume Escape in insert mode and let it bubble up to `DialogWrapper` in normal mode — proved false due to the layered nature of key dispatch.

#### Key Dispatch Architecture (relevant findings)
- `IdeEventQueue.dispatchKeyEvent` calls `IdeKeyEventDispatcher.dispatchKeyEvent`. If any action is found and fired for the keystroke, it returns `true` and `IdeEventQueue` marks the underlying `KeyEvent` as consumed.
- `DialogWrapper.registerKeyboardShortcuts` registers Escape as a `WHEN_IN_FOCUSED_WINDOW` Swing input map binding. **Swing input maps do not fire for consumed `KeyEvent`s.** So this path is dead when the editor is focused — `IdeKeyEventDispatcher` always finds and fires `ACTION_EDITOR_ESCAPE` for the editor, consuming the event before Swing's input map runs.
- `DialogWrapperPeerImpl.show()` independently registers an `AnCancelAction` (a `DumbAware` `AnAction`) for `ACTION_EDITOR_ESCAPE`. However, `AnCancelAction.update()` disables itself for non-tree/table components, so it is always disabled for our `EditorTextField`.
- IdeaVIM registers `VimEscHandler` (an `OctopusHandler`/`EditorActionHandler`) for `ACTION_EDITOR_ESCAPE`. `VimActionsPromoter` runs it before other handlers.
  - In **insert mode**: `VimEscHandler.isHandlerEnabled` returns `true` → `KeyHandler.handleKey(Escape)` is called → IdeaVIM switches to normal mode. `IdeKeyEventDispatcher` returns `true`, event consumed, Swing input map never fires. The `DialogWrapper` cancel is never triggered. This is the desired behaviour.
  - In **normal mode**: `VimEscHandler.isHandlerEnabled` returns `false` (`vimStateNeedsToHandleEscape` = `!inNormalMode || hasKeys` evaluates to `false`). `OctopusHandler.doExecute` falls through to `nextHandler`. The platform default Escape handler fires, `IdeKeyEventDispatcher` still returns `true`, event still consumed, Swing input map still never fires.

#### The Fix: Two-part approach

**Part 1 — `DumbAwareAction` for Escape on `editor.contentComponent`** (mirrors the Enter action pattern):
Registered via `escapeAction.registerCustomShortcutSet(CustomShortcutSet.fromString("ESCAPE"), editor.contentComponent, disposable)` inside `addSettingsProvider`. This fires at the IntelliJ action layer, the same layer as `OctopusHandler`, so it reliably receives Escape regardless of whether `IdeEventQueue` has consumed the `KeyEvent`.

**Part 2 — `createCancelAction()` override** (fallback for when the editor doesn't have focus, e.g. a dialog button is focused):
Kept as a secondary path. When the editor is not focused, the `DumbAwareAction` won't fire but the Swing input map will.

Both call `tryHandleVimEscape()`, which:
- Returns `false` (close dialog) if IdeaVIM is not installed or the editor has no `VimEditor`
- Checks `VimEditor.getMode()` via reflection: if `Mode.NORMAL` instance → returns `false` (close dialog)
- Otherwise (INSERT, VISUAL, etc.) → calls `KeyHandler.getInstance().handleKey(Escape)` directly through IdeaVIM's own KeyHandler to switch modes, returns `true` (keep dialog open)

#### Critical pitfall: `insertMode` vs `getMode()`
Do NOT use `VimEditor.getInsertMode()` to check whether IdeaVIM is in insert mode. It delegates to `EditorEx.isInsertMode` — an IntelliJ cursor-shape flag that is set `true` when entering INSERT mode but is **never reset to `false` when exiting**. It remains `true` for the lifetime of the editor after the first insert-mode entry. Use `VimEditor.getMode()` and check `instanceof Mode$NORMAL` instead.

#### Reflection is required
IdeaVIM classes live in a separate plugin classloader and are not on the compile classpath (only in the sandbox via `plugins(...)`). All IdeaVIM class access must go through `vimPlugin.pluginClassLoader`. Platform classes (`com.intellij.openapi.editor.Editor`) use `Class.forName()` from the platform classloader — do not use the IdeaVIM classloader for these.

The reflection call chain in `tryHandleVimEscape()`:
1. `IjVimEditorKt.getVim(Editor)` → `VimEditor`
2. `VimEditor.getMode()` → check `instanceof Mode$NORMAL`
3. `VimInjectorKt.getInjector().executionContextManager.getEditorExecutionContext(vim)` → `ExecutionContext`
4. `KeyHandler.getInstance().getKeyHandlerState()` → `KeyHandlerState`
5. `KeyHandler.getInstance().handleKey(vim, VK_ESCAPE, context, keyHandlerState)`

All wrapped in `try/catch(Exception)` — returns `false` on any reflection failure so the dialog closes normally.

## Files Modified (Final State)

| File | Key Changes |
|---|---|
| `build.gradle.kts` | Added `testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")` |
| `src/main/resources/META-INF/plugin.xml` | Added optional IdeaVIM dependency |
| `src/main/resources/META-INF/jetbridge-ideavim.xml` | New file — empty descriptor for optional dep |
| `src/main/kotlin/.../JetbridgeDialog.kt` | Full implementation; Escape handling via `DumbAwareAction` + `tryHandleVimEscape()` reflection |
| `src/main/kotlin/.../JetbridgeActions.kt` | Updated `captureDialogInput()` to use `JetbridgeDialog`, cleaned imports |
| `src/main/kotlin/.../Macros.kt` | `allMacros`/`allMacroRegex` changed to `internal`, `cleanAllMacros()` rewritten |
| `src/test/kotlin/.../MacrosTest.kt` | Added mockito mocks, `@Before`/`@After`, `setupMockEditor()`, completed tests |

## Key Technical Decisions & Why

1. **`DumbAwareAction` + `CustomShortcutSet` for Enter and Escape** instead of AWT `KeyListener`: IntelliJ's action system processes keys before AWT listeners. With `oneLineMode = false`, the editor's default `EnterAction` would consume Enter before a `KeyListener` could intercept it. The `AnAction` approach runs at the same layer and takes priority. The same applies to Escape — the action layer is the only reliable intercept point.

2. **Reflection for IdeaVIM classes**: `plugins(...)` in `build.gradle.kts` puts IdeaVIM only in the sandbox, not on the compile classpath. All IdeaVIM interaction goes through `vimPlugin.pluginClassLoader`. This is also the right design for an optional dependency — the code compiles and functions without IdeaVIM present.

3. **`oneLineMode = false`** is essential for: (a) Shift+Enter newlines (the `EnterAction` handler is completely disabled in one-line mode), (b) IdeaVIM activation (IdeaVIM disables itself for single-line editors by default).

4. **`VimEditor.getMode()` not `getInsertMode()`**: `insertMode` is an IntelliJ cursor-shape flag that is never reset on vim mode exit. `getMode()` returns the canonical sealed class instance and is always accurate.

## What Could Be Done Next

- **Remove the unused `import com.maddyhome.idea.vim.api.injector`** from `Jetbridge.kt` (line 10) — it's a leftover from experimentation
- **Implement remaining macro expansions**: `@buffer` and `@dir` are still TODO in `Macros.kt`
- **Add keyboard shortcuts** for the actions in `plugin.xml` — currently none are defined, users must use action search
- **Consider `@plan` and `@build` expansion** — currently these are only extracted as agent specifiers in `OpenCodeProvider.extractAgent()`, not expanded
