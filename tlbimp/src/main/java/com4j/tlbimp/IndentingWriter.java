
package com4j.tlbimp;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Stack;

/**
 * {@link PrintWriter} with a little additional capability.
 *
 * <p>
 * Specifically,
 * <ol>
 * <li>Indentation.
 * <li>Printing comma-separated tokens.
 * <li>Buffering the certain portion of the output and canceling it later.
 * </ol>
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 * @author Michael Schnell (scm, (C) 2008, Michael-Schnell@gmx.de)
 */
public class IndentingWriter extends PrintWriter {
    private final int indentSize = 2;
    private String indentString = null;

    private int indent = 0;
    private boolean newLine = true;

    public IndentingWriter(final Writer out) {
        super(new CancellableWriter(out));
        initializeIndentString();
    }

    public IndentingWriter(final Writer out, final boolean autoFlush) {
        super(new CancellableWriter(out), autoFlush);
        initializeIndentString();
    }

    private CancellableWriter getOut() {
        return (CancellableWriter) out;
    }

    private void initializeIndentString() {
        final StringBuffer sb = new StringBuffer(indentSize);
        for (int i = 0; i < indentSize; sb.append(' '), i++) {
            ;
        }
        indentString = sb.toString();
    }

    //
    //
    // buffering, cancelling, and committing
    //
    //
    public void startBuffering() {
        try {
            getOut().mark();
        } catch (final IOException e) {
        }
    }

    public void cancel() {
        getOut().cancel();
    }

    public void commit() {
        try {
            getOut().commit();
        } catch (final IOException e) {
        }
    }

    //
    //
    // indentation
    //
    //
    /**
     * Increases the indentation level.
     */
    public void in() {
        indent++;
    }

    /**
     * Decreases the indentation level.
     */
    public void out() {
        indent--;
    }

    private void printIndent() {
        try {
            for (int i = 0; i < indent; i++) {
                out.write(indentString);
            }
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    private void checkIndent() {
        if (newLine) {
            printIndent();
            if (javaDocMode) {
                try {
                    out.write(" * ");
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
        }
        newLine = false;
    }

    //
    //
    // comma-separated tokens
    //
    //
    /**
     * If true, we need to print ',' in the next {@link #comma()}.
     */
    private boolean needsComma;

    private final Stack<Boolean> commaStack = new Stack<>();

    /**
     * Starts the comma-separated token mode.
     */
    public void beginCommaMode() {
        commaStack.push(needsComma);
        needsComma = false;
    }

    /**
     * Ends the comma-separated token mode.
     */
    public void endCommaMode() {
        needsComma |= commaStack.pop();
    }

    /**
     * Prints out ',' if something was printed since
     * the last invocation of {@link #comma()} or {@link #beginCommaMode()}.
     */
    public void comma() {
        if (needsComma) {
            print(',');
            needsComma = false;
        }
    }

    private boolean javaDocMode = false;

    public void beginJavaDocMode() {
        assert javaDocMode == false;
        if (javaDocMode) {
            System.err.println("Waring: Already in JavaDocMode!");
        }
        println("/**");
        javaDocMode = true;
    }

    public void endJavaDocMode() {
        assert javaDocMode == true;
        if (!javaDocMode) {
            System.err.println("Waring: Wasn't in JavaDocMode!");
        }
        javaDocMode = false;
        println(" */");
    }

    //
    //
    // overriding the base class methods
    //
    //
    @Override
    public void println() {
        super.println();
        newLine = true;
    }

    @Override
    public void write(final int c) {
        checkIndent();
        needsComma = true;
        super.write(c);
    }

    @Override
    public void write(final char buf[], final int off, final int len) {
        checkIndent();
        needsComma = true;
        super.write(buf, off, len);
    }

    @Override
    public void write(final String s, final int off, final int len) {
        checkIndent();
        needsComma = true;
        super.write(s, off, len);
    }

    public void printJavadoc(final String doc) {
        if (doc != null) {
            beginJavaDocMode();
            println(doc);
            endJavaDocMode();
        }
    }
}

class CancellableWriter extends FilterWriter {
    /**
     * Text that might be cancelled later will be buffered here.
     */
    private final StringBuffer buffer = new StringBuffer();

    private boolean marked;

    /**
     * Once called, successive writing will be buffered until
     * cancel() or commit() is called later.
     */
    public void mark() throws IOException {
        if (marked) {
            commit();
        }
        marked = true;
    }

    /**
     * Cancel the data written since the last {@link #mark()} method.
     */
    public void cancel() {
        if (!marked) {
            throw new IllegalStateException();
        }
        marked = false;
        buffer.setLength(0);
    }

    /**
     * Write the pending data.
     */
    public void commit() throws IOException {
        if (!marked) {
            throw new IllegalStateException();
        }
        marked = false;
        super.append(buffer);
        buffer.setLength(0);
    }

    public CancellableWriter(final Writer out) {
        super(out);
    }

    @Override
    public void write(final int c) throws IOException {
        if (marked) {
            buffer.append((char) c);
        } else {
            super.write(c);
        }
    }

    @Override
    public void write(final char[] cbuf, final int off, final int len) throws IOException {
        if (marked) {
            buffer.append(cbuf, off, len);
        } else {
            super.write(cbuf, off, len);
        }
    }

    @Override
    public void write(final String str, final int off, final int len) throws IOException {
        if (marked) {
            buffer.append(str, off, len);
        } else {
            super.write(str, off, len);
        }
    }

    @Override
    public void flush() throws IOException {
        super.flush();
    }

    @Override
    public void close() throws IOException {
        if (marked) {
            commit();
        }
        super.close();
    }
}