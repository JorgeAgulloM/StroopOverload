package com.softyorch.stroopoverload.core

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] for coroutine code: identical, except a [CancellationException] is
 * rethrown instead of being wrapped into [Result.failure]. Plain `runCatching`
 * around a suspending call swallows cancellation, so a coroutine whose scope was
 * cancelled (e.g. a cleared ViewModel) keeps running as if nothing happened.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
