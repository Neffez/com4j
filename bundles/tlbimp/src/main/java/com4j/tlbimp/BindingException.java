
package com4j.tlbimp;

import java.util.ArrayList;
import java.util.List;

/**
 * Signals a failure in the binding process.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */

@SuppressWarnings("serial")
public class BindingException extends Exception {

    public BindingException(final String message) {
        super(message);
    }

    public BindingException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public BindingException(final Throwable cause) {
        super(cause);
    }

    private final List<String> contexts = new ArrayList<>();

    void addContext(final String ctxt) {
        contexts.add(ctxt);
    }

    @Override
    public String getMessage() {
        final StringBuilder buf = new StringBuilder();
        buf.append(super.getMessage());
        for (final String s : contexts) {
            buf.append("\n  ").append(s);
        }
        return buf.toString();
    }
}
