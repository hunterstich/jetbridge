package com.hunterstich.idea.jetbridge

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.EditorTextField
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.command.MappingMode
import com.maddyhome.idea.vim.key.MappingOwner.Plugin.Companion.get
import javax.swing.JComponent


/**
 * TODO: Work in progress
 *
 * A multi-line, vim enabled text area for writing prompts
 */
class JetbridgeDialog : DialogWrapper(true) {

    var enterRemapped = false
    val editorTextField: EditorTextField = EditorTextField()

    init {
        setSize(200, 200)
        title = "Jetbridge"
//        val vimPlugin = PluginManagerCore.getPlugin(PluginId.getId("IdeaVIM"))
//        val vimEnabled = vimPlugin != null && vimPlugin.isEnabled
//        if (vimEnabled) {
//            remapEnter()
//        }
        init()
    }

    private fun remapEnter() {
        if (enterRemapped) return
//        // i had 2 <cr> here before and i can't remember why
//        val keys: List<KeyStroke> =
//            injector.parser.parseKeys(":action SelectHarpoonItem<cr>")
//        val keyGroup: VimKeyGroup =
//            injector.keyGroup
//        keyGroup.putKeyMapping(
//            MappingMode.NVO,
//            injector.parser.parseKeys("<cr>"),
//            get("Jetbridge"), keys, false
//        )

        // Call doOkAction when enter is pressed
        // TODO: Causing crash
        injector.keyGroup.putKeyMapping(
            MappingMode.NVO,
            injector.parser.parseKeys("<cr>"),
            get("Jetbridge"),
            injector.parser.parseKeys("<C><cr>"),
            false
        )


        enterRemapped = true
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return editorTextField
    }

    override fun createCenterPanel(): JComponent {
        editorTextField.setOneLineMode(false)
        editorTextField.addSettingsProvider { editor ->
            editor.isInsertMode = true
        }

        return editorTextField
    }
}