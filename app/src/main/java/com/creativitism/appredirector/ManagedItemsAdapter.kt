package com.creativitism.appredirector

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.creativitism.appredirector.databinding.ItemManagedItemBinding

class ManagedItemsAdapter(
    private var items: List<ManagedItem>,
    private val onRemoveClicked: (ManagedItem) -> Unit
) : RecyclerView.Adapter<ManagedItemsAdapter.ViewHolder>() {

    class ViewHolder(private val binding: ItemManagedItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ManagedItem, onRemoveClicked: (ManagedItem) -> Unit) {
            binding.itemName.text = item.displayName
            binding.itemIcon.setImageDrawable(item.icon)
            binding.removeButton.setOnClickListener { onRemoveClicked(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemManagedItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onRemoveClicked)
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<ManagedItem>) {
        items = newItems
        notifyDataSetChanged()
    }
} 