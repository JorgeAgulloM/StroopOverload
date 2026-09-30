package com.softyorch.stroopoverload.core

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunCatchingCancellableTest {

    @Test
    fun `returns success with the block's value`() {
        assertEquals(Result.success(42), runCatchingCancellable { 42 })
    }

    @Test
    fun `wraps ordinary exceptions as failure`() {
        val result = runCatchingCancellable<Int> { throw IllegalStateException("boom") }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test(expected = CancellationException::class)
    fun `rethrows CancellationException instead of swallowing it`() {
        runCatchingCancellable<Int> { throw CancellationException("cancelled") }
    }
}
