package com.jorisjonkers.privatestack.common.exception

open class DomainException(
    message: String,
    val code: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
