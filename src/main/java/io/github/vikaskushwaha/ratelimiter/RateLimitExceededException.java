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
 * <h2>Structured data</h2>
 * <p>Beyond the human-readable message, this exception carries
 * {@link #getRequestedPermits()} and {@link #getAvailablePermits()} so callers
 * can inspect the rejection reason programmatically (e.g., to build a
 * {@code Retry-After} header) without parsing the message string.
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

    /** Number of permits the caller requested. */
    private final int requestedPermits;

    /** Number of permits that were available at rejection time (approximate). */
    private final long availablePermits;

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message human-readable explanation, surfaced to callers and logs
     */
    public RateLimitExceededException(String message) {
        this(message, 1, 0);
    }

    /**
     * Constructs a new exception with structured permit information.
     *
     * @param message           human-readable explanation
     * @param requestedPermits  number of permits the caller tried to acquire
     * @param availablePermits  approximate number of permits available at rejection time
     */
    public RateLimitExceededException(String message, int requestedPermits, long availablePermits) {
        super(message);
        this.requestedPermits = requestedPermits;
        this.availablePermits = availablePermits;
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message human-readable explanation
     * @param cause   the underlying cause (e.g., an interrupted sleep)
     */
    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
        this.requestedPermits = 1;
        this.availablePermits = 0;
    }

    /**
     * Returns the number of permits the caller tried to acquire.
     *
     * @return requested permit count, always &ge; 1
     */
    public int getRequestedPermits() {
        return requestedPermits;
    }

    /**
     * Returns the approximate number of permits that were available when the
     * request was rejected. This is a snapshot — the actual value may have
     * changed by the time the caller reads it.
     *
     * @return available permit count at rejection time, always &ge; 0
     */
    public long getAvailablePermits() {
        return availablePermits;
    }
}
