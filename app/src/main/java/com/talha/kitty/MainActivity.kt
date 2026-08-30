package com.talha.kitty

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: KittyAdapter
    private val engine = KittyEngine.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        KittyStore.init(this)

        recycler = findViewById(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = KittyAdapter()
        recycler.adapter = adapter

        findViewById<Button>(R.id.btnAdd).setOnClickListener { showAddKittyDialog() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        adapter.submit(KittyStore.kitties)
        val empty = findViewById<TextView>(R.id.txtEmpty)
        empty.visibility = if (KittyStore.kitties.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAddKittyDialog() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_add_kitty, null)
        val name = view.findViewById<EditText>(R.id.etName)

        AlertDialog.Builder(this)
            .setTitle("New Kitty")
            .setView(view)
            .setPositiveButton("Create") { _, _ ->
                val kittyName = name.text.toString().trim()
                if (kittyName.isNotEmpty()) {
                    KittyStore.add(Kitty(name = kittyName))
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class KittyAdapter : RecyclerView.Adapter<KittyAdapter.VH>() {
        private val items = ArrayList<Kitty>()

        fun submit(list: List<Kitty>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_kitty, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val k = items[position]
            holder.name.text = k.name
            holder.meta.text = "${k.members.size} members · each adds any amount"

            val nextPayee = k.payoutForCycle(engine.nextCycleIndex(k))
            val nextName = k.members.firstOrNull { it.id == nextPayee }?.name
            if (nextName != null) {
                holder.tag.text = "Next: $nextName"
                holder.tag.setTextColor(if (engine.isRotationBalanced(k)) 0xFF3E9B6E.toInt() else 0xFFD9A11C.toInt())
            } else {
                holder.tag.text = "New"
                holder.tag.setTextColor(0xFFC4685A.toInt())
            }

            holder.itemView.setOnClickListener {
                val intent = Intent(this@MainActivity, KittyDetailActivity::class.java)
                intent.putExtra(KittyDetailActivity.EXTRA_KITTY_ID, k.id)
                startActivity(intent)
            }
            holder.itemView.setOnLongClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Delete ${k.name}?")
                    .setMessage("This removes the kitty and all its records.")
                    .setPositiveButton("Delete") { _, _ ->
                        KittyStore.remove(k)
                        refresh()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
        }

        override fun getItemCount(): Int = items.size

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvName)
            val meta: TextView = v.findViewById(R.id.tvMeta)
            val tag: TextView = v.findViewById(R.id.tvTag)
        }
    }
}
