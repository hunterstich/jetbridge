# Jetbridge
Jetbridge is a custom IdeaVim extension for Jetbrains IDEs that adds keybindings for interacting 
with an AI code assistant provider like opencode, gemini-cli, or Gemini in Android Studio. Inspired 
by[opencode.nvim](https://github.com/nickjvandyke/opencode.nvim).

## Installation
1. Build the plugin: `./gradlew buildPlugin`
2. Install the resulting ZIP from `build/distributions/` into your IntelliJ IDEA through
   the "Settings > Plugins > Install plugins from disk" option
3. Enable the extension in your `.ideavimrc` with:
   ```vim
   set jetbridge
   ```
   
## Usage
To use jetbridge, fist start one of the supported providers:

### OpenCode
With the plugin installed, start an open code session in a terminal (either in the IDE or in a 
separate terminal window) with `opencode --port 3000`.

### Gemini-CLI
In a local terminal, start Gemini-CLI in a tmux session named "gemini" 
with `tmux new-session -s gemini 'gemini'`. This is a limitation of the gemini-cli tool which
doesn't have a direct way to interact with the TUI and instead has to be sent keys through tmux.

## Keybinding defaults
* `<leader>oa` Ask the provider about current line or selection
