# jetbridge
IdeaVim extension that lets you interact 
with an AI code assistant like opencode or gemini-cli. Similar to 
 [opencode.nvim](https://github.com/nickjvandyke/opencode.nvim).

## Installation
1. Make sure IdeaVIM is installed in your Intellij or Android Studio
2. Clone the repo
3. Build the plugin: `./gradlew buildPlugin`
4. Install the resulting ZIP from `build/distributions/` into your IDE through
   the "Settings > Plugins > Install plugins from disk" option
5. Enable the extension in your `.ideavimrc` with:
   ```vim
   set jetbridge
   ```
 
## Keybinding defaults
With a provider running (see [Providers] section):
* `<leader>oa` Ask the provider about current line or selection  

## Providers

### OpenCode
* Start opencode in a terminal with `opencode --port 3000`
* When in the editor, use keybidnings to interact with opencode running
in your terminal


### Gemini-CLI
* Start gemini-cli in a tmux session with `tmux new-session -s gemini 'gemini' (gemini-cli
doesn't have a supported way of controlling the TUI and it's being done by sending 
tmux events)

