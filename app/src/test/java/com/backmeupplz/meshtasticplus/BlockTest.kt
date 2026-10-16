package com.backmeupplz.meshtasticplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockTest {
    private fun msg(from: Long, peer: Long = 0, mine: Boolean = false) = Message(1, "x", "hi", 0, mine = mine, from = from, peer = peer)

    @Test fun blockedNodeIsHiddenEverywhere() {
        val blocked = listOf(7L)
        assertFalse(visible(msg(from = 7), blocked)) // their room message
        assertFalse(visible(msg(from = 7, peer = 7), blocked)) // their DM
        assertFalse(visible(msg(from = 1, peer = 7, mine = true), blocked)) // my DM to them: the whole conversation goes
        assertTrue(visible(msg(from = 8), blocked)) // everyone else stays
        assertTrue(visible(msg(from = 1, mine = true), blocked)) // my own room messages stay
    }
}
