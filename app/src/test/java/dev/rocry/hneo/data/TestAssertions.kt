package dev.rocry.hneo.data

/** Like kotlin.test's, but usable from suspend blocks without adding a dependency. */
internal inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        throw AssertionError("Expected ${T::class.simpleName} but got ${e::class.simpleName}: ${e.message}", e)
    }
    throw AssertionError("Expected ${T::class.simpleName} but nothing was thrown")
}
