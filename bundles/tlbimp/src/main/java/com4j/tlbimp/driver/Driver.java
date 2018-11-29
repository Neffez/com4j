
package com4j.tlbimp.driver;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com4j.COM4J;
import com4j.GUID;
import com4j.tlbimp.BindingException;
import com4j.tlbimp.CodeWriter;
import com4j.tlbimp.ErrorListener;
import com4j.tlbimp.Generator;
import com4j.tlbimp.ReferenceResolver;
import com4j.tlbimp.def.IWTypeLib;

/**
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 * @author Michael Schnell (scm, (C) 2008, Michael-Schnell@gmx.de)
 */
final class Driver {

    private final Map<GUID, Lib> libs = new HashMap<>();

    private String packageName = "";

    private Locale locale = Locale.getDefault();

    boolean renameGetterAndSetters = false;
    boolean alwaysUseComEnums = false;
    boolean generateDefaultMethodOverloads = false;

    public void addLib(final Lib r) {
        libs.put(r.getLibid(), r);
    }

    public void setPackageName(final String packageName) {
        this.packageName = packageName;
    }

    public void setLocale(final String locale) {
        final String[] tokens = locale.split("_");
        this.locale = new Locale(tokens.length > 0 ? tokens[0] : "", tokens.length > 1 ? tokens[1] : "",
                tokens.length > 2 ? tokens[2] : "");
    }

    public void run(final CodeWriter cw, final ErrorListener el) throws BindingException, IOException {

        final Set<IWTypeLib> libsToGen = new HashSet<>();
        for (final Lib lib : libs.values()) {
            libsToGen.add(COM4J.loadTypeLibrary(lib.getFile()).queryInterface(IWTypeLib.class));
        }

        final ReferenceResolver resolver = new ReferenceResolver() {
            @Override
            public String resolve(final IWTypeLib lib) {
                final GUID libid = lib.getLibid();
                if (libs.containsKey(libid)) {
                    final String pkg = libs.get(libid).getPackage();
                    if (pkg != null) {
                        return pkg;
                    }
                }

                // TODO: move this to a filter
                if (libid.equals(GUID.GUID_STDOLE)) {
                    return ""; // don't generate STDOLE. That's replaced by com4j runtime.
                }

                if (libsToGen.add(lib)) {
                    el.warning(Messages.REFERENCED_TYPELIB_GENERATED.format(lib.getName(), packageName));
                }

                return packageName;
            }

            @Override
            public boolean suppress(final IWTypeLib lib) {
                final GUID libid = lib.getLibid();

                if (libid.equals(GUID.GUID_STDOLE)) {
                    return true;
                }

                final Lib r = libs.get(libid);
                if (r != null) {
                    return r.suppress();
                } else {
                    return false;
                }
            }
        };

        final Generator generator = new Generator(cw, resolver, el, locale);
        generator.setAlwaysUseComEnums(alwaysUseComEnums);
        generator.setRenameGetterAndSetters(renameGetterAndSetters);
        generator.setGenerateDefaultMethodOverloads(generateDefaultMethodOverloads);

        // repeatedly generate all the libraries that need to be generated
        final Set<IWTypeLib> generatedLibs = new HashSet<>();
        while (!generatedLibs.containsAll(libsToGen)) {
            final Set<IWTypeLib> s = new HashSet<>(libsToGen);
            s.removeAll(generatedLibs);
            for (final IWTypeLib lib : s) {
                el.started(lib);
                generator.generate(lib);
                generatedLibs.add(lib);
            }
        }

        // wrap up
        generator.finish();
    }

}
