
package com4j;

/**
 * Signals a general {@link RuntimeException} thrown during com4j processing.
 *
 * @author Kohsuke Kawaguchi
 */

@SuppressWarnings("serial")
public class ExecutionException extends RuntimeException {
    public ExecutionException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public ExecutionException(final Throwable cause) {
        super(cause);
    }
}
