package com.couchzap.iptv.ui

import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.couchzap.iptv.R
import com.couchzap.iptv.data.Channel
import com.couchzap.iptv.databinding.ItemChannelBinding
import com.couchzap.iptv.player.ChannelPlaybackStatus
import com.couchzap.iptv.util.GroupLabels
import com.couchzap.iptv.ui.design.*

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
    private var tvMode:Boolean?=null
    private var language:String?=null
    fun updatePresentation(tv:Boolean,language:String){if(tvMode==tv&&this.language==language)return;tvMode=tv;this.language=language;notifyItemRangeChanged(0,itemCount,"presentation")}
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
        if (payloads.isNotEmpty()&&"presentation" !in payloads) holder.bindStatus(channels[position], position == selectedIndex,
            language?:com.couchzap.iptv.util.Prefs(holder.itemView.context).getLanguage()?:"en")
        else onBindViewHolder(holder, position)
    }
    override fun getItemCount() = channels.size
    override fun onViewRecycled(holder: ChannelViewHolder) { holder.recycle(); super.onViewRecycled(holder) }
    override fun onViewAttachedToWindow(holder: ChannelViewHolder) { super.onViewAttachedToWindow(holder); FocusMotionBinder.bind(holder.itemView, FocusMotionBinder.Role.CHANNEL).refresh() }
    inner class ChannelViewHolder(private val binding: ItemChannelBinding) : RecyclerView.ViewHolder(binding.root) {
        private val initialTv=AdaptiveUi.isTv(binding.root.context)
        private val initialLanguage=com.couchzap.iptv.util.Prefs(binding.root.context).getLanguage()?:"en"
        init {
            binding.root.setOnFocusChangeListener { _, focused ->
                if (focused) channelAt(bindingAdapterPosition)?.let(onFocused)
            }
            FocusMotionBinder.bind(binding.root, FocusMotionBinder.Role.CHANNEL)
        }
        fun recycle() { FocusMotionBinder.reset(binding.root); artwork?.bind(binding.ivChannelLogo, null) }
        fun bind(channel: Channel, selected: Boolean) {
            val tv=tvMode?:initialTv;val lang=language?:initialLanguage
            CouchZapTokens.text(binding.tvItemChannelName, CouchZapTokens.Text.CHANNEL,tv,lang)
            CouchZapTokens.text(binding.tvItemChannelGroup, CouchZapTokens.Text.VALUE,tv,lang)
            CouchZapTokens.text(binding.tvItemChannelStatus, CouchZapTokens.Text.VALUE,tv,lang)
            CouchZapTokens.text(binding.tvItemChannelNum, CouchZapTokens.Text.FOOTER,tv,lang)
            binding.root.setTag(R.id.cz_action_key, channel.stableId)
            binding.tvItemChannelNum.text = com.couchzap.iptv.util.LocalizedNumbers.channelNumber(channel.number, lang)
            // Item 9: keep localized digit sequences in logical order under RTL.
            binding.tvItemChannelNum.textDirection=View.TEXT_DIRECTION_LTR
            binding.tvItemChannelNum.layoutParams=binding.tvItemChannelNum.layoutParams.apply{width=maxOf((36*binding.root.resources.displayMetrics.density).toInt(),kotlin.math.ceil(binding.tvItemChannelNum.paint.measureText(binding.tvItemChannelNum.text.toString())).toInt())}
            binding.tvItemChannelNum.fontFeatureSettings="tnum"
            binding.ivChannelLogo.layoutParams=binding.ivChannelLogo.layoutParams.apply{val size=if(tv)40 else 36;width=(size*binding.root.resources.displayMetrics.density).toInt();height=width}
            binding.tvItemChannelName.text = channel.name
            binding.tvItemChannelGroup.text = GroupLabels.display(binding.root.context, channel.group)
            bindStatus(channel, selected, lang)
            artwork?.bind(binding.ivChannelLogo, channel.logo)
            binding.root.setOnClickListener {
                val index = bindingAdapterPosition
                if (index != RecyclerView.NO_POSITION) onChannelSelected(channels[index])
            }
            binding.root.setOnLongClickListener { channelAt(bindingAdapterPosition)?.let(onFavourite); true }
        }
        fun bindStatus(channel: Channel, selected: Boolean, lang: String) {
            val label = when (playbackStatus) {
                ChannelPlaybackStatus.CONNECTING -> R.string.connecting
                ChannelPlaybackStatus.BUFFERING -> R.string.buffering
                ChannelPlaybackStatus.PLAYING -> R.string.playing
                ChannelPlaybackStatus.PAUSED -> R.string.paused
                ChannelPlaybackStatus.UNAVAILABLE -> R.string.channel_unavailable_short
                ChannelPlaybackStatus.ENDED -> R.string.playback_ended
            }
            binding.tvItemChannelStatus.text = if (selected) binding.root.context.getString(label) else ""
            binding.root.findViewById<View>(R.id.cz_channel_favourite).apply {
                isSelected = channel.stableId in favourites
                visibility = if (isSelected) View.VISIBLE else View.GONE
            }
            binding.tvItemChannelGroup.visibility = if (selected) View.GONE else View.VISIBLE
            binding.tvItemChannelStatus.visibility = if (binding.tvItemChannelStatus.text.isEmpty()) View.GONE else View.VISIBLE
            binding.root.isSelected = selected
            binding.root.contentDescription = listOfNotNull(com.couchzap.iptv.util.LocalizedNumbers.localize(channel.number, lang), channel.name,
                binding.tvItemChannelStatus.text.toString().takeIf { it.isNotEmpty() },
                binding.root.context.getString(R.string.favourites).takeIf { channel.stableId in favourites }).joinToString(", ")
        }
    }
}
