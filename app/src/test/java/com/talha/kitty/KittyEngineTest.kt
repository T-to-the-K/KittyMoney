package com.talha.kitty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KittyEngineTest {

    private val engine = KittyEngine.instance

    private fun kittyWith3Members(name: String = "Test", shareAmount: Double = 0.0): Kitty {
        val k = Kitty(name = name, shareAmount = shareAmount)
        k.members.add(Member(id = "a", name = "A"))
        k.members.add(Member(id = "b", name = "B"))
        k.members.add(Member(id = "c", name = "C"))
        return k
    }

    @Test
    fun `one share per member rotates round-robin`() {
        val k = kittyWith3Members()
        val order = k.buildSchedule().map { it.single().memberId }
        assertEquals(listOf("a", "b", "c"), order)
        assertEquals(3, engine.rotationLength(k))
        assertEquals("a", engine.payeesForCycle(k, 3).single().memberId)
    }

    @Test
    fun `payees empty when there are no members`() {
        val k = Kitty(name = "Empty")
        assertTrue(engine.payeesForCycle(k, 0).isEmpty())
    }

    @Test
    fun `totalShares sums share holdings`() {
        val k = Kitty(name = "Shares")
        k.members.add(Member(id = "a", name = "A"))
        k.members.add(Member(id = "b", name = "B", shares = 2.0))
        k.members.add(Member(id = "c", name = "C", shares = 0.5))
        assertEquals(3.5, k.totalShares(), 0.001)
    }

    @Test
    fun `double share member holds two payout slots`() {
        val k = Kitty(name = "Double")
        k.members.add(Member(id = "a", name = "A"))
        k.members.add(Member(id = "b", name = "B", shares = 2.0))
        k.members.add(Member(id = "c", name = "C"))
        val order = k.buildSchedule().map { it.single().memberId }
        assertEquals(listOf("a", "b", "b", "c"), order)
        assertEquals(4, engine.rotationLength(k))
    }

    @Test
    fun `two half shares split one slot`() {
        val k = Kitty(name = "Split")
        k.members.add(Member(id = "a", name = "A", shares = 0.5))
        k.members.add(Member(id = "b", name = "B", shares = 0.5))
        k.members.add(Member(id = "c", name = "C"))
        val schedule = k.buildSchedule()
        assertEquals(2, schedule.size)
        val split = schedule[1]
        assertEquals(2, split.size)
        assertEquals(0.5, split[0].fraction, 0.001)
        assertEquals("a", split[0].memberId)
        assertEquals(0.5, split[1].fraction, 0.001)
        assertEquals("b", split[1].memberId)
    }

    @Test
    fun `unpaired half share gets its own half-pot slot`() {
        val k = Kitty(name = "Odd")
        k.members.add(Member(id = "a", name = "A", shares = 0.5))
        k.members.add(Member(id = "b", name = "B"))
        val schedule = k.buildSchedule()
        assertEquals(2, schedule.size)
        assertEquals(1.0, schedule[0].single().fraction, 0.001)
        assertEquals("b", schedule[0].single().memberId)
        assertEquals(0.5, schedule[1].single().fraction, 0.001)
        assertEquals("a", schedule[1].single().memberId)
    }

    @Test
    fun `expected contribution scales with shares`() {
        val k = Kitty(name = "Exp", shareAmount = 100.0)
        k.members.add(Member(id = "a", name = "A"))
        k.members.add(Member(id = "b", name = "B", shares = 2.0))
        k.members.add(Member(id = "c", name = "C", shares = 0.5))
        assertEquals(100.0, engine.expectedContribution(k, k.members[0]), 0.001)
        assertEquals(200.0, engine.expectedContribution(k, k.members[1]), 0.001)
        assertEquals(50.0, engine.expectedContribution(k, k.members[2]), 0.001)
        assertEquals(350.0, engine.potExpected(k), 0.001)
    }

    @Test
    fun `cycle is complete only when every member has paid`() {
        val k = kittyWith3Members()
        val cycle = Cycle(index = 0, payoutMemberId = "a")
        k.cycles.add(cycle)

        assertFalse(engine.isCycleComplete(k, cycle))
        engine.recordPayment(k, cycle, "a", 100.0)
        engine.recordPayment(k, cycle, "b", 100.0)
        assertFalse(engine.isCycleComplete(k, cycle))
        engine.recordPayment(k, cycle, "c", 100.0)
        assertTrue(engine.isCycleComplete(k, cycle))
    }

    @Test
    fun `close cycle refuses until it is complete`() {
        val k = kittyWith3Members()
        val cycle = Cycle(index = 0, payoutMemberId = "a")
        k.cycles.add(cycle)

        assertFalse(engine.closeCycle(k, cycle))
        assertFalse(cycle.isClosed)

        engine.recordPayment(k, cycle, "a", 100.0)
        engine.recordPayment(k, cycle, "b", 100.0)
        engine.recordPayment(k, cycle, "c", 100.0)
        assertTrue(engine.closeCycle(k, cycle))
        assertTrue(cycle.isClosed)
    }

    @Test
    fun `total paid sums across all cycles`() {
        val k = kittyWith3Members()
        val c0 = Cycle(index = 0, payoutMemberId = "a")
        val c1 = Cycle(index = 1, payoutMemberId = "b")
        k.cycles.add(c0)
        k.cycles.add(c1)

        engine.recordPayment(k, c0, "a", 100.0)
        engine.recordPayment(k, c0, "b", 100.0)
        engine.recordPayment(k, c1, "a", 100.0)

        assertEquals(200.0, engine.totalPaid(k, "a"), 0.001)
        assertEquals(100.0, engine.totalPaid(k, "b"), 0.001)
        assertEquals(0.0, engine.totalPaid(k, "c"), 0.001)
    }

    @Test
    fun `undo payment removes a contribution`() {
        val k = kittyWith3Members()
        val c0 = Cycle(index = 0, payoutMemberId = "a")
        engine.recordPayment(k, c0, "a", 100.0)
        assertTrue(c0.contributions.containsKey("a"))
        engine.undoPayment(k, c0, "a")
        assertFalse(c0.contributions.containsKey("a"))
    }

    @Test
    fun `running balance is sum of all paid contributions`() {
        val k = kittyWith3Members()
        val c0 = Cycle(index = 0, payoutMemberId = "a")
        val c1 = Cycle(index = 1, payoutMemberId = "b")
        k.cycles.add(c0)
        k.cycles.add(c1)
        engine.recordPayment(k, c0, "a", 100.0)
        engine.recordPayment(k, c0, "b", 100.0)
        engine.recordPayment(k, c1, "a", 100.0)
        assertEquals(300.0, engine.runningBalance(k), 0.001)
    }

    @Test
    fun `rotation is balanced only after every slot is paid out`() {
        val k = kittyWith3Members()
        val c0 = Cycle(index = 0, payoutMemberId = "a")
        val c1 = Cycle(index = 1, payoutMemberId = "b")
        val c2 = Cycle(index = 2, payoutMemberId = "c")
        k.cycles.add(c0)
        k.cycles.add(c1)
        k.cycles.add(c2)
        assertFalse(engine.isRotationBalanced(k))
        engine.recordPayment(k, c0, "a", 100.0)
        engine.recordPayment(k, c0, "b", 100.0)
        engine.recordPayment(k, c0, "c", 100.0)
        assertTrue(engine.closeCycle(k, c0))
        assertFalse(engine.isRotationBalanced(k))
        engine.recordPayment(k, c1, "a", 100.0)
        engine.recordPayment(k, c1, "b", 100.0)
        engine.recordPayment(k, c1, "c", 100.0)
        assertTrue(engine.closeCycle(k, c1))
        assertFalse(engine.isRotationBalanced(k))
        engine.recordPayment(k, c2, "a", 100.0)
        engine.recordPayment(k, c2, "b", 100.0)
        engine.recordPayment(k, c2, "c", 100.0)
        assertTrue(engine.closeCycle(k, c2))
        assertTrue(engine.isRotationBalanced(k))
    }

    @Test
    fun `pot collected equals sum of contributions in a cycle`() {
        val k = kittyWith3Members()
        val c0 = Cycle(index = 0, payoutMemberId = "a")
        engine.recordPayment(k, c0, "a", 50.0)
        engine.recordPayment(k, c0, "b", 100.0)
        assertEquals(150.0, k.potCollected(c0), 0.001)
    }

    @Test
    fun `closed cycle cannot be modified`() {
        val k = kittyWith3Members()
        val c0 = Cycle(index = 0, payoutMemberId = "a")
        engine.recordPayment(k, c0, "a", 100.0)
        engine.recordPayment(k, c0, "b", 100.0)
        engine.recordPayment(k, c0, "c", 100.0)
        assertTrue(engine.closeCycle(k, c0))

        engine.recordPayment(k, c0, "a", 200.0)
        engine.undoPayment(k, c0, "b")

        assertEquals(3, c0.contributions.size)
        assertEquals(100.0, c0.contributions["a"]!!.amount, 0.001)
        assertTrue(c0.contributions.containsKey("b"))
        assertTrue(c0.contributions.containsKey("c"))
    }
}