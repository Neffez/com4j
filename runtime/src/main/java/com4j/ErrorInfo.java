
package com4j;

import java.io.File;

/**
 * Represents error information.
 *
 * <p>
 * This is a Java bean version of <tt>IErrorInfo</tt> COM interface.
 *
 * @author Kohsuke Kawaguchi
 */

public class ErrorInfo {

    private GUID guid;

    private String source;

    private String description;

    private File helpFile;

    private Integer helpContext;

    /* package */ ErrorInfo(final IErrorInfo ei) {
        try {
            this.guid = ei.guid();
        } catch (final ComException e) {
            // ignore
        }
        try {
            this.source = ei.source();
        } catch (final ComException e) {
            // ignore
        }
        try {
            this.description = ei.description();
        } catch (final ComException e) {
            // ignore
        }
        try {
            final String pathname = ei.helpFile();
            if (pathname != null) {
                this.helpFile = new File(pathname);
            } else {
                this.helpFile = null;
            }
        } catch (final ComException e) {
            // ignore
        }
        try {
            this.helpContext = ei.helpContext();
        } catch (final ComException e) {
            // ignore
        }
    }

    /**
     * Returns GUID for the interface that defined the error.
     *
     * @return
     * null if no such information is available.
     */
    public GUID getGuid() {
        return guid;
    }

    /**
     * Returns the ProgID for the class or application that returned the error.
     *
     * @return
     * null if no such information is available.
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns a textual description of the error.
     *
     * @return
     * null if no such information is available.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the path of the Help file that describes the error.
     *
     * @return
     * null if no such information is available.
     */
    public File getHelpFile() {
        return helpFile;
    }

    /**
     * Returns the Help context identifier (ID) for the error.
     *
     * @return
     * null if no such information is available.
     */
    public Integer getHelpContext() {
        return helpContext;
    }

    @Override
    public String toString() {
        return description != null ? description : "(no description)";
    }
}
