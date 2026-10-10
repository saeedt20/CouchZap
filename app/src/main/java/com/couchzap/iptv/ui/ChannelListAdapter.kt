package com.couchzap.iptv.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.couchzap.iptv.data.Channel
import com.couchzap.iptv.databinding.ItemChannelBinding

class ChannelListAdapter(
    private val onChannelSelected: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelListAdapter.ChannelViewHolder>() {

    private val channels = mutableListOf<Channel>()
    private var selectedIndex = -1

    fun setChannels(newList: List<Channel>, currentPlayingIndex: Int) {
        channels.clear()
        channels.addAll(newList)
        selectedIndex = currentPlayingIndex
        notifyDataSetChanged()
    }

    fun setSelectedIndex(index: Int) {
        val prev = selectedIndex
        selectedIndex = index
        if (prev in 0 until channels.size) notifyItemChanged(prev)
        if (selectedIndex in 0 until channels.size) notifyItemChanged(selectedIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(channels[position], position == selectedIndex)
    }

    override fun getItemCount(): Int = channels.size

    inner class ChannelViewHolder(private val binding: ItemChannelBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(channel: Channel, isSelected: Boolean) {
            binding.tvItemChannelNum.text = String.format("%03d", channel.number)
            binding.tvItemChannelName.text = channel.name

            binding.root.isSelected = isSelected
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    setSelectedIndex(pos)
                    onChannelSelected(channels[pos])
                }
            }
        }
    }
}
