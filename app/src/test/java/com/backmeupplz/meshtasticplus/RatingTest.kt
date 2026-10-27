package com.backmeupplz.meshtasticplus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingTest {
    private val day = 86_400_000L
    private val now = 1_000 * day
    private fun due(first: Long = now - 3 * day, days: Int = 3, sent: Int = 20, asks: Int = 0, lastAsk: Long = 0, lastVersion: Long = 0, version: Long = 9) =
        ratingDue(now, first, days, sent, asks, lastAsk, lastVersion, version)

    @Test fun asksOnlyWhenEarnedAndAtMostTwice() {
        assertTrue(due())
        assertFalse(due(sent = 19))
        assertFalse(due(days = 2))
        assertFalse(due(first = now - 3 * day + 1)) // 3 days of use, but first use under 3 days ago
        assertTrue(due(asks = 1, lastAsk = now - 120 * day, lastVersion = 8))
        assertFalse(due(asks = 1, lastAsk = now - 119 * day, lastVersion = 8)) // too soon
        assertFalse(due(asks = 1, lastAsk = now - 200 * day, lastVersion = 9)) // same version
        assertFalse(due(asks = 2, lastAsk = now - 400 * day, lastVersion = 1)) // never a third time
    }
}
