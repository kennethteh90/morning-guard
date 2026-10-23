package com.morningguard.app.monitoring

import com.morningguard.data.monitoring.ErrorReporter
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SentryErrorReporter @Inject constructor() : ErrorReporter {

    override fun reportNonFatal(
        throwable: Throwable,
        tags: Map<String, String>,
        extras: Map<String, String>,
    ) {
        Sentry.withScope { scope ->
            scope.level = SentryLevel.WARNING
            tags.forEach { (key, value) ->
                scope.setTag(key, value.take(TAG_VALUE_MAX))
            }
            extras.forEach { (key, value) ->
                scope.setExtra(key, value.take(EXTRA_MAX))
            }
            Sentry.captureException(throwable)
        }
    }

    override fun breadcrumb(category: String, message: String) {
        Sentry.addBreadcrumb(
            Breadcrumb.info(message.take(EXTRA_MAX)).apply {
                this.category = category.take(CATEGORY_MAX)
            },
        )
    }

    companion object {
        private const val TAG_VALUE_MAX = 200
        private const val EXTRA_MAX = 2_000
        private const val CATEGORY_MAX = 64
    }
}
