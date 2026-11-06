package com.seasaw.theta.core.common

import kotlinx.coroutines.CancellationException

/** Why an expected operation failed. Expected failures are values, not thrown exceptions. */
sealed interface AppError {
    data object Network : AppError
    data object Storage : AppError
    data object NotSupported : AppError
    data object InvalidInput : AppError

    /** A failure we did not anticipate. [cause] is for logging only and must not reach the UI as text. */
    data class Unexpected(val cause: Throwable) : AppError
}

sealed interface AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> = apply {
    if (this is AppResult.Success) action(value)
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> = apply {
    if (this is AppResult.Failure) action(error)
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value

/**
 * Runs [block] and captures failures. Coroutine cancellation is rethrown because swallowing it would
 * stop structured concurrency from unwinding the caller.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> resultOf(mapError: (Throwable) -> AppError = { AppError.Unexpected(it) }, block: () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        AppResult.Failure(mapError(failure))
    }
