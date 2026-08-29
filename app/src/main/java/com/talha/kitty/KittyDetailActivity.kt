package com.talha.kitty

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class KittyDetailActivity : AppCompatActivity() {

    private val engine = KittyEngine.instance
    private var kitty: Kitty? = null

    private lateinit var tvName: TextView
    private lateinit var tvMeta: TextView
    private lateinit var tvStats: TextView
    private lateinit var membersContainer: LinearLayout
    private lateinit var cyclesContainer: LinearLayout

    companion object {
        const val EXTRA_KITTY_ID = "kitty_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kitty_detail)
        KittyStore.init(this)

        tvName = findViewById(R.id.tvKittyName)
        tvMeta = findViewById(R.id.tvKittyMeta)
        tvStats = findViewById(R.id.tvStats)
        membersContainer = findViewById(R.id.membersContainer)
        cyclesContainer = findViewById(R.id.cyclesContainer)

        val id = intent.getStringExtra(EXTRA_KITTY_ID)
        kitty = KittyStore.kitties.firstOrNull { it.id == id }
        if (kitty == null) {
            finish()
            return
        }

        findViewById<Button>(R.id.btnAddMember).setOnClickListener { showAddMemberDialog() }
        findViewById<Button>(R.id.btnAddCycle).setOnClickListener { addCycle() }
        render()
    }

    private fun render() {
        val k = kitty ?: return
        tvName.text = k.name
        tvMeta.text = "${k.members.size} members · ${k.contributionAmount} each per cycle"

        val collected = engine.runningBalance(k)
        val complete = k.cycles.count { it.isClosed }
        tvStats.text = "Total collected: $collected\nCycles completed: $complete / ${k.cycles.size}"

        membersContainer.removeAllViews()
        k.members.forEach { m ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_member, membersContainer, false)
            row.findViewById<TextView>(R.id.tvMemberName).text = m.name
            row.findViewById<Button>(R.id.btnRemoveMember).setOnClickListener {
                k.members.remove(m)
                KittyStore.update()
                render()
            }
            membersContainer.addView(row)
        }

        cyclesContainer.removeAllViews()
        k.cycles.forEach { cycle -> cyclesContainer.addView(buildCycleView(k, cycle)) }
    }

    private fun buildCycleView(k: Kitty, cycle: Cycle): View {
        val v = LayoutInflater.from(this).inflate(R.layout.item_cycle, cyclesContainer, false)
        val payee = k.members.firstOrNull { it.id == cycle.payoutMemberId }?.name ?: "?"
        val title = v.findViewById<TextView>(R.id.tvCycleTitle)
        val status = v.findViewById<TextView>(R.id.tvCycleStatus)
        val memberBox = v.findViewById<GridLayout>(R.id.cycleMembers)
        val btnClose = v.findViewById<Button>(R.id.btnCloseCycle)

        title.text = "Cycle ${cycle.index + 1} — pays ${payee}"
        val pot = k.potCollected(cycle)
        val expected = k.expectedTotalForCycle()
        status.text = "Collected $pot / $expected"

        memberBox.removeAllViews()
        val cols = when {
            k.members.isEmpty() -> 1
            k.members.size <= 4 -> k.members.size
            k.members.size <= 8 -> 2
            else -> 3
        }
        memberBox.columnCount = cols
        k.members.forEach { m ->
            val cb = CheckBox(this)
            cb.text = m.name
            cb.isChecked = cycle.contributions.containsKey(m.id)
            cb.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    engine.recordPayment(k, cycle, m.id, k.contributionAmount)
                } else {
                    engine.undoPayment(k, cycle, m.id)
                }
                KittyStore.update()
                render()
            }
            val lp = GridLayout.LayoutParams()
            lp.width = 0
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            cb.layoutParams = lp
            memberBox.addView(cb)
        }

        btnClose.text = if (cycle.isClosed) "Closed ✓" else "Close Cycle (hand over pot)"
        btnClose.isEnabled = !cycle.isClosed
        btnClose.setOnClickListener {
            if (engine.closeCycle(k, cycle)) {
                KittyStore.update()
                Toast.makeText(this, "Cycle ${cycle.index + 1} closed", Toast.LENGTH_SHORT).show()
                render()
            } else {
                Toast.makeText(this, "All members must pay before closing", Toast.LENGTH_SHORT).show()
            }
        }
        return v
    }

    private fun showAddMemberDialog() {
        val k = kitty ?: return
        val input = EditText(this)
        input.hint = "Member name"
        AlertDialog.Builder(this)
            .setTitle("Add Member")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    k.members.add(Member(name = name))
                    KittyStore.update()
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addCycle() {
        val k = kitty ?: return
        if (k.members.isEmpty()) {
            Toast.makeText(this, "Add at least one member first", Toast.LENGTH_SHORT).show()
            return
        }
        val index = engine.nextCycleIndex(k)
        val payeeId = k.payoutForCycle(index) ?: return
        k.cycles.add(Cycle(index = index, payoutMemberId = payeeId))
        KittyStore.update()
        render()
    }
}
