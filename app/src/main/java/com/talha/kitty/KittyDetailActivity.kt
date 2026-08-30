package com.talha.kitty

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KittyDetailActivity : AppCompatActivity() {

    private val engine = KittyEngine.instance
    private var kitty: Kitty? = null

    private lateinit var tvName: TextView
    private lateinit var tvMeta: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvNextPayout: TextView
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
        tvNextPayout = findViewById(R.id.tvNextPayout)
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
        tvMeta.text = "${k.members.size} members · each adds any amount"

        val collected = engine.runningBalance(k)
        val complete = k.cycles.count { it.isClosed }
        tvStats.text = "Total collected:  $collected\nCycles completed:  $complete / ${k.cycles.size}"

        val nextIndex = engine.nextCycleIndex(k)
        val nextPayeeId = k.payoutForCycle(nextIndex)
        val nextName = k.members.firstOrNull { it.id == nextPayeeId }?.name
        tvNextPayout.text =
            if (nextName != null) "Next payout:  ${nextName} (cycle ${nextIndex + 1})"
            else "Add members to see who collects next."

        membersContainer.removeAllViews()
        k.members.forEach { m ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_member, membersContainer, false)
            row.findViewById<TextView>(R.id.tvMemberName).text = m.name
            val paid = engine.totalPaid(k, m.id)
            row.findViewById<TextView>(R.id.tvMemberStatus).text =
                if (paid > 0) "Paid in total:  $paid" else "No contributions yet"
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
        val summary = v.findViewById<TextView>(R.id.tvCycleSummary)
        val memberBox = v.findViewById<LinearLayout>(R.id.cycleMembers)
        val btnClose = v.findViewById<Button>(R.id.btnCloseCycle)

        title.text = "Cycle ${cycle.index + 1} — pays ${payee}"
        val pot = k.potCollected(cycle)
        summary.text = "Collected so far:  $pot"

        val complete = engine.isCycleComplete(k, cycle)
        status.text = when {
            cycle.isClosed -> "Done"
            complete -> "Ready"
            else -> "${k.members.size - cycle.contributions.size} to pay"
        }
        status.setTextColor(
            when {
                cycle.isClosed -> 0xFF3E9B6E.toInt()
                complete -> 0xFF3E9B6E.toInt()
                else -> 0xFFD9A11C.toInt()
            }
        )

        memberBox.removeAllViews()
        val frozen = cycle.isClosed
        k.members.forEach { m ->
            val row = Button(this)
            val contribution = cycle.contributions[m.id]
            val amount = contribution?.amount ?: 0.0
            row.text = if (amount > 0)
                "👤 ${m.name}\n   gave $amount (${formatTime(contribution!!.paidAtMillis)})"
            else
                "👤 ${m.name}\n   tap to enter amount"
            row.setBackgroundResource(R.drawable.bg_tag)
            row.setTextColor(if (amount > 0) 0xFF3E9B6E.toInt() else 0xFFC4685A.toInt())
            row.isEnabled = !frozen
            row.setOnClickListener {
                showContributionDialog(k, cycle, m)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val dp = resources.displayMetrics.density
            lp.setMargins(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
            row.layoutParams = lp
            memberBox.addView(row)
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

    private fun showContributionDialog(k: Kitty, cycle: Cycle, m: Member) {
        val input = EditText(this)
        input.hint = "Amount ${m.name} can give"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        val existing = cycle.contributions[m.id]?.amount
        if (existing != null && existing > 0) input.setText(existing.toString())
        AlertDialog.Builder(this)
            .setTitle("Contribution — ${m.name}")
            .setMessage("Enter any amount. Leave blank or 0 to remove.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val text = input.text.toString().trim()
                val amount = text.toDoubleOrNull() ?: 0.0
                if (amount > 0) {
                    engine.recordPayment(k, cycle, m.id, amount)
                } else {
                    engine.undoPayment(k, cycle, m.id)
                }
                KittyStore.update()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    private fun formatTime(millis: Long): String {
        val fmt = SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault())
        return fmt.format(Date(millis))
    }
}
