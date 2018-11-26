
package com4j;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * For receiving events from COM, we use one native object (CEventReceiver)
 * to handle things in the native side, and this to handle things in the Java side.
 *
 *
 * <p>
 * {@code T} is the event interface.
 *
 * @author Kohsuke Kawaguchi
 */
final class EventProxy<T> implements EventCookie {

    private final EventInterfaceDescriptor<T> descriptor;
    private final T javaObject;

    /**
     * Pointer to the native proxy.
     */
    long nativeProxy;

    /**
     * Creates a new event proxy that implements the event interface {@code intf}
     * and delivers events to {@code javaObject}.
     */
    EventProxy(final Class<T> intf, final T javaObject) {
        this.descriptor = getDescriptor(intf);
        this.javaObject = javaObject;
    }

    /**
     * Terminates the event subscription.
     */
    @Override
    public void close() {
        if (nativeProxy != 0) {
            new Task<Void>() {
                @Override
                public Void call() {
                    Native.unadvise(nativeProxy);
                    return null;
                }
            }.execute();
            nativeProxy = 0;
        }
    }

    int[] getDISPIDs(final String[] names) {
        final int[] r = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            r[i] = descriptor.getDISPID(names[i]);
        }
        return r;
    }

    Object invoke(final int dispId, final int flag, final Variant[] args) throws Throwable {
        final EventMethod m = descriptor.get(dispId);
        if (m == null) {
            throw new ComException("Undefined DISPID=" + dispId, DISP_E_MEMBERNOTFOUND);
        }
        return m.invoke(javaObject, flag, args);
    }

    //
    // Used by the native code to assist exception handling
    //
    static String getErrorSource(final Throwable t) {
        return t.toString();
    }

    static String getErrorDetail(final Throwable t) {
        final StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * Descriptor cache.
     */
    private static final Map<Class, EventInterfaceDescriptor> descriptors = Collections
            .synchronizedMap(new WeakHashMap<Class, EventInterfaceDescriptor>());

    /**
     * Gets the descriptor for the given type.
     */
    private static <T> EventInterfaceDescriptor<T> getDescriptor(final Class<T> t) {
        EventInterfaceDescriptor<T> r = descriptors.get(t);
        if (r == null) {
            r = new EventInterfaceDescriptor<>(t);
            descriptors.put(t, r);
        }
        return r;
    }

    /**
     * Describes the event interface.
     */
    private static class EventInterfaceDescriptor<T> {
        private final Class<T> eventInterface;

        /**
         * Methods by their names.
         */
        private final Map<String, EventMethod> methodsByName = new HashMap<>();

        private final Map<Integer, EventMethod> methodsByID = new HashMap<>();

        EventInterfaceDescriptor(final Class<T> eventInterface) {
            this.eventInterface = eventInterface;

            for (final Method m : eventInterface.getDeclaredMethods()) {
                final EventMethod em = new EventMethod(m);
                methodsByName.put(m.getName(), em);
                methodsByID.put(em.dispid, em);
            }
        }

        public int getDISPID(final String name) {
            final EventMethod r = methodsByName.get(name);
            if (r == null) {
                return DISP_E_UNKNOWNNAME;
            } else {
                return r.dispid;
            }
        }

        public EventMethod get(final int id) {
            return methodsByID.get(id);
        }
    }

    private static class EventMethod {
        private final int dispid;
        private final Method method;
        private final Class<?>[] params;

        public EventMethod(final Method m) {
            final DISPID a = m.getAnnotation(DISPID.class);
            if (a == null) {
                throw new IllegalAnnotationException(m + " needs to have @DISPID");
            }
            dispid = a.value();
            method = m;
            params = m.getParameterTypes();
        }

        /**
         * Invokes a method.
         */
        public Object invoke(final Object o, final int flag, final Variant[] args) throws Throwable {
            try {
                if (args.length != params.length) {
                    throw new ComException(
                            "Argument length mismatch. Expected " + params.length + " but found " + args.length,
                            DISP_E_BADPARAMCOUNT);
                }

                final Object[] oargs = new Object[args.length];
                for (int i = 0; i < args.length; i++) {
                    oargs[i] = args[i].convertTo(params[i]);
                }
                return method.invoke(o, oargs);
            } catch (final InvocationTargetException e) {
                logger.log(Level.WARNING, method + " on " + o + " reported an exception", e.getTargetException());
                throw e.getTargetException();
            } catch (final RuntimeException e) {
                logger.log(Level.WARNING, "Unable to invoke " + method + " on " + o, e);
                throw e;
            }
        }
    }

    private static final int DISP_E_UNKNOWNNAME = 0x80020006;
    private static final int DISP_E_MEMBERNOTFOUND = 0x80020003;
    private static final int DISP_E_BADPARAMCOUNT = 0x8002000E;

    private static final Logger logger = Logger.getLogger(EventProxy.class.getName());

    static {
        boolean com4jDebug = false;
        try {
            com4jDebug = System.getProperty("com4j.debug") != null;
        } catch (final Throwable e) {
            ;
        }

        if (!com4jDebug) {
            logger.setLevel(Level.OFF);
        }
    }
}
