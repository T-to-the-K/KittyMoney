package com.talha.kitty

import android.graphics.Typeface
import android.os.Bundle
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BoardActivity : AppCompatActivity() {

    private val engine = KittyEngine.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_board)
        KittyStore.init(this)
        findViewById<TextView>(R.id.tvBoardTitle).text =
            "Spreadsheet — ${KittyStore.kitties.size} kitty(ies) running"
        buildTable()
    }

    private fun buildTable() {
        val table = findViewById<TableLayout>(R.id.boardTable)
        val kitties = KittyStore.kitties
        table.removeAllViews()
        if (kitties.isEmpty()) return

        val dp = resources.displayMetrics.density
        val textColor = 0xFF4A3F35.toInt()
        val accent = 0xFFC96A45.toInt()

        fun cell(text: String, bold: Boolean = false, color: Int = textColor): TextView {
            val tv = TextView(this)
            tv.text = text
            tv.textSize = 13f
            tv.setTypeface(null, if (bold) Typeface.BOLD else Typeface.NORMAL)
            tv.setTextColor(color)
            tv.setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
            return tv
        }

        val header = TableRow(this)
        header.addView(cell("Member", bold = true, color = accent))
        kitties.forEach { header.addView(cell(it.name, bold = true, color = accent)) }
        header.addView(cell("Total", bold = true, color = accent))
        table.addView(header)

        val members = LinkedHashMap<String, String>()
        kitties.forEach { k -> k.members.forEach { m -> members[m.id] = m.name } }

        members.forEach { (id, name) ->
            val row = TableRow(this)
            row.addView(cell(name))
            var memberTotal = 0.0
            kitties.forEach { k ->
                if (k.members.any { it.id == id }) {
                    val paid = engine.totalPaid(k, id)
                    memberTotal += paid
                    row.addView(cell(fmt(paid)))
                } else {
                    row.addView(cell("—"))
                }
            }
            row.addView(cell(fmt(memberTotal), bold = true))
            table.addView(row)
        }

        val totals = TableRow(this)
        totals.addView(cell("Collected", bold = true))
        var grand = 0.0
        kitties.forEach { k ->
            val b = engine.runningBalance(k)
            grand += b
            totals.addView(cell(fmt(b), bold = true))
        }
        totals.addView(cell(fmt(grand), bold = true))
        table.addView(totals)

        val targets = TableRow(this)
        targets.addView(cell("Target", bold = true))
        var grandTarget = 0.0
        kitties.forEach { k ->
            grandTarget += k.threshold
            targets.addView(cell(if (k.threshold > 0) fmt(k.threshold) else "—"))
        }
        targets.addView(cell(fmt(grandTarget)))
        table.addView(targets)

        val left = TableRow(this)
        left.addView(cell("Left", bold = true))
        var grandLeft = 0.0
        kitties.forEach { k ->
            val rem = if (k.threshold > 0) (k.threshold - engine.runningBalance(k)).coerceAtLeast(0.0) else 0.0
            grandLeft += rem
            left.addView(cell(if (k.threshold > 0) fmt(rem) else "—", color = 0xFFC4685A.toInt()))
        }
        left.addView(cell(fmt(grandLeft), color = 0xFFC4685A.toInt()))
        table.addView(left)
    }

    private fun fmt(n: Double): String =
        if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()
}