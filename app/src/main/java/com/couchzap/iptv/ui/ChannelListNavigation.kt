package com.couchzap.iptv.ui

import android.view.View
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.couchzap.iptv.data.Channel

/** Keeps an off-screen destination inside the list, including while key repeats outrun layout. */
class ChannelListNavigation(
    private val list: RecyclerView,
    private val adapter: ChannelListAdapter,
    private val isActive: () -> Boolean,
    private val isTv: () -> Boolean,
    private val onEmpty: () -> Unit
) {
    private var rememberedId: String? = null
    private var rememberedPosition = 0
    private var pendingId: String? = null
    private var generation = 0
    private var retry: Runnable? = null
    val hasPending: Boolean get() = pendingId != null
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (hasPending && isActive()) attempt(generation, 0)
    }

    init {
        list.isFocusableInTouchMode = true
        list.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }

    fun focusedPosition(): Int = list.findFocus()?.let(list::findContainingItemView)
        ?.let(list::getChildAdapterPosition) ?: RecyclerView.NO_POSITION

    fun focusedChannel(): Channel? = adapter.channelAt(focusedPosition())

    fun targetChannel(): Channel? = pendingId?.let(adapter::indexOfId)?.let(adapter::channelAt) ?: focusedChannel()

    fun remember(channel: Channel) {
        // A recycled/previous row must not replace a newer off-screen destination.
        if (pendingId != null && pendingId != channel.stableId) return
        rememberedId = channel.stableId
        rememberedPosition = adapter.indexOfId(channel.stableId).coerceAtLeast(0)
        cancel()
    }

    fun restore(currentId: String?) {
        val remembered = rememberedId?.let(adapter::indexOfId) ?: -1
        val current = currentId?.let(adapter::indexOfId) ?: -1
        focus(when { remembered >= 0 -> remembered; current >= 0 -> current; else -> rememberedPosition })
    }

    fun move(delta: Int) {
        val count = adapter.itemCount
        if (count == 0) { cancel(); onEmpty(); return }
        val pending = pendingId?.let(adapter::indexOfId) ?: -1
        val focused = focusedPosition()
        val remembered = rememberedId?.let(adapter::indexOfId) ?: -1
        val position = when { pending >= 0 -> pending; focused >= 0 -> focused; remembered >= 0 -> remembered; else -> 0 }
        focus((position + delta + count) % count)
    }

    fun focus(position: Int) {
        cancel()
        if (!isActive()) return
        if (adapter.itemCount == 0) { onEmpty(); return }
        val target = position.coerceIn(0, adapter.itemCount - 1)
        pendingId = adapter.channelAt(target)!!.stableId
        rememberedId = pendingId
        rememberedPosition = target
        val holder = list.findViewHolderForAdapterPosition(target)
        if (holder == null) {
            // Never park on the Activity root: global focus traversal can reach playback errors.
            requestFocus(list)
            (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(target, 0)
            list.requestLayout()
        }
        attempt(generation, 0)
    }

    private fun requestFocus(view: View): Boolean =
        if (isTv()) view.requestFocusFromTouch() else view.requestFocus()

    private fun attempt(token: Int, tries: Int) {
        if (token != generation || !isActive()) return
        val id = pendingId ?: return
        val target = adapter.indexOfId(id)
        if (target < 0) { cancel(); onEmpty(); return }
        val row = if (list.isComputingLayout) null else list.findViewHolderForAdapterPosition(target)?.itemView
        if (row != null && requestFocus(row)) { cancel(); return }
        // A later global layout can finish the request even after this bounded retry budget.
        if (tries < 8 && retry == null) {
            retry = Runnable { retry = null; attempt(token, tries + 1) }.also { list.postDelayed(it, 16L) }
        }
    }

    fun cancel() {
        generation++
        retry?.let(list::removeCallbacks)
        retry = null
        pendingId = null
    }

    fun destroy() {
        cancel()
        if (list.viewTreeObserver.isAlive) list.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
    }
}
