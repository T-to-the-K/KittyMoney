package com.talha.kitty

import java.util.UUID
import kotlin.math.floor

data class Member(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** How many shares (slots) this member holds. Multiples of 0.5. 1 = standard, 2 = double slot, 0.5 = half share. */
    val shares: Double = 1.0
)

data class Contribution(
    val memberId: String,
    val cycleIndex: Int,
    val amount: Double,
    val paidAtMillis: Long
)

/**
 * A payout within a cycle. [fraction] is the share of the pot a member receives
 * (1.0 for a full slot, 0.5 for a half-slot, etc.).
 */
data class Payout(
    val memberId: String,
    val fraction: Double
)

data class Cycle(
    val index: Int,
    val payoutMemberId: String,
    val payouts: MutableList<Payout> = ArrayList(),
    val contributions: MutableMap<String, Contribution> = HashMap(),
    var isClosed: Boolean = false,
    /** True for months that were paid out before this app was adopted. Frozen history. */
    var imported: Boolean = false
) {
    init {
        if (payouts.isEmpty()) payouts.add(Payout(payoutMemberId, 1.0))
    }
}

data class Kitty(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Total amount the kitty must generate (the threshold). 0 = free-form ("any amount") mode. */
    val threshold: Double = 0.0,
    /** How many months the kitty runs. 0 = auto (one month per payout slot). */
    val durationMonths: Int = 0,
    val members: MutableList<Member> = ArrayList(),
    var cycles: MutableList<Cycle> = ArrayList()
) {

    /** Total shares across all members (a half share counts 0.5). */
    fun totalShares(): Double = members.sumOf { it.shares }

    /**
     * Full-rotation payout schedule. Each entry is one cycle's payee(s).
     * - A whole share = one slot paying the full pot.
     * - Half shares are paired into a shared slot: each pays/receives half the pot.
     * - An unpaired half becomes its own half-pot slot (it settles over two rotations).
     */
    fun buildSchedule(): List<MutableList<Payout>> {
        val schedule = ArrayList<MutableList<Payout>>()
        val halfFragments = ArrayList<String>()
        for (m in members) {
            val whole = floor(m.shares).toInt()
            repeat(whole) { schedule.add(mutableListOf(Payout(m.id, 1.0))) }
            if (m.shares - whole >= 0.49) halfFragments.add(m.id)
        }
        var i = 0
        while (i + 1 < halfFragments.size) {
            schedule.add(
                mutableListOf(
                    Payout(halfFragments[i], 0.5),
                    Payout(halfFragments[i + 1], 0.5)
                )
            )
            i += 2
        }
        if (i < halfFragments.size) {
            schedule.add(mutableListOf(Payout(halfFragments[i], 0.5)))
        }
        return schedule
    }

    /** Payees for the given cycle index (fallback to simple round-robin by member). */
    fun payeesForCycle(index: Int): List<Payout> {
        val s = buildSchedule()
        if (s.isNotEmpty()) return s[index % s.size]
        return if (members.isEmpty()) emptyList()
        else listOf(Payout(members[index % members.size].id, 1.0))
    }

    fun potCollected(cycle: Cycle): Double =
        cycle.contributions.values.sumOf { it.amount }

    /** Total months in the kitty period. 0 duration = one month per payout slot. */
    fun monthsTotal(): Int = if (durationMonths > 0) durationMonths else buildSchedule().size

    /** Derived payment per full share per month so the kitty lands exactly on [threshold].
     *  0 when the kitty is in free-form mode. */
    fun perFullShareMonth(): Double {
        val months = monthsTotal()
        val shares = totalShares()
        return if (threshold > 0 && months > 0 && shares > 0) threshold / (months * shares) else 0.0
    }

    /** Months already paid out before the app was adopted. */
    fun monthsImported(): Int = cycles.count { it.imported }

    /**
     * Seed history for a kitty that was already running before this app:
     * marks the first [completed] months as closed, imported cycles whose payees
     * follow the share schedule. Returns how many months were seeded.
     */
    fun seedImportedMonths(completed: Int): Int {
        if (completed <= 0 || cycles.isNotEmpty()) return 0
        val schedule = buildSchedule()
        for (i in 0 until completed) {
            val payees = if (schedule.isNotEmpty()) schedule[i % schedule.size]
            else mutableListOf(Payout("", 1.0))
            val cycle = Cycle(
                index = i,
                payoutMemberId = payees.first().memberId,
                isClosed = true,
                imported = true
            )
            cycle.payouts.clear()
            cycle.payouts.addAll(payees)
            cycles.add(cycle)
        }
        return completed
    }
}