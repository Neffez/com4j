
package com4j.tlbimp;

import java.util.ArrayList;
import java.util.List;

import com4j.ReturnValue;
import com4j.tlbimp.Generator.LibBinder;
import com4j.tlbimp.def.IDispInterfaceDecl;
import com4j.tlbimp.def.IInterface;
import com4j.tlbimp.def.IInterfaceDecl;
import com4j.tlbimp.def.IMethod;
import com4j.tlbimp.def.IPtrType;
import com4j.tlbimp.def.IType;

/**
 * Common code for generating interfaces that user application calls.
 *
 * <p>
 * Namely the common code between {@link CustomInterfaceGenerator}
 * and {@link DispInterfaceGenerator}.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class InvocableInterfaceGenerator<T extends IInterface> extends InterfaceGenerator<T> {
    protected InvocableInterfaceGenerator(final LibBinder lib, final T t) {
        super(lib, t);
    }

    @Override
    protected final String getClassDecl() {
        return "public interface";
    }

    @Override
    protected final void generateMethod(final IMethod m, final IndentingWriter o) throws BindingException {
        try {
            final MethodBinderImpl mb = createMethodBinder(m);
            if (mb == null) {
                return;
            }
            mb.declare(o);
            o.println();

            mb.generateDefaultInterfaceFacade(o);
        } catch (final BindingException e) {
            e.addContext("method " + m.getName());
            throw e;
        }
    }

    @Override
    protected final void generateExtends(final IndentingWriter o) {
        boolean hasEnum = false;
        for (int j = 0; j < t.countMethods() && !hasEnum; j++) {
            final IMethod m = t.getMethod(j);
            hasEnum = MethodBinder.isEnum(m);
        }

        final List<String> baseTypes = getBaseTypes();
        if (baseTypes.isEmpty()) {
            // if it has a base type, it indirectly inherits Com4jObject,
            // so no point in declaring it explicitly.
            baseTypes.add("Com4jObject");
        }
        if (hasEnum) {
            baseTypes.add("Iterable<Com4jObject>");
        }

        o.print(" extends ");
        o.beginCommaMode();
        for (final String name : baseTypes) {
            o.comma();
            o.print(name);
        }
        o.endCommaMode();
    }

    /**
     * Returns a {@link MethodBinderImpl} that generates a method definition.
     *
     * @return
     * null to skip the generation
     */
    protected abstract MethodBinderImpl createMethodBinder(IMethod m) throws BindingException;

    /**
     * Partially implemented common {@link MethodBinder} implementation.
     */
    protected abstract class MethodBinderImpl extends MethodBinder {
        protected MethodBinderImpl(final Generator g, final IMethod method) throws BindingException {
            super(g, method);
        }

        @Override
        protected final void terminate(final IndentingWriter o) {
            o.println(";");
        }

        /**
         * Generates a method that uses {@link ReturnValue#defaultPropertyThrough()}
         * if applicable, or otherwise no-op.
         */
        final void generateDefaultInterfaceFacade(final IndentingWriter o) throws BindingException {
            IMethod m = method;
            final List<IType> intermediates = new ArrayList<>();

            while (true) {
                final MethodBinderImpl mb = createMethodBinder(m);
                // only handle methods of the form "HRESULT foo([out,retval]IFoo** ppOut);
                // TODO: Check: do not handle enums?? Causes NullPointerExceptions..
                if (m.getParamCount() != 1 || mb.retParam != 0 || mb.params[mb.retParam].isIn()
                        || MethodBinder.isEnum(m)) {
                    break;
                }

                // we expect it to be an interface pointer.
                final IPtrType pt = mb.returnType.queryInterface(IPtrType.class);
                IDispInterfaceDecl di = null;
                IInterfaceDecl ii = null;
                if (pt != null) {
                    final IType t = pt.getPointedAtType();
                    di = t.queryInterface(IDispInterfaceDecl.class);
                    ii = t.queryInterface(IInterfaceDecl.class);
                }

                if (di == null && ii == null) {
                    break;
                }

                IInterface intf;
                if (ii != null) {
                    intf = ii;
                } else {
                    if (di.isDual()) {
                        intf = di.getVtblInterface();
                    } else {
                        break;
                    }
                }

                // does this target interface has a default method?
                final IMethod dm = g.dmf.getDefaultMethod(intf);
                if (dm == null) {
                    return;
                }

                // recursively check...
                m = dm;
                intermediates.add(pt);
            }

            if (intermediates.isEmpty()) {
                return; // no default method to generate
            }

            if (m.getParamCount() < 2) {
                return; // the default method has to have at least one in param and one ret val
            }

            // TODO: check if this is correct.
            if (createMethodBinder(m).retParam < 0) {
                return; // there is no return value.. This would cause a NullPointerException
            }

            o.printf("@VTID(%1d)", method.getVtableIndex());
            o.println();

            final MethodBinderImpl mb = createMethodBinder(m);
            mb.declareReturnType(o, intermediates, false);
            this.declareMethodName(o);
            mb.declareParameters(o, null);
            o.println();
        }
    }
}
