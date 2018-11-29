
package com4j.tlbimp;

import java.util.ArrayList;
import java.util.List;

import com4j.GUID;
import com4j.tlbimp.Generator.LibBinder;
import com4j.tlbimp.def.IDispInterfaceDecl;
import com4j.tlbimp.def.IMethod;
import com4j.tlbimp.def.IProperty;
import com4j.tlbimp.def.IType;
import com4j.tlbimp.def.InvokeKind;

/**
 * Generates the out-going (AKA event sink) interface.
 *
 * @author Kohsuke Kawaguchi
 * @author Michael Schnell (scm, (C) 2008, Michael-Schnell@gmx.de)
 */
final class EventInterfaceGenerator extends InterfaceGenerator<IDispInterfaceDecl> {
    public EventInterfaceGenerator(final LibBinder lib, final IDispInterfaceDecl t) {
        super(lib, t);
    }

    @Override
    protected String getClassDecl() {
        return "public abstract class";
    }

    @Override
    protected List<String> getBaseTypes() {
        return new ArrayList<>();
    }

    @Override
    protected GUID getIID() {
        return t.getGUID();
    }

    @Override
    protected String getSubPackageName() {
        return "events";
    }

    @Override
    protected void generateMethod(final IMethod m, final IndentingWriter o) throws BindingException {
        final InvokeKind kind = m.getKind();
        if (kind != InvokeKind.FUNC || isBogusDispatchMethod(m)) {
            return;
        }

        final MethodBinder mb = new MethodBinderImpl(g, m);
        mb.declare(o);
        o.println();
    }

    @Override
    protected void generateProperty(final IProperty p, final IndentingWriter o) throws BindingException {
        throw new BindingException("Don't know how to generate a COM Property for a event interface");
    }

    private final class MethodBinderImpl extends MethodBinder {
        public MethodBinderImpl(final Generator g, final IMethod method) throws BindingException {
            super(g, method);
        }

        @Override
        protected IType getReturnTypeBinding() {
            return getDispInterfaceReturnType();
        }

        @Override
        protected void terminate(final IndentingWriter o) {
            o.println(" {");
            o.in();
            o.println("    throw new UnsupportedOperationException();");
            o.out();
            o.println("}");
        }

        @Override
        protected void annotate(final IndentingWriter o) {
            super.annotate(o);
            o.printf("@DISPID(%1d)", method.getDispId());
            o.println();
        }

        @Override
        protected void generateAccessModifier(final IndentingWriter o) {
            o.print("public ");
        }
    }

    static boolean isBogusDispatchMethod(final IMethod m) {
        // some type libraries contain IDispatch methods on DispInterface definitions.
        // don't map them. I'm not too sure if this is the right check,
        // but they seem to work.
        //
        // normal disp interfaces return 0 from this, so we need to handle QueryInterface
        // differently
        final int vidx = m.getVtableIndex();
        return 1 <= vidx && vidx < 7 || vidx == 0 && m.getName().toLowerCase().equals("queryinterface");
    }
}
