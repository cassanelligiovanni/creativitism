package com.creativitism.appredirector

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.creativitism.appredirector.databinding.ItemAppBinding

class AppsAdapter(
    private val apps: List<AppInfo>,
    private val onSetRedirection: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {

    class AppViewHolder(private val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: AppInfo, onSetRedirection: (AppInfo) -> Unit) {
            binding.appName.text = app.appName
            binding.packageName.text = app.packageName
            binding.appIcon.setImageDrawable(app.icon)
            
            binding.setRedirectionButton.setOnClickListener {
                onSetRedirection(app)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(apps[position], onSetRedirection)
    }

    override fun getItemCount(): Int = apps.size
} 