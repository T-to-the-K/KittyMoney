package com.talha.kitty

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lightweight JSON persistence for kitty records, stored in app-private storage.
 * Uses org.json (bundled with Android) so no extra dependencies are required.
 */
class KittyRepository(private val context: Context) {

    private val file: File
        get() = File(context.filesDir, "kitties.json")

    fun load(): MutableList<Kitty> {
        val f = file
        if (!f.exists()) return ArrayList()
        return try {
            val arr = JSONArray(f.readText())
            val out = ArrayList<Kitty>()
            for (i in 0 until arr.length()) {
                out.add(fromJson(arr.getJSONObject(i)))
            }
            out
        } catch (t: Throwable) {
            ArrayList()
        }
    }

    fun save(kitties: List<Kitty>) {
        val arr = JSONArray()
        kitties.forEach { arr.put(toJson(it)) }
        file.writeText(arr.toString())
    }

    private fun toJson(k: Kitty): JSONObject {
        val o = JSONObject()
        o.put("id", k.id)
        o.put("name", k.name)
        o.put("threshold", k.threshold)
        o.put("durationMonths", k.durationMonths)

        val members = JSONArray()
        k.members.forEach { m ->
            val mo = JSONObject()
            mo.put("id", m.id)
            mo.put("name", m.name)
            mo.put("shares", m.shares)
            members.put(mo)
        }
        o.put("members", members)

        val cycles = JSONArray()
        k.cycles.forEach { c ->
            val co = JSONObject()
            co.put("index", c.index)
            co.put("payoutMemberId", c.payoutMemberId)
            co.put("isClosed", c.isClosed)
            co.put("imported", c.imported)
            val payouts = JSONArray()
            c.payouts.forEach { p ->
                val po = JSONObject()
                po.put("memberId", p.memberId)
                po.put("fraction", p.fraction)
                payouts.put(po)
            }
            co.put("payouts", payouts)
            val contributions = JSONArray()
            c.contributions.values.forEach { con ->
                val cono = JSONObject()
                cono.put("memberId", con.memberId)
                cono.put("cycleIndex", con.cycleIndex)
                cono.put("amount", con.amount)
                cono.put("paidAtMillis", con.paidAtMillis)
                contributions.put(cono)
            }
            co.put("contributions", contributions)
            cycles.put(co)
        }
        o.put("cycles", cycles)
        return o
    }

    private fun fromJson(o: JSONObject): Kitty {
        val members = ArrayList<Member>()
        val ma = o.optJSONArray("members")
        if (ma != null) {
            for (i in 0 until ma.length()) {
                val m = ma.getJSONObject(i)
                members.add(Member(id = m.optString("id"), name = m.optString("name"), shares = m.optDouble("shares", 1.0)))
            }
        }
        // Legacy migration: v1.x stored a fixed amount per share per cycle. Convert it
        // into a threshold so that the derived per-share payment stays exactly the same.
        val hasThreshold = o.has("threshold")
        var threshold = if (hasThreshold) o.optDouble("threshold") else 0.0
        if (!hasThreshold) {
            val legacyShare = if (o.has("shareAmount")) o.optDouble("shareAmount")
            else o.optDouble("contributionAmount")
            if (legacyShare > 0) {
                val k = Kitty(name = "ns", members = members)
                val months = k.monthsTotal()
                val shares = k.totalShares()
                if (months > 0 && shares > 0) threshold = legacyShare * months * shares
            }
        }
        var k = Kitty(
            id = o.optString("id"),
            name = o.optString("name"),
            threshold = threshold,
            durationMonths = o.optInt("durationMonths", 0),
            members = members
        )
        val ca = o.optJSONArray("cycles")
        if (ca != null) {
            for (i in 0 until ca.length()) {
                val c = ca.getJSONObject(i)
                val cycle = Cycle(
                    index = c.optInt("index"),
                    payoutMemberId = c.optString("payoutMemberId")
                )
                cycle.isClosed = c.optBoolean("isClosed")
                cycle.imported = c.optBoolean("imported")
                val pa = c.optJSONArray("payouts")
                if (pa != null && pa.length() > 0) {
                    cycle.payouts.clear()
                    for (j in 0 until pa.length()) {
                        val po = pa.getJSONObject(j)
                        cycle.payouts.add(Payout(po.optString("memberId"), po.optDouble("fraction", 1.0)))
                    }
                }
                val cona = c.optJSONArray("contributions")
                if (cona != null) {
                    for (j in 0 until cona.length()) {
                        val con = cona.getJSONObject(j)
                        cycle.contributions[con.optString("memberId")] = Contribution(
                            memberId = con.optString("memberId"),
                            cycleIndex = con.optInt("cycleIndex"),
                            amount = con.optDouble("amount"),
                            paidAtMillis = con.optLong("paidAtMillis")
                        )
                    }
                }
                k.cycles.add(cycle)
            }
        }
        return k
    }
}