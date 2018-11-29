
package com4j.tlbimp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class FileCodeWriter implements CodeWriter {
    private final File outDir;

    public FileCodeWriter(final File outDir) {
        this.outDir = outDir;
    }

    @Override
    public IndentingWriter create(File file) throws IOException {
        file = new File(outDir, file.getPath());
        final File dir = file.getParentFile();
        final boolean newCreated = dir.mkdirs();
        final boolean exists = dir.exists();
        if (!exists) {
            throw new IOException("Could not create the directory " + file.getParentFile().getAbsolutePath());
        }
        // TODO: proper escaping
        return new IndentingWriter(new FileWriter(file));
    }
}
