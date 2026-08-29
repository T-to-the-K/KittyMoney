package com.talha.kitty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KittyEngineTest {

    private val engine = KittyEngine.instance

    private fun kittyWith3Members(name: String = "Test", amount: Double = 100.0): Kitty {
        val k = Kitty(name = name, contributionAmount = amount)
        k.members.add(Member(id = "a", name = "A"))
        k.members.add(Member(id = "b", name = "B"))
        k.members.add(Member(id = "c", name = "C"))
        return k
    }

    @Test
    fun `round robin payout rotates through members`() {
        val k = kittyWith3Members()
        assertEquals("a", k.payoutForCycle(0))
        assertEquals("b", k.payoutForCycle(1))
        assertEquals("c", k.payoutForCycle(2))
        assertEquals("a", k.payoutForCycle(3))
    }

    @Test
    fun `payout returns null when there are no members`() {
        val k = Kitty(name = "Empty", contributionAmount = 50.0)
        assertEquals(null, k.payoutForCycle(0))
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
    fun `outstanding returns remaining amount owed`() {
        val k = kittyWith3Members()
        val c0 = Cycle(index = 0, payoutMemberId = "a")
        engine.recordPayment(k, c0, "a", 40.0)
        assertEquals(60.0, engine.outstanding(k, c0, "a"), 0.001)
        assertEquals(100.0, engine.outstanding(k, c0, "b"), 0.001)
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
    fun `rotation is balanced when every member has had a closed payout`() {
        val k = kittyWith3Members()
        val c0 = Cycle(index = 0, payoutMemberId = "a")
        val c1 = Cycle(index = 1, payoutMemberId = "b")
        k.cycles.add(c0)
        k.cycles.add(c1)
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
        val c2 = Cycle(index = 2, payoutMemberId = "c")
        k.cycles.add(c2)
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
        assertEquals(300.0, k.expectedTotalForCycle(), 0.001)
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
