package com.dominic.lineworksping

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dominic.lineworksping.databinding.ActivityRecentBinding

/** Shows the recent notifications the listener saw and how each was classified. */
class RecentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecentBinding
    private val adapter = EventAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.btnClear.setOnClickListener {
            EventLog.clear()
            refresh()
        }
        binding.btnRefresh.setOnClickListener { refresh() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val items = EventLog.snapshot()
        adapter.submit(items)
        binding.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private inner class EventAdapter : RecyclerView.Adapter<EventAdapter.VH>() {
        private var items: List<EventLog.Entry> = emptyList()

        fun submit(list: List<EventLog.Entry>) {
            items = list
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val line1: TextView = v.findViewById(R.id.eventLine1)
            val line2: TextView = v.findViewById(R.id.eventLine2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            val time = DateUtils.getRelativeTimeSpanString(
                e.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
            val marker = if (e.pinged) "🔔 " else ""
            val head = if (e.label.isNotBlank()) e.label else null
            holder.line1.text = listOfNotNull(marker.ifBlank { null }, head, e.decision)
                .joinToString(" · ")
                .ifBlank { e.text }
            holder.line2.text = listOfNotNull(
                e.text.takeIf { it.isNotBlank() && (head != null) },
                time.toString()
            ).joinToString("  •  ")

            holder.itemView.setOnClickListener { showDetail(e) }
        }
    }

    private fun showDetail(e: EventLog.Entry) {
        val body = e.detail.ifBlank { e.text.ifBlank { e.decision } }
        AlertDialog.Builder(this)
            .setTitle(R.string.detail_title)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.copy) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("notification detail", body))
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
