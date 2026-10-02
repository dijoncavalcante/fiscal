package com.bragadev.fiscal.domain.model

/** Resultado de uma operação que pode falhar com um erro de domínio. */
sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>
    data class Failure(val error: FileOperationError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = flatMap { Outcome.Success(transform(it)) }

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value
