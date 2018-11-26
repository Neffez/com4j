
package com4j.tlbimp;

import java.util.ArrayList;
import java.util.List;

import com4j.GUID;
import com4j.IID;
import com4j.tlbimp.Generator.LibBinder;
import com4j.tlbimp.def.IDispInterfaceDecl;
import com4j.tlbimp.def.IMethod;
import com4j.tlbimp.def.IProperty;
import com4j.tlbimp.def.IType;

/**
 * Generates a disp-only interface.
 *
 * @author Kohsuke Kawaguchi
 * @author Michael Schnell (scm, (C) 2008, Michael-Schnell@gmx.de)
 */
final class DispInterfaceGenerator extends InvocableInterfaceGenerator<IDispInterfaceDecl> {

    public DispInterfaceGenerator(final LibBinder lib, final IDispInterfaceDecl t) {
        super(lib, t);
    }

    @Override
    protected List<String> getBaseTypes() {
        return new ArrayList<>(); // needs to return a fresh list
    }

    @Override
    protected GUID getIID() {
        return GUID_IDISPATCH;
    }

    @Override
    protected MethodBinderImpl createMethodBinder(final IMethod m) throws BindingException {
        if (EventInterfaceGenerator.isBogusDispatchMethod(m)) {
            return null;
        } else {
            return new MethodBinderImpl(g, m);
        }
    }

    private final class MethodBinderImpl extends InvocableInterfaceGenerator<IDispInterfaceDecl>.MethodBinderImpl {
        public MethodBinderImpl(final Generator g, final IMethod method) throws BindingException {
            super(g, method);
        }

        @Override
        protected IType getReturnTypeBinding() {
            return getDispInterfaceReturnType();
        }

        @Override
        protected void annotate(final IndentingWriter o) {
            super.annotate(o);
            if (t.isDual()) {
                o.printf("@VTID(%1d)", method.getVtableIndex());
                o.println();
            }
            o.printf("@DISPID(%1d)", method.getDispId());
            o.println();
            switch (method.getKind()) {
                case PROPERTYGET:
                    o.println("@PropGet");
                    break;
                case PROPERTYPUT:
                case PROPERTYPUTREF:
                    o.println("@PropPut");
                    break;
            }
        }
    }

    @Override
    protected void generateProperty(final IProperty p, final IndentingWriter o) throws BindingException {
        final TypeBinding tb = TypeBinding.bind(this.g, p.getType(), null);
        final String typeString = tb.javaType;

        final String propName = p.getName().substring(0, 1).toUpperCase() + p.getName().substring(1);

        o.beginJavaDocMode();
        final String help = p.getHelpString();
        if (help != null) {
            o.println("<p>");
            o.println(help);
            o.println("</p>");
        }
        o.println("<p>");
        o.println("Getter method for the COM property \"" + p.getName() + "\"");
        o.println("</p>");
        o.println("@return The COM property " + p.getName() + " as a " + typeString);
        o.endJavaDocMode();
        o.printf("@DISPID(%1d)", p.getDispId());
        o.println();
        o.println("@PropGet");
        o.printf("%s get%s();", typeString, propName);
        o.println();
        o.println();

        o.beginJavaDocMode();
        if (help != null) {
            o.println("<p>");
            o.print(help);
            o.println("</p>");
        }
        o.println("<p>");
        o.println("Setter method for the COM property \"" + p.getName() + "\"");
        o.println("</p>");
        o.println("@param newValue The new value for the COM property " + p.getName() + " as a " + typeString);
        o.endJavaDocMode();
        o.printf("@DISPID(%1d)", p.getDispId());
        o.println();
        o.println("@PropPut");
        o.printf("void set%s(%s newValue);", propName, typeString);
        o.println();
        o.println();
    }

    private static final GUID GUID_IDISPATCH = new GUID(IID.IDispatch);
}
