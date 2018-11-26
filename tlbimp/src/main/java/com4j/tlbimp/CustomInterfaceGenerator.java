
package com4j.tlbimp;

import java.util.ArrayList;
import java.util.List;

import com4j.GUID;
import com4j.tlbimp.Generator.LibBinder;
import com4j.tlbimp.def.IDispInterfaceDecl;
import com4j.tlbimp.def.IInterfaceDecl;
import com4j.tlbimp.def.IMethod;
import com4j.tlbimp.def.IProperty;

/**
 * Generates custom interface definition.
 *
 * @author Kohsuke Kawaguchi
 * @author Michael Schnell (scm, (C) 2008, Michael-Schnell@gmx.de)
 */
final class CustomInterfaceGenerator extends InvocableInterfaceGenerator<IInterfaceDecl> {
    CustomInterfaceGenerator(final LibBinder lib, final IInterfaceDecl t) {
        super(lib, t);
    }

    @Override
    protected List<String> getBaseTypes() {
        final List<String> r = new ArrayList<>();

        for (int i = 0; i < t.countBaseInterfaces(); i++) {
            try {
                final String baseName = g.getTypeName(t.getBaseInterface(i));
                r.add(baseName);
            } catch (final BindingException e) {
                e.addContext("interface " + simpleName);
                g.el.error(e);
            }
        }

        return r;
    }

    @Override
    protected GUID getIID() {
        return t.getGUID();
    }

    @Override
    protected MethodBinderImpl createMethodBinder(final IMethod m) throws BindingException {
        return new MethodBinderImpl(g, m);
    }

    private final class MethodBinderImpl extends InvocableInterfaceGenerator<IInterfaceDecl>.MethodBinderImpl {
        public MethodBinderImpl(final Generator g, final IMethod method) throws BindingException {
            super(g, method);
        }

        @Override
        protected void annotate(final IndentingWriter o) {
            super.annotate(o);
            final IDispInterfaceDecl disp = t.queryInterface(IDispInterfaceDecl.class);
            if (t.isDual()) {
                o.printf("@DISPID(%1d) //= 0x%1x. The runtime will prefer the VTID if present", method.getDispId(),
                        method.getDispId());
                o.println();
            }
            o.printf("@VTID(%1d)", method.getVtableIndex());
            o.println();
        }

        @Override
        protected boolean needsMarshalAs() {
            return true;
        }
    }

    @Override
    protected void generateProperty(final IProperty p, final IndentingWriter o) throws BindingException {
        throw new BindingException("Don't know how to generate a COM Property for a custom interface");
    }
}
