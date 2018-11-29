
package com4j;

import java.lang.reflect.Method;

/**
 * {@link ComMethod} that calls default methods.
 *
 * <p>
 * This is used so that the user can do {@code foo()}
 * whereas the real COM method invocation goes like
 * {@code foo().bar().zot()}.
 *
 * @author Kohsuke Kawaguchi
 */
public class DefaultedComMethod extends ComMethod {

    // private final Class<? extends Com4jObject>[] interfaces;
    private final int[] vtids;

    private final StandardComMethod last;

    public DefaultedComMethod(Method m, final ReturnValue retVal) {
        super(m);
        final Class<? extends Com4jObject>[] intermediates = retVal.defaultPropertyThrough();
        vtids = new int[intermediates.length];

        for (int idx = 0; idx < vtids.length; idx++) {
            final VTID id = m.getAnnotation(VTID.class);
            assert id != null;
            vtids[idx] = id.value();

            // find the next default method
            m = findDefaultMethod(intermediates[idx]);
            if (m == null) {
                throw new IllegalAnnotationException(
                        intermediates[idx].getName() + " needs to have a method with @DefaultMethod");
            }
        }

        last = new StandardComMethod(m);
    }

    private Method findDefaultMethod(final Class<?> intf) {
        for (final Method m : intf.getMethods()) {
            if (m.getAnnotation(DefaultMethod.class) != null) {
                return m;
            }
        }
        return null;
    }

    @Override
    Object invoke(long ptr, final Object[] args) {
        Native.addRef(ptr);

        // invoke default properties
        for (final int vtid : vtids) {
            final long newPtr = (Long) Native.invoke(ptr, vtid, EMPTY_ARRAY, EMPTY_INTARRAY, 0, false,
                    NativeType.ComObject.code);
            Native.release(ptr);
            ptr = newPtr;
        }

        final Object r = last.invoke(ptr, args);
        Native.release(ptr);

        return r;
    }

    private static final Object[] EMPTY_ARRAY = new Object[0];
    private static final int[] EMPTY_INTARRAY = new int[0];
}
