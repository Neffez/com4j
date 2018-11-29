
package com4j;

/**
 * CLSCTX constants.
 *
 * @author Kohsuke Kawaguchi
 */
public interface CLSCTX {
    /**
     * COM object will be in the same process.
     */
    int INPROC_SERVER = 1;
    /**
     *
     */
    int INPROC_HANDLER = 2;
    /**
     * COM object will be in another process on the same machine
     */
    int LOCAL_SERVER = 4;
    /**
     * COM object will be in a remote machine.
     */
    int REMOTE_SERVER = 16;
    /**
     * Use this when you don't care where the server is.
     */
    int ALL = INPROC_HANDLER | INPROC_SERVER | LOCAL_SERVER | REMOTE_SERVER;
}
