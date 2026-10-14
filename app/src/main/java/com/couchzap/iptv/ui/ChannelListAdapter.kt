package com.couchzap.iptv.ui

import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.couchzap.iptv.R
import com.couchzap.iptv.data.Channel
import com.couchzap.iptv.databinding.ItemChannelBinding
import com.couchzap.iptv.player.ChannelPlaybackStatus

class ChannelListAdapter(
    private val artwork: ChannelArtwork? = null,
    private val onFavourite: (Channel) -> Unit = {},
    private val onFocused: (Channel) -> Unit = {},
    private val onChannelSelected: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelListAdapter.ChannelViewHolder>() {
    private val channels = mutableListOf<Channel>()
    private var selectedIndex = -1
    private var favourites = emptySet<String>()
    private var playbackStatus = ChannelPlaybackStatus.CONNECTING
    init { setHasStableIds(true) }
    fun setChannels(newList: List<Channel>, currentPlayingIndex: Int, favouriteIds: Set<String> = emptySet()) {
        channels.clear(); channels.addAll(newList); selectedIndex = currentPlayingIndex; favourites = favouriteIds
        notifyDataSetChanged()
    }
    fun channelAt(index: Int): Channel? = channels.getOrNull(index)
    fun indexOfId(id: String): Int = channels.indexOfFirst { it.stableId == id }
    fun setSelectedIndex(index: Int) {
        val previous = selectedIndex; selectedIndex = index
        if (previous in channels.indices) notifyItemChanged(previous)
        if (index in channels.indices) notifyItemChanged(index)
    }
    fun updatePlayback(channelId: String?, status: ChannelPlaybackStatus) {
        val index = channelId?.let(::indexOfId) ?: -1
        if (index == selectedIndex && status == playbackStatus) return
        val previous = selectedIndex
        selectedIndex = index
        playbackStatus = status
        if (previous in channels.indices) notifyItemChanged(previous, "playback")
        if (index in channels.indices && index != previous) notifyItemChanged(index, "playback")
    }
    override fun getItemId(position: Int): Long = (channels[position].stableId + channels[position].url)
        .fold(1469598103934665603L) { hash, char -> (hash xor char.code.toLong()) * 1099511628211L }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ChannelViewHolder(ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) = holder.bind(channels[position], position == selectedIndex)
    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) holder.bindStatus(channels[position], position == selectedIndex)
        else onBindViewHolder(holder, position)
    }
    override fun getItemCount() = channels.size
    inner class ChannelViewHolder(private val binding: ItemChannelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(channel: Channel, selected: Boolean) {
            binding.tvItemChannelNum.text = String.format(java.util.Locale.US, "%03d", channel.number)
            binding.tvItemChannelName.text = channel.name
            binding.tvItemChannelGroup.text = channel.group ?: binding.root.context.getString(R.string.other_group)
            bindStatus(channel, selected)
            artwork?.bind(binding.ivChannelLogo, channel.logo)
            binding.root.setOnFocusChangeListener { _, focused ->
                if (focused) channelAt(bindingAdapterPosition)?.let(onFocused)
            }
            binding.root.setOnClickListener {
                val index = bindingAdapterPosition
                if (index != RecyclerView.NO_POSITION) onChannelSelected(channels[index])
            }
            binding.root.setOnLongClickListener { channelAt(bindingAdapterPosition)?.let(onFavourite); true }
        }
        fun bindStatus(channel: Channel, selected: Boolean) {
            val label = when (playbackStatus) {
                ChannelPlaybackStatus.CONNECTING -> R.string.connecting
                ChannelPlaybackStatus.BUFFERING -> R.string.buffering
                ChannelPlaybackStatus.PLAYING -> R.string.playing
                ChannelPlaybackStatus.PAUSED -> R.string.paused
                ChannelPlaybackStatus.UNAVAILABLE -> R.string.channel_unavailable_short
                ChannelPlaybackStatus.ENDED -> R.string.playback_ended
            }
            binding.tvItemChannelStatus.text = listOfNotNull(if (channel.stableId in favourites) "★" else null,
                if (selected) binding.root.context.getString(label) else null).joinToString(" ")
            binding.tvItemChannelStatus.visibility = if (binding.tvItemChannelStatus.text.isEmpty()) View.GONE else View.VISIBLE
            binding.root.isSelected = selected
            binding.root.contentDescription = "${channel.number} ${channel.name} ${binding.tvItemChannelStatus.text}"
        }
    }
}
