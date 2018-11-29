
package com4j.tlbimp.driver;

import java.io.File;
import java.io.IOException;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

import com4j.ComException;
import com4j.GUID;
import com4j.tlbimp.BindingException;
import com4j.tlbimp.ErrorListener;
import com4j.tlbimp.FileCodeWriter;
import com4j.tlbimp.def.IWTypeLib;

/**
 * tlbimp implemented as an Ant task.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class AntTaskImpl extends Task implements ErrorListener {

    private File destDir;
    private File source;

    private GUID libid;
    private String libver;

    private final Driver driver = new Driver();

    private boolean hasLib = false;

    public void setDestDir(final File destDir) {
        this.destDir = destDir;
    }

    public void setPackage(final String packageName) {
        driver.setPackageName(packageName);
    }

    public void setFile(final File source) {
        this.source = source;
    }

    public void setLibid(final String libid) {
        this.libid = new GUID(libid);
    }

    public void setLibver(final String libver) {
        this.libver = libver;
    }

    public void setLocale(final String locale) {
        driver.setLocale(locale);
    }

    public void setRenameGetterAndSetters(final boolean renameGetterAndSetters) {
        driver.renameGetterAndSetters = renameGetterAndSetters;
    }

    public void setAlwaysUseComEnums(final boolean alwaysUseComEnums) {
        driver.alwaysUseComEnums = alwaysUseComEnums;
    }

    public void setGenerateDefaultMethodOverloads(final boolean v) {
        driver.generateDefaultMethodOverloads = v;
    }

    public void addConfiguredLib(final Lib r) {
        r.validate();
        driver.addLib(r);
        hasLib = true;
    }

    @Override
    public void execute() throws BuildException {
        if (destDir == null) {
            throw new BuildException("@destDir is missing");
        }

        if (source != null || libid != null) {
            final Lib lib = new Lib();
            lib.setLibid(libid);
            lib.setLibver(libver);
            lib.setFile(source);
            addConfiguredLib(lib);
        }

        if (!hasLib) {
            throw new BuildException("No type library is specified");
        }

        try {
            driver.run(new FileCodeWriter(destDir), this);
        } catch (final ComException e) {
            throw new BuildException(e);
        } catch (final IOException e) {
            throw new BuildException(e);
        } catch (final BindingException e) {
            throw new BuildException(e);
        }
    }

    @Override
    public void started(final IWTypeLib lib) {
        log("Generating definitions from " + lib.getName(), Project.MSG_INFO);
    }

    @Override
    public void error(final BindingException e) {
        log(e.getMessage(), Project.MSG_ERR);
    }

    @Override
    public void warning(final String message) {
        log(message, Project.MSG_WARN);
    }
}
