package com.talha.kitty

import java.util.UUID

data class Member(
    val id: String = UUID.randomUUID().toString(),
    val name: String
)

data class Contribution(
    val memberId: String,
    val cycleIndex: Int,
    val amount: Double,
    val paidAtMillis: Long
)

data class Cycle(
    val index: Int,
    val payoutMemberId: String,
    val contributions: MutableMap<String, Contribution> = HashMap(),
    var isClosed: Boolean = false
)

data class Kitty(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val contributionAmount: Double,
    val members: MutableList<Member> = ArrayList(),
    var cycles: MutableList<Cycle> = ArrayList()
) {

    /** Round-robin payout: cycle i is paid to members[i % members.size]. */
    fun payoutForCycle(index: Int): String? =
        if (members.isEmpty()) null else members[index % members.size].id

    fun expectedTotalForCycle(): Double = contributionAmount * members.size

    fun potCollected(cycle: Cycle): Double =
        cycle.contributions.values.sumOf { it.amount }
}
