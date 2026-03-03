package com.hunterstich.idea.jetbridge.ui

import com.hunterstich.idea.jetbridge.core.OpenCodeApi
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

class OpenCodeConnectDialog(
    private val servers: List<OpenCodeApi.Server>,
    private val initialAddress: String? = null,
    private val initialSessionId: String? = null,
) : DialogWrapper(true) {

    companion object {
        fun show(
            servers: List<OpenCodeApi.Server>,
            initialAddress: String? = null,
            initialSessionId: String? = null,
        ): Result? {
            val dialog = OpenCodeConnectDialog(
                servers = servers,
                initialAddress = initialAddress,
                initialSessionId = initialSessionId,
            )
            dialog.show()
            return if (dialog.isOK) dialog.selectedResult() else null
        }
    }

    data class Result(
        val server: OpenCodeApi.Server,
        val session: OpenCodeApi.Session
    )

    private val scope = CoroutineScope(Dispatchers.IO)
    private val addressOptions = servers.map { it.address }.toTypedArray()

    private var selectedServer: OpenCodeApi.Server? = null
    private var sessions: List<OpenCodeApi.Session> = emptyList()

    private val addressCombo = ComboBox(DefaultComboBoxModel(addressOptions))
        .apply {
            isEditable = true
        }
    private val sessionCombo = ComboBox(DefaultComboBoxModel(emptyArray<String>()))
        .apply {
            isEditable = true
        }

    init {
        title = "Connect to OpenCode"
        Disposer.register(disposable) {
            scope.cancel()
        }
        initializeAddressAndSessionSelection()
        registerAddressListeners()
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row("Address:") { cell(addressCombo) }
            row("Session:") { cell(sessionCombo) }
        }
    }

    private fun initializeAddressAndSessionSelection() {
        // Auto-load sessions for initial server selection if available.
        if (addressOptions.isNotEmpty()) {
            val initialSelection = initialAddress
                ?.takeIf { address -> addressOptions.contains(address) }
                ?: addressOptions.first()
            addressCombo.selectedItem = initialSelection
            loadSessions(initialSelection, initialSessionId)
        }
    }

    private fun registerAddressListeners() {
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
    }

    private fun loadSessions(address: String, preferredSessionId: String? = null) {
        scope.launch {
            selectedServer = if (addressOptions.contains(address)) {
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
                val preferredSessionTitle = preferredSessionId
                    ?.let { id -> fetched.firstOrNull { it.id == id }?.title }
                if (preferredSessionTitle != null) {
                    sessionCombo.selectedItem = preferredSessionTitle
                }
            }
        }
    }

    private fun selectedResult(): Result? {
        val server = selectedServer ?: return null
        val sessionTitle = (sessionCombo.selectedItem as? String)
            ?: (sessionCombo.editor.item as? String)
            ?: return null
        val session = sessions.firstOrNull { it.title == sessionTitle } ?: return null
        return Result(server, session)
    }
}
