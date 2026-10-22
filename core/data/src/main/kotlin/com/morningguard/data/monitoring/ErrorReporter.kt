package com.morningguard.data.monitoring

/**
 * Reports unexpected issues for backend monitoring (e.g. Sentry). Must not drive user-facing copy.
 */
interface ErrorReporter {
    fun reportNonFatal(
        throwable: Throwable,
        tags: Map<String, String> = emptyMap(),
        extras: Map<String, String> = emptyMap(),
    )

    fun breadcrumb(category: String, message: String) = Unit
}

class NoOpErrorReporter : ErrorReporter {
    override fun reportNonFatal(
        throwable: Throwable,
        tags: Map<String, String>,
        extras: Map<String, String>,
    ) = Unit
}
