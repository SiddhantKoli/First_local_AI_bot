package com.sainiksahayak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sainiksahayak.app.chat.ChatScreen
import com.sainiksahayak.app.chat.ChatViewModel
import com.sainiksahayak.app.ui.SainikTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    /**
     * Storage Access Framework picker. We copy the file immediately, so no
     * persistable URI permission is needed afterwards.
     */
    private val pickModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onModelSelected(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SainikTheme {
                val state by viewModel.ui.collectAsStateWithLifecycle()
                ChatScreen(
                    state = state,
                    onSend = viewModel::send,
                    onStop = viewModel::stop,
                    onNewChat = viewModel::newChat,
                    onPickModel = { pickModel.launch(arrayOf("*/*")) },
                )
            }
        }
    }
}
