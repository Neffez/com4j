
package com4j;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * Defines "toolkit" to introspect a COM-bound method.
 *
 * @author Kohsuke Kawaguchi
 */
class MethodIntrospector {

    final Method method;
    final Annotation[][] pa;
    final Type[] paramTypes;

    protected MethodIntrospector(final Method method) {
        this.method = method;
        this.pa = method.getParameterAnnotations();
        this.paramTypes = method.getGenericParameterTypes();
    }

    protected final MarshalAs getMarshalAs(final int idx) {
        for (final Annotation a : pa[idx]) {
            if (a instanceof MarshalAs) {
                return (MarshalAs) a;
            }
        }
        return null;
    }

    protected final NativeType getParamConversation(final int idx) {
        final MarshalAs ma = getMarshalAs(idx);
        if (ma != null) {
            return ma.value();
        } else {
            return StandardComMethod.getDefaultConversion(paramTypes[idx]);
        }
    }

    protected final int paramLength() {
        return pa.length;
    }
}
