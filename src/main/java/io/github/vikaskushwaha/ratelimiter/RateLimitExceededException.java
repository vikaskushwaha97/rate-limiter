package io.github.vikaskushwaha.ratelimiter;

/**
 * Unchecked exception thrown when a call to {@link RateLimiter#acquire()}
 * cannot be satisfied because the rate limit has been exceeded.
 *
 * <p>This is an unchecked exception ({@code RuntimeException}) so callers are
 * not forced to declare it in their {@code throws} clause, following the same
 * convention used by {@code IllegalStateException} and {@code IllegalArgumentException}.
 * Callers that wish to handle it explicitly can still catch it.
 *
 * <h2>Design rationale</h2>
 * <ul>
 *   <li>Unchecked — rate limit violations are programming-environment concerns,
 *       not recoverable business exceptions that every caller must handle.</li>
 *   <li>Extends {@link RuntimeException} directly — no checked-exception
 *       pollution on hot call paths.</li>
 * </ul>
 *
 * @author Vikas Kushwaha
 * @version 1.0.0
 * @since 1.0.0
 */
public class RateLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message human-readable explanation, surfaced to callers and logs
     */
    public RateLimitExceededException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message human-readable explanation
     * @param cause   the underlying cause (e.g., an interrupted sleep)
     */
    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
