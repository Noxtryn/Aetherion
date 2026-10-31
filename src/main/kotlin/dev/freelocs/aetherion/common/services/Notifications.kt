package dev.freelocs.aetherion.common.services

enum class NoticeKind { INFO, SUCCESS, WARNING, ERROR }

class Notice(val kind: NoticeKind, val message: String, val timeMillis: Long = System.currentTimeMillis())

/**
 * In-GUI notification history. Drives the header bell (unread count + list) and toasts.
 * Purely local: nothing is sent anywhere.
 */
object Notifications {
    private const val MAX = 50
    private val history = ArrayDeque<Notice>()
    private val toastQueue = ArrayDeque<Notice>()

    var unread = 0
        private set

    val all: List<Notice> get() = history.toList()

    fun push(kind: NoticeKind, message: String) {
        val n = Notice(kind, message)
        history.addFirst(n)
        while (history.size > MAX) history.removeLast()
        toastQueue.addLast(n)
        unread++
    }

    fun markAllRead() { unread = 0 }

    fun clear() { history.clear(); toastQueue.clear(); unread = 0 }

    /** Next toast waiting to be shown, if any. */
    fun pollToast(): Notice? = toastQueue.removeFirstOrNull()
}
