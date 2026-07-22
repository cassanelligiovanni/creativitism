package com.creativitism.appredirector

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.creativitism.appredirector.databinding.ItemAllAppsBinding

class AllAppsAdapter(
    private val apps: List<AppInfo>,
    private val onAddToCreative: (AppInfo) -> Unit,
    private val onAddToSoulSucking: (AppInfo) -> Unit
) : RecyclerView.Adapter<AllAppsAdapter.ViewHolder>() {

    class ViewHolder(private val binding: ItemAllAppsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            app: AppInfo,
            onAddToCreative: (AppInfo) -> Unit,
            onAddToSoulSucking: (AppInfo) -> Unit
        ) {
            binding.appName.text = app.appName
            binding.appIcon.setImageDrawable(app.icon)
            binding.addToCreativeButton.setOnClickListener { onAddToCreative(app) }
            binding.addToSoulSuckingButton.setOnClickListener { onAddToSoulSucking(app) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAllAppsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(apps[position], onAddToCreative, onAddToSoulSucking)
    }

    override fun getItemCount(): Int = apps.size
} 