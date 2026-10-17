package app.fuelwise.core.domain

import javax.inject.Qualifier

/** Inject dispatchers with this qualifier; never reference `Dispatchers.IO` directly (CLAUDE.md). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val value: FwDispatcher)

enum class FwDispatcher {
    Default,
    IO,
}
