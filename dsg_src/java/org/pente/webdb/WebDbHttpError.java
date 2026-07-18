package org.pente.webdb;

/**
 * A validation failure that maps to a specific HTTP error envelope
 * ({@code {"error":{"code":..,"message":..}}}). Thrown by the auth'd handlers'
 * testable core methods (e.g. an import batch over the size cap, or an analysis
 * tree that is malformed or too large) so the HTTP {@code handle*} wrappers can
 * translate it with {@link JsonHttp#error} while unit tests can assert the
 * {@link #status}/{@link #code} directly without a servlet container.
 */
public class WebDbHttpError extends Exception {

    private static final long serialVersionUID = 1L;

    /** HTTP status to send (e.g. 400, 413). */
    public final int status;

    /** Machine-readable error code for the envelope (e.g. {@code "bad_tree"}). */
    public final String code;

    public WebDbHttpError(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
