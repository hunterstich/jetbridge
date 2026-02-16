package com.hunterstich.idea.jetbridge.provider.opencode

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

object OpenCodeComponents {

    data class ConnectDialogResult(
        val server: OpenCodeApi.Server,
        val session: OpenCodeApi.Session
    )

    // @oc Add documentation to this method
    fun showConnectDialog(
        servers: List<OpenCodeApi.Server>
    ): ConnectDialogResult? {
        var selectedServer: OpenCodeApi.Server? = null

        val addressOptions = servers.map { it.address }.toTypedArray()
        var sessions: List<OpenCodeApi.Session> = emptyList()

        val addressCombo = ComboBox(DefaultComboBoxModel(addressOptions)).apply {
            isEditable = true
        }
        val sessionCombo = ComboBox(DefaultComboBoxModel(emptyArray<String>())).apply {
            isEditable = true
        }

        // Fetch sessions for the given address and update the session dropdown
        fun loadSessions(address: String) {
            CoroutineScope(Dispatchers.IO).launch {
                // Update the selected server
                selectedServer = if (servers.map { it.address }.contains(address)) {
                    servers.firstOrNull { it.address == address }
                } else {
                    OpenCodeApi
                        .getServerPath(address)
                        .getOrNull()
                        ?.let { OpenCodeApi.Server(address, it) }
                }

                val fetched = OpenCodeApi.getSessions(address).getOrNull() ?: emptyList()
                withContext(Dispatchers.Main) {
                    sessions = fetched
                    sessionCombo.model = DefaultComboBoxModel(
                        fetched.map { it.title }.toTypedArray()
                    )
                }
            }
        }

        addressCombo.addActionListener {
            val selected = addressCombo.selectedItem as? String ?: return@addActionListener
            loadSessions(selected)
        }

        // Load sessions when the user types a custom address and presses Enter
        (addressCombo.editor.editorComponent as? JComponent)?.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    e.consume()
                    val address = addressCombo.editor.item as? String ?: return
                    loadSessions(address)
                }
            }
        })

        // Auto-load sessions for the first server if available
        if (addressOptions.isNotEmpty()) {
            loadSessions(addressOptions.first())
        }

        val dialog = object : DialogWrapper(true) {
            init {
                title = "Connect to OpenCode"
                init()
            }

            override fun createCenterPanel(): JComponent {
                return panel {
                    row("Address:") { cell(addressCombo) }
                    row("Session:") { cell(sessionCombo) }
                }
            }
        }

        dialog.show()
        return if (dialog.isOK) {
            val server = selectedServer ?: return null
            val sessionTitle = (sessionCombo.selectedItem as? String)
                ?: (sessionCombo.editor.item as? String)
                ?: return null
            val session = sessions.firstOrNull { it.title == sessionTitle } ?: return null
            ConnectDialogResult(server, session)
        } else {
            null
        }
    }
}
