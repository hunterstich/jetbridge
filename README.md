# idea-gemini
This is a custom IdeaVim extension for Jetbrains IDEs that adds keybindings for interacting with
the Gemini in Android Studio tool window. Inspired by [opencode.nvim](https://github.com/nickjvandyke/opencode.nvim).

## Usage
* `<leader>oa` Ask gemini about current line or selection
* `<leaader>ox` Execute a gemini command with the current line or selection

## Installation
1. Build the plugin: `./gradlew buildPlugin`
2. Install the resulting ZIP from `build/distributions/` into your IntelliJ IDEA.
3. Enable the extension in your `.ideavimrc`:
   ```vim
   set gemini
   ```

## TODO
* [ ] Expand to be agent/tool-window agnostic
    - Have modules for different flows - opencode tui, gemini in AS, etc.
    - Just provide the basic keybindings and Jetbrains plugin UI
* [ ] File an FR for public Jetbrains actions that can control the Gemini side panel:
    - InsertInAsk
    - SubmitAsk
    - InsertInAgent
    - SubmitAgent
    - ScrollWindowUp
    - ScrollWindowDown
    - AddToContext
