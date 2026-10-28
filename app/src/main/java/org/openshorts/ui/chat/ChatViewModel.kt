package org.openshorts.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.openshorts.OpenShortsApp
import org.openshorts.data.Conversation
import org.openshorts.data.peertube.Creator

/** Inbox and conversations. Messages are stored on this device only (see [org.openshorts.data.ChatRepository]). */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenShortsApp
    private val chats = app.chatRepository

    val conversations: StateFlow<List<Conversation>> = chats.conversations

    /** Followed creators, offered as contacts for a new chat. */
    val contacts: StateFlow<List<Creator>> = app.profileRepository.following
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openConversation(handle: String, name: String, avatarUrl: String?): String =
        chats.openConversation(handle, name, avatarUrl)

    fun send(conversationId: String, text: String) = chats.send(conversationId, text)

    fun markRead(conversationId: String) = chats.markRead(conversationId)

    fun delete(conversationId: String) = chats.delete(conversationId)
}
