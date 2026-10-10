package com.dataloom.checklist.ai.model

/**
 * Outcome of every AI call (architecture section 11.3). Expected situations (AI switched off, no
 * network, a command that was not understood) are values, so the UI can show one friendly message and
 * a "do it manually" button instead of crashing or spinning.
 */
sealed interface AiResult<out T> {
    data class Success<out T>(val value: T) : AiResult<T>

    /** The service cannot answer right now; the app works the same without it. */
    data class Unavailable(val reason: UnavailableReason) : AiResult<Nothing>

    /** The service answered but refused or could not understand the request. */
    data class Rejected(val reason: RejectionReason) : AiResult<Nothing>

    /** Unexpected failure inside the service (bug, malformed provider response). */
    data class Error(val cause: Throwable) : AiResult<Nothing>
}

enum class UnavailableReason {
    /** AI is off in Settings (the default). */
    DISABLED,

    /** Online AI needs a connection. */
    OFFLINE,

    /** The provider's free quota is used up. */
    QUOTA,

    /** The provider did not answer in time. */
    TIMEOUT,

    /** This implementation does not offer the feature (the offline parser cannot summarize). */
    NOT_SUPPORTED,
}

enum class RejectionReason {
    EMPTY_INPUT,

    /** Longer than [Utterance.MAX_LENGTH]; long text is not a quick command. */
    TOO_LONG,

    /** Nothing in the text could be turned into an action. */
    NOT_UNDERSTOOD,

    /** The command needs an open checklist ("add rice") but none was given. */
    NEEDS_CHECKLIST,
}

/** Maps the value of a [AiResult.Success], leaving every other outcome unchanged. */
inline fun <T, R> AiResult<T>.map(transform: (T) -> R): AiResult<R> = when (this) {
    is AiResult.Success -> AiResult.Success(transform(value))
    is AiResult.Unavailable -> this
    is AiResult.Rejected -> this
    is AiResult.Error -> this
}
