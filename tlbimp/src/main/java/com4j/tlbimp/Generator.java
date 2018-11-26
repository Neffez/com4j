
package com4j.tlbimp;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com4j.Com4jObject;
import com4j.GUID;
import com4j.tlbimp.def.ICoClassDecl;
import com4j.tlbimp.def.IConstant;
import com4j.tlbimp.def.IDispInterfaceDecl;
import com4j.tlbimp.def.IEnumDecl;
import com4j.tlbimp.def.IImplementedInterfaceDecl;
import com4j.tlbimp.def.IInterfaceDecl;
import com4j.tlbimp.def.ITypeDecl;
import com4j.tlbimp.def.ITypedefDecl;
import com4j.tlbimp.def.IWTypeLib;
import com4j.tlbimp.def.TypeKind;

/**
 * Type library importer.
 *
 * <p>
 * One instance of this class is created for one invocation of type library generation.
 * This object keeps track of what type libraries are compiled into what packages,
 * and keeps other "global" (per tlbimp invocation) state.
 *
 * <p>
 * The actual details of the generation is delegated to other generator objects.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 * @author Michael Schnell (scm, (C) 2008, Michael-Schnell@gmx.de)
 */
public final class Generator {
    private final CodeWriter writer;

    private final ReferenceResolver referenceResolver;

    /**
     * Errors should be reported to this object.
     * Always non-null.
     */
    protected final ErrorListener el;

    final DefaultMethodFinder dmf = new DefaultMethodFinder();

    /**
     * Locale for manipulating strings.
     * Always non-null.
     */
    final Locale locale;

    /**
     * {@link IWTypeLib}s specified to the {@link #generate(IWTypeLib)} method.
     */
    private final Set<LibBinder> generatedTypeLibs = new HashSet<>();

    /**
     * Specifies if the get and put COM methods should be renamed to a more Java
     * like fashion. If true, a COM put method named "Item" will be named "setItem" on the
     * Java side. If false, it will be named "item" (scm)
     */
    boolean renameGetterAndSetters = false;

    /**
     * If this value is true, the type generator will always generate enums that implement
     * the ComEnum interface. (scm)
     */
    boolean alwaysUseComEnums = false;

    /**
     * If true, generate the overloaded methods by skipping optional parameters.
     *
     * This feature still appears to be unstable, so turning it off by default.
     * For example, try the word demo to see how it breaks.
     */
    boolean generateDefaultMethodOverloads = true;

    public Generator(final CodeWriter writer, final ReferenceResolver resolver, final ErrorListener el,
            final Locale locale) {
        this.el = el;
        this.writer = writer;
        this.referenceResolver = resolver;
        this.locale = locale;
    }

    public void setRenameGetterAndSetters(final boolean renameGetterAndSetters) {
        this.renameGetterAndSetters = renameGetterAndSetters;
    }

    public void setAlwaysUseComEnums(final boolean alwaysUseComEnums) {
        this.alwaysUseComEnums = alwaysUseComEnums;
    }

    public void setGenerateDefaultMethodOverloads(final boolean v) {
        this.generateDefaultMethodOverloads = v;
    }

    /**
     * Call this method repeatedly to generate classes from each type library.
     */
    public void generate(final IWTypeLib lib) throws BindingException, IOException {
        final LibBinder tli = getTypeLibInfo(lib);
        if (referenceResolver.suppress(lib)) {
            return; // skip code generation
        }
        if (generatedTypeLibs.add(tli)) {
            tli.generate();
        }
    }

    /**
     * Finally call this method to wrap things up.
     *
     * <p>
     * In particular this generates the ClassFactory class.
     */
    public void finish() throws IOException {
        // Map<String,Set<TypeLibInfo>> byPackage = new HashMap<String,Set<TypeLibInfo>>();
        // for( TypeLibInfo tli : generatedTypeLibs ) {
        // Set<TypeLibInfo> s = byPackage.get(tli.packageName);
        // if(s==null)
        // byPackage.put(tli.packageName,s=new HashSet<TypeLibInfo>());
        // s.add(tli);
        // }

        // for( Map.Entry<String,Set<TypeLibInfo>> e : byPackage.entrySet() ) {
        for (final Package pkg : packages.values()) {
            final LibBinder lib1 = pkg.typeLibs.iterator().next();

            if (referenceResolver.suppress(lib1.lib)) {
                continue;
            }

            // generate ClassFactory
            final IndentingWriter o = pkg.createWriter(lib1, "ClassFactory.java");
            lib1.generateHeader(o);

            o.printJavadoc("Defines methods to create COM objects");
            o.println("public abstract class ClassFactory {");
            o.in();

            o.println("private ClassFactory() {} // instanciation is not allowed");
            o.println();

            for (final LibBinder lib : pkg.typeLibs) {
                final int len = lib.lib.count();
                for (int i = 0; i < len; i++) {
                    final ICoClassDecl t = lib.lib.getType(i).queryInterface(ICoClassDecl.class);
                    if (t == null) {
                        continue;
                    }
                    if (!t.isCreatable()) {
                        continue;
                    }

                    declareFactoryMethod(o, t);
                    t.dispose();
                }
            }

            o.out();
            o.println("}");
            o.close();
        }
    }

    /**
     * Returns the primary interface for the given co-class.
     *
     * @return
     * null if none is found.
     */
    ITypeDecl getDefaultInterface(final ICoClassDecl t) {
        final int count = t.countImplementedInterfaces();
        // look for the default interface first.
        for (int i = 0; i < count; i++) {
            final IImplementedInterfaceDecl impl = t.getImplementedInterface(i);
            if (impl.isSource()) {
                continue;
            }
            if (impl.isDefault()) {
                final IInterfaceDecl c = getCustom(impl);
                if (c != null) {
                    return c;
                }
                final IDispInterfaceDecl d = impl.getType().queryInterface(IDispInterfaceDecl.class);
                if (d != null) {
                    return d;
                }
            }
        }

        // if none is found, look for any non-source interface
        final Set<IInterfaceDecl> types = new HashSet<>();
        for (int i = 0; i < count; i++) {
            final IImplementedInterfaceDecl impl = t.getImplementedInterface(i);
            if (impl.isSource()) {
                continue;
            }
            final IInterfaceDecl c = getCustom(impl);
            if (c != null) {
                types.add(c);
            }
        }

        if (types.isEmpty()) {
            return null;
        }

        // if T1 and T2 are in the set and T1 derives from T2, eliminate T2
        // (since returning T1 is better)
        final Set<IInterfaceDecl> bases = new HashSet<>();
        for (final IInterfaceDecl ii : types) {
            for (int i = 0; i < ii.countBaseInterfaces(); i++) {
                bases.add(ii.getBaseInterface(i).queryInterface(IInterfaceDecl.class));
            }
        }
        types.removeAll(bases);

        return types.iterator().next();
    }

    private IInterfaceDecl getCustom(final IImplementedInterfaceDecl impl) {
        final ITypeDecl t = impl.getType();
        final IInterfaceDecl ii = t.queryInterface(IInterfaceDecl.class);
        if (ii != null) {
            return ii; // this is a custom interface
        }

        final IDispInterfaceDecl di = t.queryInterface(IDispInterfaceDecl.class);
        if (di.isDual()) {
            return di.getVtblInterface();
        }

        return null; // disp-only. can't handle it now.
    }

    private void declareFactoryMethod(final IndentingWriter o, final ICoClassDecl t) {
        final ITypeDecl p = getDefaultInterface(t);

        String primaryIntf = Com4jObject.class.getName(); // default interface name
        try {
            if (p != null) {
                primaryIntf = getTypeName(p);
            }
        } catch (final BindingException e) {
            e.addContext("class factory for coclass " + t.getName());
            el.error(e);
        }

        o.println();
        o.printJavadoc(t.getHelpString());

        o.printf("public static %1s create%2s() {", primaryIntf, t.getName());
        o.println();
        o.in();

        o.printf("return COM4J.createInstance( %1s.class, \"%2s\" );", primaryIntf, t.getGUID());
        o.println();

        o.out();
        o.println("}");
        //
        //
        // o.println(t.getHelpString());
        // o.println(t.getName());
        // int count = t.countImplementedInterfaces();
        // for( int j=0; j<count; j++ ) {
        // IImplementedInterfaceDecl impl = t.getImplementedInterface(j);
        // o.printf("%1s def:%2b src:%3b rst:%4b\n",
        // impl.getType().getName(),
        // impl.isDefault(),
        // impl.isSource(),
        // impl.isRestricted());
        // impl.dispose();
        // }
    }

    /**
     * All {@link Package}s keyed by their names.
     */
    private final Map<String, Package> packages = new HashMap<>();

    private Package getPackage(final String name) {
        Package p = packages.get(name);
        if (p == null) {
            packages.put(name, p = new Package(name));
        }
        return p;
    }

    /**
     * Represents a Java package.
     */
    private final class Package {
        /**
         * Java package name of this type library.
         * Can be empty but never null.
         */
        final String name;

        /**
         * Short filenames that are generated into this package.
         * <p>
         * Used to detect collisions. The value is the type that
         * generated it.
         */
        private final Map<String, LibBinder> fileNames = new HashMap<>();

        /**
         * Type libraries generated into this package.
         */
        final Set<LibBinder> typeLibs = new HashSet<>();

        public Package(final String name) {
            this.name = name;
        }

        private File getDir() {
            if (isRoot()) {
                return new File(".");
            } else {
                return new File(name.replace('.', File.separatorChar));
            }
        }

        /**
         * True if this is the root package.
         */
        public boolean isRoot() {
            return name.equals("");
        }

        /**
         * Creates an {@link IndentingWriter} for a given file in this package.
         *
         * @param fileName
         *     such as "Foo.java"
         */
        public IndentingWriter createWriter(final LibBinder lib, final String fileName) throws IOException {
            final LibBinder tli = fileNames.get(fileName);
            if (tli != null) {
                el.error(new BindingException(
                        Messages.FILE_CONFLICT.format(fileName, tli.lib.getName(), lib.lib.getName(), name)));
            } else {
                fileNames.put(fileName, lib);
            }
            return writer.create(new File(getDir(), fileName));
        }

    }

    /**
     * Type library information.
     */
    private final Map<IWTypeLib, LibBinder> typeLibs = new HashMap<>();

    /**
     * Generates code from a type library.
     *
     * <p>
     * Processing a type library often requires references to other
     * type libraries, and in particular how the type names in other
     * type libraries are bound in Java.
     *
     * <p>
     * An instance of this class is created for each type library
     * (including the one that we are binding.)
     */
    /* package */ final class LibBinder {
        final IWTypeLib lib;

        /**
         * Java package of this type library.
         */
        final Package pkg;

        /**
         * Every top-level type declaration in this type library and their
         * Java type name.
         */
        private final Map<ITypeDecl, String> aliases = new HashMap<>();

        /**
         * Generated event interfaces,
         * so that we don't generate them as invocable interfaces,
         * and we don't generate them twice.
         */
        private final Set<ITypeDecl> eventInterfaces = new HashSet<>();

        public LibBinder(final IWTypeLib lib) throws BindingException {
            this.lib = lib;
            this.pkg = getPackage(referenceResolver.resolve(lib));
            this.pkg.typeLibs.add(this);

            buildSimpleAliasTable();
        }

        /**
         * MIDL often produces typedefs of the form "typedef A B" where
         * B is an enum declaration, and A is the name given by the user in IDL
         * (A is usually cryptic, B is human readable.)
         *
         * <p>
         * I don't know why MIDL behaves in this way, but in this case
         * it's usually desirable as a binding if we use A everywhere in place of B.
         *
         * <p>
         * We build this map B -> A to simply this.
         */
        private void buildSimpleAliasTable() {
            String prefix = pkg.name;
            if (prefix.length() == 0) {
                prefix += '.';
            }

            final int len = lib.count();
            for (int i = 0; i < len; i++) {
                final ITypeDecl t = lib.getType(i);
                if (t.getKind() == TypeKind.ALIAS) {
                    final ITypedefDecl typedef = t.queryInterface(ITypedefDecl.class);
                    final ITypeDecl def = typedef.getDefinition().queryInterface(ITypeDecl.class);
                    if (def != null) {
                        // System.out.println(def.getName()+" -> "+typedef.getName());
                        aliases.put(def, typedef.getName());

                        if (def.getKind() == TypeKind.DISPATCH) {
                            // if the alias is defined against dispinterface,
                            // also define the same typedef for 'interface'

                            final IDispInterfaceDecl dispi = def.queryInterface(IDispInterfaceDecl.class);
                            if (dispi.isDual()) {
                                final IInterfaceDecl custitf = dispi.getVtblInterface();
                                aliases.put(custitf, typedef.getName());
                            }
                        }
                    }
                }
                t.dispose();
            }
        }

        /**
         * Gets the simple name of the type (as opposed to FQCN.)
         */
        String getSimpleTypeName(final ITypeDecl decl) {
            assert decl.getParent().equals(lib);

            String name;
            if (aliases.containsKey(decl)) {
                name = aliases.get(decl);
            } else {
                name = decl.getName();
            }

            // check GUID
            final GUID guid = getGUID(decl);

            if (guid != null && STDOLE_TYPES.contains(guid)) {
                return "Com4jObject";
            } else {
                return name;
            }
        }

        private GUID getGUID(final ITypeDecl decl) {
            final IDispInterfaceDecl di = decl.queryInterface(IDispInterfaceDecl.class);
            if (di != null) {
                return di.getGUID();
            }

            final IInterfaceDecl ii = decl.queryInterface(IInterfaceDecl.class);
            if (ii != null) {
                return ii.getGUID();
            }

            return null;
        }

        public IndentingWriter createWriter(final String fileName) throws IOException {
            return pkg.createWriter(this, fileName);
        }

        /**
         * Generates all the code from this type library.
         */
        public void generate() throws IOException {
            final IWTypeLib tlib = lib;
            generatePackageHtml();

            final int len = tlib.count();
            // generate event interface first,
            // so that we don't generate same interface as invokable ones.
            for (int i = 0; i < len; i++) {
                final ITypeDecl t = tlib.getType(i);
                if (t.getKind() == TypeKind.COCLASS) {
                    generateEventsFrom(t.queryInterface(ICoClassDecl.class));
                }
            }

            for (int i = 0; i < len; i++) {
                final ITypeDecl t = tlib.getType(i);
                switch (t.getKind()) {
                    case DISPATCH:
                        generate(t.queryInterface(IDispInterfaceDecl.class));
                        break;
                    case INTERFACE:
                        new CustomInterfaceGenerator(this, t.queryInterface(IInterfaceDecl.class)).generate();
                        break;
                    case ENUM:
                        generate(t.queryInterface(IEnumDecl.class));
                        break;
                }
                t.dispose();
            }
        }

        private void generatePackageHtml() throws IOException {
            final PrintWriter o = createWriter("package.html");
            o.println("<html><body>");
            o.printf("<h2>%1s</h2>", lib.getName());
            o.printf("<p>%1s</p>", lib.getHelpString());
            o.println("</html></body>");
            o.close();
        }

        private void generate(final IEnumDecl t) throws IOException {

            // load all the constants first
            final int len = t.countConstants();
            final IConstant[] cons = new IConstant[len];

            for (int i = 0; i < len; i++) {
                cons[i] = t.getConstant(i);
            }

            // check if we need to use ComEnum
            boolean needComEnum = false;
            if (alwaysUseComEnums) {
                needComEnum = true;
            } else {
                for (int i = 0; i < len; i++) {
                    if (cons[i].getValue() != i) {
                        needComEnum = true;
                        break;
                    }
                }
            }

            // generate the prolog
            final String typeName = getSimpleTypeName(t);
            final IndentingWriter o = createWriter(typeName + ".java");
            generateHeader(o);

            o.beginJavaDocMode();
            if (t.getHelpString() != null) {
                o.println("<p>");
                o.println(t.getHelpString());
                o.println("</p>");
            }
            o.endJavaDocMode();

            o.printf("public enum %1s ", typeName);
            if (needComEnum) {
                o.print("implements ComEnum ");
            }
            o.println("{");
            o.in();

            // generate constants
            for (final IConstant con : cons) {
                o.beginJavaDocMode();
                if (con.getHelpString() != null) {
                    o.println("<p>");
                    o.println(con.getHelpString());
                    o.println("</p>");
                }
                o.println("<p>");
                o.println("The value of this constant is " + con.getValue());
                o.println("</p>");
                o.endJavaDocMode();
                o.print(con.getName());
                if (needComEnum) {
                    o.printf("(%1d),", con.getValue());
                } else {
                    o.print(", // ");
                    o.print(con.getValue());
                }
                o.println();
            }

            if (needComEnum) {
                // the rest of the boiler-plate code
                o.println(";");
                o.println();
                o.println("private final int value;");
                o.println(typeName + "(int value) { this.value=value; }");
                o.println("public int comEnumValue() { return value; }");
            }

            o.out();
            o.println("}");

            // clean up
            for (final IConstant con : cons) {
                con.dispose();
            }

            o.close();
        }

        private void generate(final IDispInterfaceDecl t) throws IOException {
            // if dual, always prefer the custom binding
            if (eventInterfaces.contains(t)) {
                return; // avoid generating the same interface twice, once as event, once as normal
            }
            if (t.isDual()) {
                new CustomInterfaceGenerator(this, t.getVtblInterface()).generate();
            } else {
                new DispInterfaceGenerator(this, t).generate();
            }
        }

        /**
         * Generates the event sink interfaces from this object.
         */
        private void generateEventsFrom(final ICoClassDecl co) throws IOException {
            final int len = co.countImplementedInterfaces();
            for (int i = 0; i < len; i++) {
                final IImplementedInterfaceDecl item = co.getImplementedInterface(i);
                if (item.isSource()) {
                    final ITypeDecl it = item.getType();
                    if (eventInterfaces.add(it)) {
                        final IDispInterfaceDecl di = it.queryInterface(IDispInterfaceDecl.class);
                        if (di != null) {
                            new EventInterfaceGenerator(this, di).generate();
                        }
                    }
                }
            }
        }

        void generateHeader(final IndentingWriter o) {
            generateHeader(o, null);
        }

        void generateHeader(final IndentingWriter o, String subPackage) {
            if (!pkg.isRoot() || subPackage != null) {
                if (subPackage == null) {
                    subPackage = "";
                } else {
                    subPackage = '.' + subPackage;
                }
                o.printf("package %1s%2s;", pkg.name, subPackage);
                o.println();
                o.println();
            }

            o.println("import com4j.*;");
            o.println();
        }

        public Generator parent() {
            return Generator.this;
        }
    }

    // TODO: map IFont and IPicture correctly
    private static final Set<GUID> STDOLE_TYPES = new HashSet<>(
            Arrays.asList(new GUID("00000000-0000-0000-C000-000000000046"), // IUnknown
                    new GUID("00020400-0000-0000-C000-000000000046")// , // IDispatch
            // new GUID("BEF6E002-A874-101A-8BBA-00AA00300CAB"), // IFont
            // new GUID("7BF80980-BF32-101A-8BBB-00AA00300CAB") // IPicture
            ));

    /**
     * Gets the type name for the given declaration.
     * <p>
     * This takes the followings into account:
     *
     * <ul>
     * <li>aliases (typedefs)
     * <li>types in other type libs.
     * </ul>
     */
    /* package */ String getTypeName(final ITypeDecl decl) throws BindingException {
        final Generator.LibBinder tli = getTypeLibInfo(decl.getParent());
        String pkgName = tli.pkg.name;
        final String name = tli.getSimpleTypeName(decl);
        if (pkgName.equals("com4j.stdole")) {
            if (name.equals("Com4jObject")) {
                return name;
            } else {
                return pkgName + "." + name;
            }
        }
        if (pkgName.length() > 0) {
            pkgName += '.';
        }
        return pkgName + name;
    }

    /**
     * Gets or creates a {@link LibBinder} object for the given
     * type library.
     */
    private LibBinder getTypeLibInfo(final IWTypeLib p) throws BindingException {
        LibBinder tli = typeLibs.get(p);
        if (tli == null) {
            typeLibs.put(p, tli = new LibBinder(p));
        }
        return tli;
    }
}
