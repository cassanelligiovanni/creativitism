package com.creativitism.appredirector

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.creativitism.appredirector.databinding.ItemColorAppBinding

/**
 * Row adapter for the "apps in color" picker. Holds the single source of truth for
 * which packages are currently selected — seeded from [GrayscaleStore] by the caller,
 * mutated in place on tap. The adapter never re-sorts itself: the activity owns
 * ordering and pushes a freshly sorted list via [submitList] only when it is safe to
 * move rows (initial load, search change, onResume) — never mid-tap.
 */
class ColorAppsAdapter(
    initialSelected: Set<String>,
    private val onToggle: (AppInfo, Boolean) -> Unit,
) : RecyclerView.Adapter<ColorAppsAdapter.ViewHolder>() {

    private var items: List<AppInfo> = emptyList()
    private val selected: MutableSet<String> = initialSelected.toMutableSet()

    fun submitList(apps: List<AppInfo>) {
        items = apps
        notifyDataSetChanged()
    }

    fun isSelected(packageName: String): Boolean = selected.contains(packageName)

    inner class ViewHolder(private val binding: ItemColorAppBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppInfo) {
            binding.appName.text = app.appName
            binding.appIcon.setImageDrawable(app.icon)
            applyToggleIcon(app.packageName)
            binding.root.setOnClickListener {
                val nowSelected = if (selected.contains(app.packageName)) {
                    selected.remove(app.packageName)
                    false
                } else {
                    selected.add(app.packageName)
                    true
                }
                applyToggleIcon(app.packageName)
                onToggle(app, nowSelected)
            }
        }

        private fun applyToggleIcon(packageName: String) {
            val context = binding.root.context
            if (selected.contains(packageName)) {
                binding.toggleIcon.setImageResource(R.drawable.ic_check_circle)
                ImageViewCompat.setImageTintList(
                    binding.toggleIcon,
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.cv_creative)),
                )
            } else {
                binding.toggleIcon.setImageResource(R.drawable.ic_circle_outline)
                ImageViewCompat.setImageTintList(
                    binding.toggleIcon,
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.cv_text_secondary)),
                )
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemColorAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
