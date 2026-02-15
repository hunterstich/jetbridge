# jetbridge ✈️ 🌉
Intellij/Android Studio plugin that adds actions for interacting
with an AI code assistant like opencode or gemini-cli from the IDE editor. Inspired by 
 [opencode.nvim](https://github.com/nickjvandyke/opencode.nvim).

* **Don't leave the editor**: Pull up a prompt dialog with an IntelliJ action or vim keybinding
* **Prompt more precisely**: '@' macros expand inline in your final prompt or trigger configurations when sent 

| Example                                                | Result                                                                                                           |
|--------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| @plan How could re-write @this in a more idiomatic way | (Run in OpenCode's "Plan" mode) <br>How could we re-write @myproject/src/main/kotlin/com/example/Main.kt L15:C0-L20:C2 in a more idomatic way 

## Macros

| Macro      | Input                              | Output                                    | Desc                                                                                                                  |
|------------|------------------------------------|-------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| @this      | How does @this work?               | How does @src/Main.kt L10:C0-L15:C4 work? | Expands to the current file path and the cursor or selected text's line numbers and columns                           |
| @plan      | @plan How should we refactor @this | How should we refactor @src/Main.kt L0?   | @plan will run the command using the "plan" agent in OpenCode                                                         
| @build     | @build Refactor @this              | Refactor @src/Main.kt L0?                 | @build will run the command using the "build" agent in OpenCode. This is the default option if no @agent is specified 
| @a:<agent> | @a:<agent> Add tests for @this     | Add tests for @src/Main.kt                | @a:<agent> runs the command using the custom agent you specify in place of <agent>                                    

Only opencode is currently implemented.

## Installation
1. Clone the repo
2. Build the plugin: `./gradlew buildPlugin`
3. Install the resulting ZIP from `build/distributions/` into your IDE through
   the "Settings > Plugins > Install plugins from disk" option
4. For IdeaVIM: Add keybindings to your `.ideavimrc` with:
   ```vim
   nmap <leader>oo :action Jetbridge.PromptAction<CR>
   vmap <leader>oo :action Jetbridge.PromptAction<CR>
   nmap <leader>oa :action Jetbridge.AskAction<CR>
   vmap <leader>oa :action Jetbridge.AskAction<CR>
   ...
   ```
5. Otherwise: Set up keyboard shortcuts in the IDE to trigger jetbridge actions. To see a list 
of avaiSble actions, use `Shift + Cmd/Ctrl + A` and type "Jetbridge".
 
## Providers

### OpenCode
* Start opencode in a terminal with `opencode --port`
* Call jetbridge actions from your IDE and the plugin will work with the opencode instance running
in the path parent nearest your current file
* jetbridge doesn't implement any UI inside Intellij to reflect questions or followups requested 
from OpenCode. However, if a question is asked in the terminal, jetbridge will send a little IJ notification to let you know.

~~### Gemini-CLI~~
NOT IMPLEMENTED YET
* Start gemini-cli in a tmux session with `tmux new-session -s gemini 'gemini' (gemini-cli
doesn't have a supported way of controlling the TUI and it's being done by sending 
tmux events)

## Develop
1. Import the project
2. Run `./gradlew runIde` to launch a sandboxed version of Intellij jetbridge installed
3. Trigger actions and test


