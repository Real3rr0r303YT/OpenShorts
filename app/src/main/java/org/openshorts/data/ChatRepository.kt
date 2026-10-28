package org.openshorts.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class ChatMessage(
    val id: String,
    val text: String,
    val fromMe: Boolean,
    val sentAtMillis: Long,
)

@Serializable
data class Conversation(
    val id: String,
    /** Contact handle, e.g. `alice@tube.example`. */
    val contactHandle: String,
    val contactName: String,
    val contactAvatarUrl: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val updatedAtMillis: Long,
    val unread: Int = 0,
) {
    val lastMessage: ChatMessage? get() = messages.lastOrNull()
}

/**
 * Direct messages, stored in app-private storage.
 *
 * PeerTube has no direct-message API, so conversations currently live only on
 * this device and are not delivered to the contact. The repository is the single
 * place a real transport (e.g. Matrix or ActivityPub direct notes) would plug into.
 */
class ChatRepository(context: Context) {

    private val file = File(context.applicationContext.filesDir, "chats.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Conversation.serializer())
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())

    /** Most recently active first. */
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    init {
        scope.launch {
            val stored = runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull()
            if (stored != null) {
                // Keep anything created before loading finished.
                _conversations.update { current -> (current + stored).distinctBy { it.id }.sortedByDescending { it.updatedAtMillis } }
            }
        }
    }

    /** Returns the conversation id with [handle], creating it if needed. */
    fun openConversation(handle: String, name: String, avatarUrl: String?): String {
        val normalized = handle.trim().removePrefix("@")
        _conversations.value.firstOrNull { it.contactHandle.equals(normalized, ignoreCase = true) }
            ?.let { return it.id }
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            contactHandle = normalized,
            contactName = name.ifBlank { normalized.substringBefore('@') },
            contactAvatarUrl = avatarUrl,
            updatedAtMillis = System.currentTimeMillis(),
        )
        mutate { listOf(conversation) + it }
        return conversation.id
    }

    fun send(conversationId: String, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        val now = System.currentTimeMillis()
        val message = ChatMessage(UUID.randomUUID().toString(), body, fromMe = true, sentAtMillis = now)
        mutate { list ->
            list.map { if (it.id == conversationId) it.copy(messages = it.messages + message, updatedAtMillis = now) else it }
                .sortedByDescending { it.updatedAtMillis }
        }
    }

    fun markRead(conversationId: String) {
        if (_conversations.value.none { it.id == conversationId && it.unread > 0 }) return
        mutate { list -> list.map { if (it.id == conversationId) it.copy(unread = 0) else it } }
    }

    fun delete(conversationId: String) {
        mutate { list -> list.filterNot { it.id == conversationId } }
    }

    private fun mutate(transform: (List<Conversation>) -> List<Conversation>) {
        _conversations.update(transform)
        val snapshot = _conversations.value
        scope.launch {
            mutex.withLock { file.writeText(json.encodeToString(serializer, snapshot)) }
        }
    }
}
