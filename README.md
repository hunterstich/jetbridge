# Jetbridge
Jetbridge is a custom IdeaVim extension for Jetbrains IDEs that adds keybindings for interacting 
with an AI code assistant provider like opencode, gemini-cli, or Gemini in Android Studio. Inspired 
by[opencode.nvim](https://github.com/nickjvandyke/opencode.nvim).

## Usage
* `<leader>oa` Ask the provider about current line or selection
* `<leaader>ox` Execute a provider command with the current line or selection

## Installation
1. Build the plugin: `./gradlew buildPlugin`
2. Install the resulting ZIP from `build/distributions/` into your IntelliJ IDEA.
3. Enable the extension in your `.ideavimrc`:
   ```vim
   set jetbridge
   ```