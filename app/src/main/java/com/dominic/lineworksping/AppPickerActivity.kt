package com.dominic.lineworksping

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dominic.lineworksping.databinding.ActivityAppPickerBinding

/** Lets the user choose which installed apps' notifications should be monitored. */
class AppPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppPickerBinding
    private lateinit var settings: SettingsStore
    private val selected = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = SettingsStore(this)
        selected.addAll(settings.monitoredPackages)

        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null || it.packageName in selected }
            .map { AppItem(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = AppAdapter(apps)
    }

    override fun onPause() {
        super.onPause()
        settings.monitoredPackages = HashSet(selected)
    }

    data class AppItem(val pkg: String, val label: String)

    private inner class AppAdapter(val items: List<AppItem>) :
        RecyclerView.Adapter<AppAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.appIcon)
            val label: TextView = v.findViewById(R.id.appLabel)
            val pkg: TextView = v.findViewById(R.id.appPkg)
            val check: CheckBox = v.findViewById(R.id.appCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.label.text = item.label
            holder.pkg.text = item.pkg
            holder.icon.setImageDrawable(
                try {
                    packageManager.getApplicationIcon(item.pkg)
                } catch (e: Exception) {
                    null
                }
            )
            holder.check.isChecked = item.pkg in selected

            val toggle = View.OnClickListener {
                if (item.pkg in selected) selected.remove(item.pkg) else selected.add(item.pkg)
                holder.check.isChecked = item.pkg in selected
            }
            holder.itemView.setOnClickListener(toggle)
            holder.check.setOnClickListener(toggle)
        }
    }
}
