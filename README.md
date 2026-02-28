# jetbridge ✈️ 🌉

https://github.com/user-attachments/assets/fd3bf887-76be-4bb1-a3e4-552e511e91b4


Intellij/Android Studio plugin that adds actions for interacting
with an AI agent tool like opencode or gemini-cli from the IDE editor. Inspired by 
 [opencode.nvim](https://github.com/nickjvandyke/opencode.nvim).

### Why?
* _Stay on the keyboard: Pull up a prompt dialog with an IntelliJ action or vim keybinding_
* _Prompt more precisely: '@' macros expand into context in your final prompt, invoke specific
agents, etc._

| Example                                                        | Result                                                                                                                                               |
|----------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| ```@plan How could I re-write @this in a more idiomatic way``` | (Run with OpenCode's "Plan" agent)<br>```How could I re-write @myproject/src/main/kotlin/com/example/Main.kt L15:C0-L20:C2 in a more idomatic way``` |

## Actions

| Action                          | Desc                                                                                              |
|---------------------------------|---------------------------------------------------------------------------------------------------|
| Jetbridge.PromptAction          | Open an empty input dialog in the IDE. "Ok" sends the prompt to the provider after parsing macros |
| Jetbridge.AskAction             | Open an input dialog with "@this " already added to the prompt                                    |
| Jetbridge.ConnectOpenCodeAction | Open a dialog that lets you manually select or input an opencode server's address and session     |

## Macros

| Macro       | Input                                               | Output                                                     | Desc                                                                                                                  |
|-------------|-----------------------------------------------------|------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| @this       | How does @this work?                                | How does @src/Main.kt L10:C0-L15:C4 work?                  | Expands to the current file path and the cursor or selection's line and column info                                   |
| @file       | Move data classes from @file into their own package | Move data classes from @src/Main.kt into their own package | Expands to just the file path (no line/column info)                                                                   |
| @plan       | @plan How should we refactor @this                  | How should we refactor @src/Main.kt L0?                    | @plan will run the command using the "plan" agent in OpenCode                                                         |
| @build      | @build Refactor @this                               | Refactor @src/Main.kt L0?                                  | @build will run the command using the "build" agent in OpenCode. This is the default option if no @agent is specified |
| @a:<agent>  | @a:<agent> Add tests for @this                      | Add tests for @src/Main.kt                                 | @a:<agent> runs the command using the custom agent you specify in place of <agent>                                    |


## Installation
1. Clone the repo
2. Build the plugin: `./gradlew buildPlugin`
3. Install the resulting ZIP from `build/distributions/` into your IDE through
   the "Settings > Plugins > Install plugins from disk" option
4. For IdeaVIM: Enable vim in the prompt dialog and add keybindings to your `.ideavimrc` with:
   ```vim
   set ideavimsupport+=dialog
   
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
* Select a session in the TUI. (Otherwise prompts sent from jetbridge won't show unitl the 
initial tui splash screen is gone)
* jetbridge will try to automatically connect to an opencode instance by finding the instance
running nearest to your project file's path
* You can manually connect to an instance by running `Shift + Cmd + A` and searching for 
"Jetbridge: Connect OpenCode". This lets you input a server address and select a session.
* Call jetbridge actions from your IDE and the plugin will work with the opencode instance running
in the path parent nearest your current file
* jetbridge doesn't implement any UI inside Intellij to reflect questions or followups requested 
from OpenCode. However, if a question is asked in the terminal, jetbridge will send a little IJ 
notification to let you know.

~~### Gemini-CLI~~
NOT IMPLEMENTED YET
* Start gemini-cli in a tmux session with `tmux new-session -s gemini 'gemini' (gemini-cli
doesn't have a supported way of controlling the TUI and it's being done by sending 
tmux events)

## Develop
1. Import the project
2. Run `./gradlew runIde` to launch a sandboxed version of Intellij jetbridge installed
3. Trigger actions and test


