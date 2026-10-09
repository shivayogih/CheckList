package com.dataloom.checklist.domain.common

import javax.inject.Qualifier

/**
 * Qualifiers for the `CoroutineDispatcher` a class runs its work on. Classes ask for a dispatcher
 * in their constructor instead of naming `Dispatchers.IO` or `Dispatchers.Default`, so tests can
 * pass a test dispatcher and the choice lives in one Hilt module (`CoroutinesModule` in :data).
 *
 * A function that takes one of these and switches with `withContext` is main-safe: callers can
 * invoke it from `viewModelScope` without thinking about threads.
 */

/** Blocking I/O: files, content resolvers, Keystore, SharedPreferences. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** CPU work: sorting, encoding, parsing, mapping large lists. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
