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
        findViewById<Button>(R.id.btnAdopt).setOnClickListener { showAdoptDialog() }
        render()
    }

    private fun render() {
        val k = kitty ?: return
        tvName.text = k.name
        tvMeta.text = buildString {
            append("${k.members.size} members")
            if (k.members.isNotEmpty()) append(" · runs ${k.monthsTotal()} months")
            if (k.potAmount > 0) {
                val total = k.totalShares()
                append(" · ${fmt(total)} shares")
                append("\nPot to hand over each month:  ${fmt(engine.potExpected(k))}")
            } else {
                append(" · members add any amount")
            }
        }

        val collected = engine.runningBalance(k)
        val closed = k.cycles.count { it.isClosed }
        val totalMonths = k.monthsTotal()
        val imported = k.monthsImported()
        tvStats.text = buildString {
            if (k.potAmount > 0) {
                append("Pot per month:  ${fmt(k.potAmount)}\n")
                append("Collected so far:  ${fmt(collected)}\n")
            } else {
                append("Total collected:  $collected\n")
            }
            append("Months done:  ${closed} / ${totalMonths}\n")
            append("Imported before app:  $imported")
        }

        val activeIdx = activeMonthIndex(k)
        val payees = activePayees(k, activeIdx)
        tvNextPayout.text =
            if (payees.isNotEmpty()) {
                val receives =
                    if (k.potAmount > 0)
                        payees.joinToString(" & ") { p ->
                            val n = k.members.firstOrNull { it.id == p.memberId }?.name ?: "?"
                            "$n  →  ${fmt(engine.potExpected(k) * p.fraction)}"
                        }
                    else
                        payees.mapNotNull { p ->
                            k.members.firstOrNull { it.id == p.memberId }?.name?.let { n -> labelFor(n, p.fraction) }
                        }.joinToString(" & ")
                "Month ${activeIdx + 1}:  $receives"
            } else {
                "Add members to see who collects this month."
            }

        membersContainer.removeAllViews()
        k.members.forEach { m ->
            val fraction = payees.firstOrNull { it.memberId == m.id }?.fraction ?: 0.0
            membersContainer.addView(buildMemberRow(k, m, fraction, activeIdx))
        }

        findViewById<Button>(R.id.btnAdopt).visibility = if (k.cycles.isEmpty()) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnAddCycle).text = "+ Start Month ${k.cycles.size + 1}"

        cyclesContainer.removeAllViews()
        k.cycles.forEach { cycle -> cyclesContainer.addView(buildCycleView(k, cycle)) }
    }

    private fun activeMonthIndex(k: Kitty): Int =
        k.cycles.lastOrNull()?.let { if (it.isClosed) null else it.index } ?: k.cycles.size

    private fun activePayees(k: Kitty, activeIdx: Int): List<Payout> {
        val open = k.cycles.lastOrNull()?.takeIf { !it.isClosed }
        return open?.payouts ?: k.payeesForCycle(activeIdx)
    }

    private fun buildMemberRow(k: Kitty, m: Member, activeFraction: Double, activeMonth: Int): View {
        val row = LayoutInflater.from(this).inflate(R.layout.item_member, membersContainer, false)
        val active = activeFraction > 0
        row.setBackgroundResource(if (active) R.drawable.bg_member_active else R.drawable.bg_card_soft)
        val nameView = row.findViewById<TextView>(R.id.tvMemberName)
        nameView.text = "${m.name} — ${fmt(m.shares)} share${if (m.shares == 1.0) "" else "s"}"
        nameView.setTextColor(if (active) 0xFFC96A45.toInt() else 0xFF4A3F35.toInt())
        val paid = engine.totalPaid(k, m.id)
        row.findViewById<TextView>(R.id.tvMemberStatus).text = buildString {
            if (active) {
                append("◆ Collects Month ${activeMonth + 1}")
                if (k.potAmount > 0) append(" · receives ${fmt(engine.potExpected(k) * activeFraction)}")
            }
            if (paid > 0) append(" · paid ${fmt(paid)} in total")
        }
        row.findViewById<Button>(R.id.btnShares).setOnClickListener { showSharesDialog(k, m) }
        row.findViewById<Button>(R.id.btnRemoveMember).setOnClickListener {
            k.members.remove(m)
            KittyStore.update()
            render()
        }
        return row
    }

    private fun buildCycleView(k: Kitty, cycle: Cycle): View {
        val v = LayoutInflater.from(this).inflate(R.layout.item_cycle, cyclesContainer, false)
        val title = v.findViewById<TextView>(R.id.tvCycleTitle)
        val status = v.findViewById<TextView>(R.id.tvCycleStatus)
        val summary = v.findViewById<TextView>(R.id.tvCycleSummary)
        val memberBox = v.findViewById<LinearLayout>(R.id.cycleMembers)
        val btnClose = v.findViewById<Button>(R.id.btnCloseCycle)

        val payeeLabels = cycle.payouts.mapNotNull { p ->
            k.members.firstOrNull { it.id == p.memberId }?.name?.let { n -> labelFor(n, p.fraction) }
        }
        title.text = if (k.potAmount > 0)
            "Month ${cycle.index + 1} — pays ${cycle.payouts.joinToString(" & ") { p ->
                val n = k.members.firstOrNull { it.id == p.memberId }?.name ?: "?"
                "$n  →  ${fmt(engine.potExpected(k) * p.fraction)}"
            }}"
        else
            "Month ${cycle.index + 1} — pays ${payeeLabels.joinToString(" & ")}"

        val complete = engine.isCycleComplete(k, cycle)
        status.text = when {
            cycle.imported -> "Done · before app"
            cycle.isClosed -> "Done"
            complete -> "Ready"
            else -> "${k.members.size - cycle.contributions.size} to pay"
        }
        status.setTextColor(
            when {
                cycle.imported || cycle.isClosed || complete -> 0xFF3E9B6E.toInt()
                else -> 0xFFD9A11C.toInt()
            }
        )

        if (cycle.imported) {
            summary.text = if (k.potAmount > 0)
                "Paid before the app · pot ${fmt(engine.potExpected(k))} handed over"
            else
                "Paid before the app."
            memberBox.visibility = View.GONE
            btnClose.visibility = View.GONE
            return v
        }

        val pot = k.potCollected(cycle)
        summary.text = if (k.potAmount > 0)
            "Collected ${fmt(pot)} of expected pot ${fmt(engine.potExpected(k))}"
        else
            "Collected so far:  ${fmt(pot)}"

        memberBox.removeAllViews()
        val frozen = cycle.isClosed
        val dp = resources.displayMetrics.density
        k.members.forEach { m ->
            val row = Button(this)
            val contribution = cycle.contributions[m.id]
            val amount = contribution?.amount ?: 0.0
            row.text = if (amount > 0)
                "👤 ${m.name}\n   gave ${fmt(amount)} (${formatTime(contribution!!.paidAtMillis)})"
            else
                "👤 ${m.name} · ${fmt(m.shares)} share${if (m.shares == 1.0) "" else "s"}\n   tap to enter amount"
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
                Toast.makeText(this, "All members must contribute before closing", Toast.LENGTH_SHORT).show()
            }
        }
        return v
    }

    private fun showContributionDialog(k: Kitty, cycle: Cycle, m: Member) {
        val input = EditText(this)
        input.hint = "Amount ${m.name} actually gave"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        val existing = cycle.contributions[m.id]?.amount
        if (existing != null && existing > 0) input.setText(existing.toString())
        alertWithTitle("Contribution — ${m.name}",
            "It's on the manager: record what ${m.name} actually gave (full, half, or anything in between). Leave blank or 0 to remove.",
            input,
            k, cycle, m)
    }

    private fun alertWithTitle(title: String, message: String, input: EditText, k: Kitty, cycle: Cycle, m: Member) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
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

    private fun showSharesDialog(k: Kitty, m: Member) {
        val input = EditText(this)
        input.hint = "Shares (0.5, 1, 1.5, 2, ...)"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setText(m.shares.toString())
        AlertDialog.Builder(this)
            .setTitle("Shares — ${m.name}")
            .setMessage("Doubles pay twice & collect twice. A half share splits one slot — two half shares share a payout.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val shares = input.text.toString().trim().toDoubleOrNull()
                if (shares != null && shares >= 0.5) {
                    val idx = k.members.indexOf(m)
                    if (idx >= 0) {
                        k.members[idx] = m.copy(shares = roundHalf(shares))
                        KittyStore.update()
                        render()
                    }
                } else {
                    Toast.makeText(this, "Shares must be at least 0.5", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAdoptDialog() {
        val k = kitty ?: return
        if (k.members.isEmpty()) {
            Toast.makeText(this, "Add your members first, then adopt the running kitty", Toast.LENGTH_LONG).show()
            return
        }
        if (k.cycles.isNotEmpty()) {
            Toast.makeText(this, "Adopt before starting months — months are already open", Toast.LENGTH_LONG).show()
            return
        }
        val input = EditText(this)
        input.hint = "Already-done months (e.g. 6)"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        AlertDialog.Builder(this)
            .setTitle("Adopt running kitty")
            .setMessage("How many months of this kitty have already been paid out before you started using the app? Those months become done-only records and their money stays outside the app.")
            .setView(input)
            .setPositiveButton("Adopt") { _, _ ->
                val months = input.text.toString().trim().toIntOrNull()
                if (months != null && months >= 0) {
                    val seeded = k.seedImportedMonths(months)
                    KittyStore.update()
                    render()
                    if (seeded > 0) {
                        Toast.makeText(this, "Marked $seeded months as done", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddMemberDialog() {
        val k = kitty ?: return
        val input = EditText(this)
        input.hint = "Member names (one per line)"
        AlertDialog.Builder(this)
            .setTitle("Add Member(s)")
            .setMessage("One name per line. Two people sharing a half share go on one line as \"Name1, Name2\" — the comma makes them a half pair.")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val added = parseMembers(input.text.toString())
                if (added.isNotEmpty()) {
                    k.members.addAll(added)
                    KittyStore.update()
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** A lone name = full share. "Name1, Name2" on one line = two people, half share each. */
    private fun parseMembers(raw: String): List<Member> {
        val result = ArrayList<Member>()
        for (line in raw.split('\n')) {
            val parts = line.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (parts.size == 2) {
                result.add(Member(name = parts[0], shares = 0.5))
                result.add(Member(name = parts[1], shares = 0.5))
            } else {
                parts.forEach { p -> result.add(Member(name = p)) }
            }
        }
        return result
    }

    private fun addCycle() {
        val k = kitty ?: return
        if (k.members.isEmpty()) {
            Toast.makeText(this, "Add at least one member first", Toast.LENGTH_SHORT).show()
            return
        }
        val index = engine.nextCycleIndex(k)
        val payees = engine.payeesForCycle(k, index)
        if (payees.isEmpty()) return
        val cycle = Cycle(index = index, payoutMemberId = payees.first().memberId)
        cycle.payouts.clear()
        cycle.payouts.addAll(payees)
        k.cycles.add(cycle)
        KittyStore.update()
        render()
    }

    private fun roundHalf(v: Double): Double = Math.round(v * 2) / 2.0

    private fun labelFor(name: String, fraction: Double): String =
        if (fraction >= 1.0) name else "$name (half)"

    private fun fmt(n: Double): String =
        if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()

    private fun formatTime(millis: Long): String {
        val fmt = SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault())
        return fmt.format(Date(millis))
    }
}