package com.creativitism.appredirector

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.creativitism.appredirector.databinding.ItemRedirectionBinding

class RedirectionsAdapter(
    private val redirections: List<RedirectionInfo>,
    private val onRemoveRedirection: (RedirectionInfo) -> Unit
) : RecyclerView.Adapter<RedirectionsAdapter.RedirectionViewHolder>() {

    class RedirectionViewHolder(private val binding: ItemRedirectionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(redirection: RedirectionInfo, onRemoveRedirection: (RedirectionInfo) -> Unit) {
            binding.sourceAppName.text = redirection.sourceApp.appName
            binding.sourceAppIcon.setImageDrawable(redirection.sourceApp.icon)
            
            binding.targetAppName.text = redirection.targetApp.appName
            binding.targetAppIcon.setImageDrawable(redirection.targetApp.icon)
            
            binding.removeRedirectionButton.setOnClickListener {
                onRemoveRedirection(redirection)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RedirectionViewHolder {
        val binding = ItemRedirectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RedirectionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RedirectionViewHolder, position: Int) {
        holder.bind(redirections[position], onRemoveRedirection)
    }

    override fun getItemCount(): Int = redirections.size
} 