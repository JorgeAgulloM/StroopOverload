package com.softyorch.stroopoverload.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerClockTest {

    @Test
    fun `reads the device clock until the server offset is known`() {
        val clock = ServerClock(deviceNowMs = { 10_000L })

        assertEquals(10_000L, clock.nowMs())
    }

    @Test
    fun `shifts the device clock by the server offset`() {
        // A phone running 2.5 s fast reports a negative offset.
        val clock = ServerClock(deviceNowMs = { 10_000L })

        clock.updateOffset(-2_500L)

        assertEquals(7_500L, clock.nowMs())
    }
}
