package com.talha.kitty

class KittyEngine {

    companion object {
        val instance = KittyEngine()
    }

    /** Total amount a member has paid into the kitty across all cycles. */
    fun totalPaid(kitty: Kitty, memberId: String): Double =
        kitty.cycles.sumOf { c ->
            c.contributions[memberId]?.amount ?: 0.0
        }

    /** Whether every member has contributed to the given cycle. */
    fun isCycleComplete(kitty: Kitty, cycle: Cycle): Boolean =
        kitty.members.isNotEmpty() &&
            kitty.members.all { cycle.contributions.containsKey(it.id) }

    /** The outstanding amount a member still owes for a given cycle. */
    fun outstanding(kitty: Kitty, cycle: Cycle, memberId: String): Double {
        val paid = cycle.contributions[memberId]?.amount ?: 0.0
        return (kitty.contributionAmount - paid).coerceAtLeast(0.0)
    }

    /** Record a member's payment for a cycle. Creates or updates the contribution.
     *  Closed cycles are frozen and cannot be changed. */
    fun recordPayment(kitty: Kitty, cycle: Cycle, memberId: String, amount: Double, paidAtMillis: Long = System.currentTimeMillis()) {
        if (cycle.isClosed) return
        if (amount <= 0) return
        cycle.contributions[memberId] = Contribution(memberId, cycle.index, amount, paidAtMillis)
    }

    /** Remove a member's recorded payment for a cycle. Closed cycles are frozen. */
    fun undoPayment(kitty: Kitty, cycle: Cycle, memberId: String) {
        if (cycle.isClosed) return
        cycle.contributions.remove(memberId)
    }

    /** Close a cycle (the pot has been handed over). Does nothing if incomplete. */
    fun closeCycle(kitty: Kitty, cycle: Cycle): Boolean {
        if (!isCycleComplete(kitty, cycle)) return false
        cycle.isClosed = true
        return true
    }

    /** How many members have been paid out across all cycles. */
    fun payoutsMade(kitty: Kitty): Int = kitty.cycles.count { it.isClosed }

    /** The kitty's running total balance: everything collected minus nothing outstanding. */
    fun runningBalance(kitty: Kitty): Double =
        kitty.cycles.sumOf { it.contributions.values.sumOf { c -> c.amount } }

    /** Next cycle index to open (one past the last existing cycle). */
    fun nextCycleIndex(kitty: Kitty): Int = kitty.cycles.size

    /** Whether the kitty is rotationally balanced (each payee has had one turn). */
    fun isRotationBalanced(kitty: Kitty): Boolean {
        if (kitty.members.isEmpty()) return true
        val received = HashSet<String>()
        kitty.cycles.forEach { c ->
            if (c.isClosed) received.add(c.payoutMemberId)
        }
        return received.size == kitty.members.size
    }
}
