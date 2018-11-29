
package com4j.tlbimp;

import java.io.File;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 * {@link CodeWriter} that dumps to stdout. For debugging.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class DumpCodeWriter implements CodeWriter {
    @Override
    public IndentingWriter create(final File file) {
        final IndentingWriter w = new IndentingWriter(new FilterWriter(new OutputStreamWriter(System.out)) {
            @Override
            public void close() throws IOException {
                flush();
                // don't close ignore
            }
        }, true);
        w.printf("------ %1s ---------\n", file.getPath());
        return w;
    }
}
