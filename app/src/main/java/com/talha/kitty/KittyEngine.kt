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

    /** Number of cycles in a full rotation (one payout per slot across members' shares). */
    fun rotationLength(kitty: Kitty): Int = kitty.buildSchedule().size

    /** Payees for the next/new cycle at the given slot index. Each has a pot fraction. */
    fun payeesForCycle(kitty: Kitty, cycleIndex: Int): List<Payout> =
        kitty.payeesForCycle(cycleIndex)

    /** The amount a member is expected to contribute per month given their shares. 0 = any amount. */
    fun expectedContribution(kitty: Kitty, member: Member): Double =
        kitty.perFullShareMonth() * member.shares

    /** The pot a collector receives per month (the potAmount itself). 0 = any amount. */
    fun potExpected(kitty: Kitty): Double =
        if (kitty.potAmount > 0) kitty.potAmount else 0.0

    /** Whether an entered amount matches what the member's share requires to build the pot.
     *  Free-form kits always accept everything. */
    fun isExactPayment(kitty: Kitty, member: Member, amount: Double): Boolean {
        if (kitty.potAmount <= 0) return true
        val expected = expectedContribution(kitty, member)
        return Math.round(expected * 100) == Math.round(amount * 100)
    }

    /** Whether every member has contributed to the given cycle. */
    fun isCycleComplete(kitty: Kitty, cycle: Cycle): Boolean =
        kitty.members.isNotEmpty() &&
            kitty.members.all { cycle.contributions.containsKey(it.id) }

    /** Record a member's payment for a cycle. Creates or updates the contribution.
     *  Closed cycles are frozen. Fixed kits reject any amount that isn't the exact
     *  share-based figure required to build the monthly pot. */
    fun recordPayment(kitty: Kitty, cycle: Cycle, memberId: String, amount: Double, paidAtMillis: Long = System.currentTimeMillis()): Boolean {
        if (cycle.isClosed) return false
        if (amount <= 0) return false
        val member = kitty.members.firstOrNull { it.id == memberId } ?: return false
        if (kitty.potAmount > 0 && !isExactPayment(kitty, member, amount)) return false
        cycle.contributions[memberId] = Contribution(memberId, cycle.index, amount, paidAtMillis)
        return true
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

    /** The kitty's running total balance: everything collected. */
    fun runningBalance(kitty: Kitty): Double =
        kitty.cycles.sumOf { it.contributions.values.sumOf { c -> c.amount } }

    /** Next cycle index to open (one past the last existing cycle). */
    fun nextCycleIndex(kitty: Kitty): Int = kitty.cycles.size

    /** Whether a full rotation has been paid out. */
    fun isRotationBalanced(kitty: Kitty): Boolean {
        if (kitty.members.isEmpty()) return true
        val closed = kitty.cycles.count { it.isClosed }
        return closed >= rotationLength(kitty)
    }
}