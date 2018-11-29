
package com4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * @author Kohsuke Kawaguchi
 */
final class CallbackBuilder {

    class MethodTable {
        private Method[] comMethods = new Method[32];
        private int tableSize = 3; // at least we have methods for IUnknown

        public void set(final int idx, final Method m) {
            if (tableSize <= idx) {
                final Method[] buf = new Method[Math.max(idx + 1, comMethods.length * 2)];
                System.arraycopy(comMethods, 0, buf, 0, tableSize);
                comMethods = buf;
            }

            comMethods[idx] = m;
            tableSize = Math.max(tableSize, idx + 1);
        }
    }

    void build(final Class<? extends Com4jObject> type) {
        final MethodTable table = new MethodTable();
        build(type, table);

    }

    private void build(final Class<? extends Com4jObject> type, final MethodTable table) {
        if (type == Com4jObject.class) {
            return;
        }

        for (final Method m : type.getDeclaredMethods()) {
            final VTID vtid = m.getAnnotation(VTID.class);
            if (vtid == null) {
                continue; // not a COM method
            }

            final ReturnValue retVal = m.getAnnotation(ReturnValue.class);
            if (retVal != null) {
                if (retVal.defaultPropertyThrough().length != 0) {
                    continue; // this is a convenience method. not a real method signature.
                }
            }

            table.set(vtid.value(), m);
        }

        // visit the super interfaces recursively
        for (final Class c : type.getInterfaces()) {
            build(c, table);
        }
    }

    class Invoker {
        private final Method method;
        private final NativeType[] params;
        public final int[] paramConvs;

        private final Class<?>[] paramTypes;
        private final Type[] paramGenTypes;

        private final NativeType retConv;
        private final int retIndex;

        public Invoker(final Method method) {
            this.method = method;

            this.paramTypes = method.getParameterTypes();
            this.paramGenTypes = method.getGenericParameterTypes();

            // check the return value
            final ReturnValue retVal = method.getAnnotation(ReturnValue.class);
            final Class<?> retType = method.getReturnType();
            if (retType == void.class) {
                retConv = null; // no return type to check for
                retIndex = -1;
            } else {
                if (retVal != null && retVal.inout()) {
                    // no need to account for return type separately.
                    // since it also shows up as a parameter
                    retConv = null;
                } else {
                    retConv = StandardComMethod.getDefaultConversion(retType);
                }

                int i = retVal != null ? retVal.index() : -1;
                if (i == -1) {
                    i = paramTypes.length;
                }
                retIndex = i;
            }

            final MethodIntrospector mi = new MethodIntrospector(method);
            params = new NativeType[mi.paramLength()];
            paramConvs = new int[mi.paramLength()];

            for (int i = 0; i < mi.paramLength(); i++) {
                final NativeType n = mi.getParamConversation(i);
                params[i] = n;
                paramConvs[i] = n.code;
            }
        }

        Object invoke(final Object target, final Object[] args)
                throws IllegalAccessException, InvocationTargetException {
            for (int i = 0; i < args.length; i++) {
                args[i] = params[i].toJava(paramTypes[i], paramGenTypes[i], args[i]);
            }
            Object ret = method.invoke(target, args);
            if (retConv != null) {
                ret = retConv.toNative(ret);
            }
            return ret;
        }

        /**
         * Computes the size of the method arguments.
         */
        int getArgSize() {
            int sz = 0;
            for (final NativeType n : params) {
                sz += n.size;
            }
            // in other cases, return value shows up as a part of the parameter
            if (retIndex == params.length) {
                sz += retConv.size;
            }
            return sz;
        }
    }
}
