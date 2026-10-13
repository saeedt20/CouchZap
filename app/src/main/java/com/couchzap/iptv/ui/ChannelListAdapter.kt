package com.couchzap.iptv.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.couchzap.iptv.R
import com.couchzap.iptv.data.Channel
import com.couchzap.iptv.databinding.ItemChannelBinding

class ChannelListAdapter(
    private val artwork: ChannelArtwork? = null,
    private val onFavourite: (Channel) -> Unit = {},
    private val onChannelSelected: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelListAdapter.ChannelViewHolder>() {
    private val channels = mutableListOf<Channel>()
    private var selectedIndex = -1
    private var favourites = emptySet<String>()
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
    override fun getItemId(position: Int): Long = (channels[position].stableId + channels[position].url)
        .fold(1469598103934665603L) { hash, char -> (hash xor char.code.toLong()) * 1099511628211L }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ChannelViewHolder(ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) = holder.bind(channels[position], position == selectedIndex)
    override fun getItemCount() = channels.size
    inner class ChannelViewHolder(private val binding: ItemChannelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(channel: Channel, playing: Boolean) {
            binding.tvItemChannelNum.text = String.format(java.util.Locale.US, "%03d", channel.number)
            binding.tvItemChannelName.text = channel.name
            binding.tvItemChannelGroup.text = channel.group ?: binding.root.context.getString(R.string.other_group)
            binding.tvItemChannelStatus.text = listOfNotNull(if (channel.stableId in favourites) "★" else null,
                if (playing) binding.root.context.getString(R.string.playing) else null).joinToString(" ")
            binding.root.isSelected = playing
            binding.root.contentDescription = "${channel.number} ${channel.name} ${binding.tvItemChannelStatus.text}"
            artwork?.bind(binding.ivChannelLogo, channel.logo)
            binding.root.setOnClickListener {
                val index = bindingAdapterPosition
                if (index != RecyclerView.NO_POSITION) { setSelectedIndex(index); onChannelSelected(channels[index]) }
            }
            binding.root.setOnLongClickListener { onFavourite(channel); true }
        }
    }
}
