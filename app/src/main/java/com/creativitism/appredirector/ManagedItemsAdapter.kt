package com.creativitism.appredirector

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.creativitism.appredirector.databinding.ItemBlockedRowBinding
import com.creativitism.appredirector.databinding.ItemManagedItemBinding

class ManagedItemsAdapter(
    private var items: List<ManagedItem>,
    private val rowMode: RowMode,
    private val onRemoveClicked: (ManagedItem) -> Unit,
    private val onPolicyChanged: ((String, BlockedItemPolicy) -> Unit)? = null,
    private val getPolicy: ((String) -> BlockedItemPolicy)? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class RowMode {
        BLOCKED,
        CREATIVE,
    }

    private class CreativeViewHolder(
        private val binding: ItemManagedItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ManagedItem, onRemoveClicked: (ManagedItem) -> Unit) {
            binding.itemName.text = item.displayName
            binding.itemIcon.setImageDrawable(item.icon)
            binding.removeButton.setOnClickListener { onRemoveClicked(item) }
        }
    }

    private class BlockedViewHolder(
        private val binding: ItemBlockedRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var boundItemId: String? = null
        private var updatingFromCode = false

        private var boundMode: EnforcementMode = EnforcementMode.STRICT

        private fun selectEnforcement(mode: EnforcementMode) {
            boundMode = mode
            binding.enforcementStrict.isSelected = mode == EnforcementMode.STRICT
            binding.enforcementOnce.isSelected = mode == EnforcementMode.ONCE_PER_VISIT
        }

        fun bind(
            item: ManagedItem,
            policy: BlockedItemPolicy,
            onRemoveClicked: (ManagedItem) -> Unit,
            onPolicyChanged: (String, BlockedItemPolicy) -> Unit,
        ) {
            boundItemId = item.id
            binding.itemName.text = item.displayName
            binding.itemIcon.setImageDrawable(item.icon)
            binding.removeButton.setOnClickListener { onRemoveClicked(item) }

            updatingFromCode = true
            selectEnforcement(policy.enforcementMode)
            binding.confessionSwitch.isChecked = policy.confessionEnabled
            updatingFromCode = false

            binding.enforcementStrict.setOnClickListener {
                if (updatingFromCode) return@setOnClickListener
                val itemId = boundItemId ?: return@setOnClickListener
                selectEnforcement(EnforcementMode.STRICT)
                onPolicyChanged(
                    itemId,
                    BlockedItemPolicy(EnforcementMode.STRICT, binding.confessionSwitch.isChecked),
                )
            }
            binding.enforcementOnce.setOnClickListener {
                if (updatingFromCode) return@setOnClickListener
                val itemId = boundItemId ?: return@setOnClickListener
                selectEnforcement(EnforcementMode.ONCE_PER_VISIT)
                onPolicyChanged(
                    itemId,
                    BlockedItemPolicy(EnforcementMode.ONCE_PER_VISIT, binding.confessionSwitch.isChecked),
                )
            }

            binding.confessionSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (updatingFromCode) return@setOnCheckedChangeListener
                val itemId = boundItemId ?: return@setOnCheckedChangeListener
                onPolicyChanged(itemId, BlockedItemPolicy(boundMode, isChecked))
            }
        }
    }

    override fun getItemViewType(position: Int): Int = when (rowMode) {
        RowMode.BLOCKED -> VIEW_TYPE_BLOCKED
        RowMode.CREATIVE -> VIEW_TYPE_CREATIVE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_BLOCKED -> BlockedViewHolder(
                ItemBlockedRowBinding.inflate(inflater, parent, false),
            )
            else -> CreativeViewHolder(
                ItemManagedItemBinding.inflate(inflater, parent, false),
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is CreativeViewHolder -> holder.bind(item, onRemoveClicked)
            is BlockedViewHolder -> {
                val policy = getPolicy?.invoke(item.id) ?: BlockedItemPolicy.DEFAULT
                val onChanged = onPolicyChanged
                    ?: throw IllegalStateException("Blocked rows require onPolicyChanged")
                holder.bind(item, policy, onRemoveClicked, onChanged)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<ManagedItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    companion object {
        private const val VIEW_TYPE_BLOCKED = 0
        private const val VIEW_TYPE_CREATIVE = 1
    }
}
