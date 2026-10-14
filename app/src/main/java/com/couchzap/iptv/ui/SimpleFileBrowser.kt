package com.couchzap.iptv.ui

import com.couchzap.iptv.R
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.couchzap.iptv.databinding.ItemFileBinding
import java.io.File

class SimpleFileBrowser(
    private val container: LinearLayout,
    private val tvPath: TextView,
    private val rvFiles: RecyclerView,
    private val onFileSelected: (File) -> Unit,
    private val onCloseRequested: () -> Unit
) {

    private var currentDirectory: File = Environment.getExternalStorageDirectory()
    private val adapter = FileAdapter()

    init {
        rvFiles.layoutManager = LinearLayoutManager(container.context)
        rvFiles.adapter = adapter
    }

    fun openAtRoot() {
        container.visibility = View.VISIBLE
        val storage = File("/storage")
        val root = if (storage.isDirectory && storage.canRead()) storage else Environment.getExternalStorageDirectory()
        navigateTo(root)
    }

    fun isVisible(): Boolean = container.visibility == View.VISIBLE

    fun close() {
        container.visibility = View.GONE
    }

    fun handleBack(): Boolean {
        if (!isVisible()) return false

        val parent = currentDirectory.parentFile
        if (parent != null && parent.canRead() && currentDirectory.path != "/storage" && currentDirectory.path != "/") {
            navigateTo(parent)
            return true
        } else {
            close()
            onCloseRequested()
            return true
        }
    }

    private fun navigateTo(dir: File) {
        currentDirectory = dir
        tvPath.textDirection = View.TEXT_DIRECTION_LTR
        tvPath.text = dir.absolutePath

        val items = mutableListOf<File>()

        // Find mount points if at /storage or /mnt
        if (dir.path == "/storage" || dir.path == "/mnt") {
            val mounts = arrayOf(
                Environment.getExternalStorageDirectory(),
                File("/storage"),
                File("/mnt/usb"),
                File("/mnt/media_rw")
            )
            for (m in mounts) {
                if (m.exists() && m.canRead()) {
                    val children = m.listFiles() ?: emptyArray()
                    for (c in children) {
                        if (c.isDirectory && c.canRead() && !items.contains(c)) {
                            items.add(c)
                        }
                    }
                }
            }
        } else {
            val files = dir.listFiles() ?: emptyArray()
            val folders = files.filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name.lowercase() }
            val playlistFiles = files.filter { file ->
                !file.isDirectory && (
                        file.name.endsWith(".m3u", ignoreCase = true) ||
                                file.name.endsWith(".m3u8", ignoreCase = true) ||
                                file.name.endsWith(".txt", ignoreCase = true) ||
                                file.name.endsWith(".xml", ignoreCase = true) ||
                                file.name.endsWith(".xml.gz", ignoreCase = true)
                        )
            }.sortedBy { it.name.lowercase() }

            items.addAll(folders)
            items.addAll(playlistFiles)
        }

        adapter.setFiles(items)
        if (items.isEmpty()) tvPath.text = container.context.getString(R.string.empty_files, dir.absolutePath)
    }

    inner class FileAdapter : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {
        private val files = mutableListOf<File>()

        fun setFiles(newList: List<File>) {
            files.clear()
            files.addAll(newList)
            notifyDataSetChanged()
            if (files.isNotEmpty()) {
                rvFiles.post {
                    rvFiles.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() ?: rvFiles.requestFocus()
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return FileViewHolder(binding)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            holder.bind(files[position])
        }

        override fun getItemCount(): Int = files.size

        inner class FileViewHolder(private val binding: ItemFileBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(file: File) {
                if (file.isDirectory) {
                    binding.tvItemFileIcon.text = "📁"
                    binding.tvItemFileName.text = file.name
                } else {
                    binding.tvItemFileIcon.text = "📄"
                    binding.tvItemFileName.text = file.name
                }

                binding.root.setOnClickListener {
                    if (file.isDirectory) {
                        navigateTo(file)
                    } else {
                        close()
                        onFileSelected(file)
                    }
                }
            }
        }
    }
}
