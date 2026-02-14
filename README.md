# jetbridge
Intellij/Android Studio extension that lets you interact 
with an AI code assistant like opencode or gemini-cli. Inspired by 
 [opencode.nvim](https://github.com/nickjvandyke/opencode.nvim).

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
of available actions, use <Shift><cmd/ctrl><A> and type "Jetbridge".
 
## Providers

### OpenCode
* Start opencode in a terminal with `opencode --port`
* When in the editor, use keybidnings to interact with opencode running
in your terminal

### Gemini-CLI
NOT IMPLEMENTED YET
* Start gemini-cli in a tmux session with `tmux new-session -s gemini 'gemini' (gemini-cli
doesn't have a supported way of controlling the TUI and it's being done by sending 
tmux events)

## Develop
1. Import the project
2. Run `./gradlew runIde` to launch a sandboxed version of Intellij jetbridge installed
3. Trigger actions and test


