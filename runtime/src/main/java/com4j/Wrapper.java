
package com4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * {@link InvocationHandler} that backs up a COM object.
 *
 * <h2>Garbage Collection and IUnknown::Release</h2>
 * <p>
 * {@link Wrapper} holds on to a COM pointer, which needs to be released eventually, before the object
 * gets recycled. We do this by using phantom references.
 *
 * <p>
 * Every wrapper owns {@link NativePointerPhantomReference} to itself. We'll have this reference queued
 * to {@link ComThread#collectableObjects} when GC determines that the object is no longer needed,
 * or we'll explicitly enqueue it when {@link #dispose()} is called. {@link ComThread} will periodically
 * wake up and go through the queue to release interface pointers.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 * @author Michael Schnell (ScM, (C) 2008, 2009, Michael-Schnell@gmx.de)
 */
final class Wrapper implements InvocationHandler, Com4jObject {
    /**
     * name of this wrapper. This is for debug purposes.
     */
    private String name;

    private static final Method[] DISPOSE_METHODS = new Method[2];
    static {
        try {
            DISPOSE_METHODS[0] = Wrapper.class.getDeclaredMethod("dispose");
            DISPOSE_METHODS[1] = Com4jObject.class.getDeclaredMethod("dispose");
        } catch (final SecurityException e) {
            throw new RuntimeException(e);
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * interface pointer.
     */
    private final long ptr;

    private volatile boolean isDisposed = false;

    /**
     * Cached hash code. The value of {@code IUnknown*}.
     */
    private volatile long hashCode = 0;

    /**
     * All the invocation to the wrapper COM object must go through this thread.
     */
    private final ComThread thread;

    /**
     * A phantom reference that owns the native pointer. When this ref is enqueue,
     * the com thread will release() the native pointer.
     */
    final NativePointerPhantomReference ref;

    /**
     * Cached of {@link ComMethod} keyed by the method declaration.
     *
     * TODO: revisit the cache design
     */
    private final Map<Method, ComMethod> cache = Collections.synchronizedMap(new WeakHashMap<Method, ComMethod>());

    /**
     * Wraps a new COM object. The pointer needs to be addRefed by the caller if needed.
     */
    private Wrapper(final long ptr) {
        if (ptr == 0) {
            throw new IllegalArgumentException();
        }
        assert ComThread.isComThread();

        this.ptr = ptr;
        thread = ComThread.get();
        ref = new NativePointerPhantomReference(this, thread.collectableObjects, ptr);
        thread.addLiveObject(this);
    }

    /**
     * Creates a new proxy object to a given COM pointer.
     * <p>
     * Must be run from a {@link ComThread}. This method doesn't do AddRef.
     */
    static <T extends Com4jObject> T create(final Class<T> primaryInterface, final long ptr) {
        final Wrapper w = new Wrapper(ptr);
        final T r = primaryInterface.cast(
                Proxy.newProxyInstance(primaryInterface.getClassLoader(), new Class<?>[] { primaryInterface }, w));
        return r;
    }

    /**
     *
     * @deprecated 64bit unsafe.
     */
    @Deprecated
    static Com4jObject create(final int ptr) {
        return create((long) ptr);
    }

    /**
     * Creates a new proxy object to a given COM pointer.
     * <p>
     * Must be run from a {@link ComThread}.
     */
    static Com4jObject create(final long ptr) {
        final Wrapper w = new Wrapper(ptr);
        return w;
    }

    /**
     * Returns the wrapped interface pointer as an integer
     *
     * @return The wrapped interface pointer.
     */
    @Override
    public int getPtr() {
        return (int) ptr;
    }

    @Override
    public long getPointer() {
        return ptr;
    }

    @Override
    public ComThread getComThread() {
        return thread;
    }

    private static final Object[] EMPTY_ARRAY = new Object[0];

    @Override
    public Object invoke(final Object proxy, final Method method, Object[] args) throws Throwable {
        if (isDisposed && method != DISPOSE_METHODS[0] && method != DISPOSE_METHODS[1]) {
            throw new IllegalStateException("COM object is already disposed");
        }
        if (args == null) {
            args = EMPTY_ARRAY;
        }

        final Class<?> declClazz = method.getDeclaringClass();

        if (declClazz == Com4jObject.class || declClazz == Object.class) {
            // method declared on Com4jObject is not meant to be delegated.
            try {
                return method.invoke(this, args);
            } catch (final IllegalAccessException e) {
                throw new IllegalAccessError(e.getMessage());
            } catch (final InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
        final UseDefaultValues useDefaultValues = method.getAnnotation(UseDefaultValues.class);

        if (useDefaultValues != null) {
            final int defValCount = useDefaultValues.optParamIndex().length;
            final Object[] newArgs = new Object[args.length + defValCount];
            // fill in the given arguments to the right place:
            for (int i = 0; i < args.length; i++) {
                newArgs[useDefaultValues.paramIndexMapping()[i]] = args[i];
            }
            // Fill in the (optional) default values:
            final ComMethod comMethod = getMethod(method);
            for (int i = 0; i < defValCount; i++) {
                final Object defParam = comMethod.defaultParameters[i];
                newArgs[useDefaultValues.optParamIndex()[i]] = defParam;
            }
            args = newArgs;
        }

        if (invCache == null) {
            invCache = new InvocationThunk();
        }
        try {
            return invCache.invoke(getMethod(method), args);
        } catch (final ExecutionException e) {
            if (e.getCause() instanceof ComException) {
                throw new ComException((ComException) e.getCause());
            }
            throw e;
        }
    }

    private ComMethod getMethod(final Method method) {
        ComMethod r = cache.get(method);
        if (r != null) {
            return r;
        }

        r = createComMethod(method);
        cache.put(method, r);
        return r;
    }

    private ComMethod createComMethod(final Method method) {
        final ReturnValue rv = method.getAnnotation(ReturnValue.class);
        if (rv != null && rv.defaultPropertyThrough().length > 0) {
            return new DefaultedComMethod(method, rv);
        }

        // prefer the custom interface.
        final VTID vtid = method.getAnnotation(VTID.class);
        if (vtid != null) {
            return new StandardComMethod(method);
        }

        final DISPID dispid = method.getAnnotation(DISPID.class);
        if (dispid != null) {
            return new DispatchComMethod(method);
        }

        throw new IllegalAnnotationException(
                "Missing annotation: You need to specify at least one of @DISPID or @VTID");
    }

    /**
     * Disposes the native part of this Wrapper. That is, calling Release on the interface pointer. After a wrapper is
     * disposed,
     * every call to a COM method will raise an {@link IllegalStateException}
     */
    @Override
    public void dispose() {
        if (!isDisposed) {
            new Task<Void>() {
                @Override
                public Void call() {
                    dispose0();
                    return null;
                }
            }.execute(thread); // Issue 39 fixed.
        }
    }

    private void dispose0() {
        if (!isDisposed) {
            ref.releaseNative();
            isDisposed = true;
        }
    }

    @Override
    public <T extends Com4jObject> boolean is(final Class<T> comInterface) {
        try {
            final GUID iid = COM4J.getIID(comInterface);
            return new QITestTask(iid).execute(thread) != 0;
        } catch (final ComException e) {
            return false;
        }
    }

    /**
     * Returns whether this object was already disposed.
     *
     * @return true if this object was disposed, false otherwise.
     */
    public boolean isDisposed() {
        return isDisposed;
    }

    @Override
    public <T extends Com4jObject> T queryInterface(final Class<T> comInterface) {
        return new Task<T>() {
            @Override
            public T call() {
                final GUID iid = COM4J.getIID(comInterface);
                final long nptr = Native.queryInterface(ptr, iid);
                if (nptr == 0) {
                    return null; // failed to cast
                }
                return create(comInterface, nptr);
            }
        }.execute(thread);
    }

    @Override
    public <T> EventProxy<?> advise(final Class<T> eventInterface, final T object) {
        return new Task<EventProxy<?>>() {
            @Override
            public EventProxy<?> call() {
                final IConnectionPointContainer cpc = queryInterface(IConnectionPointContainer.class);
                if (cpc == null) {
                    throw new ComException("This object doesn't have event source", -1);
                }
                final GUID iid = COM4J.getIID(eventInterface);
                final Com4jObject cp = cpc.FindConnectionPoint(iid);
                final EventProxy<T> proxy = new EventProxy<>(eventInterface, object);
                proxy.nativeProxy = Native.advise(cp.getPointer(), proxy, iid.v[0], iid.v[1]);

                // clean up resources to be nice
                cpc.dispose();
                cp.dispose();

                return proxy;
            }
        }.execute();
    }

    @Override
    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        if (name == null) {
            return "ComObject:" + Long.toHexString(ptr);
        } else {
            return name + ":" + Long.toHexString(ptr);
        }
    }

    @Override
    public int hashCode() {
        final long l = getIUnknownPointer();
        return (int) (l ^ l >>> 32);
    }

    @Override
    public long getIUnknownPointer() {
        if (hashCode == 0) {
            if (isDisposed) {
                hashCode = 0;
            } else {
                hashCode = new QITestTask(COM4J.IID_IUnknown).execute(thread);
            }
        }
        return hashCode;
    }

    @Override
    public boolean equals(final Object rhs) {
        if (!(rhs instanceof Com4jObject)) {
            return false;
        }
        return this.getIUnknownPointer() == ((Com4jObject) rhs).getIUnknownPointer();
    }

    /**
     * Used to pass parameters/return values between the host thread
     * and the peer {@link ComThread}.
     */
    private class InvocationThunk extends Task<Object> {
        private ComMethod method;
        private Object[] args;

        /**
         * Invokes the method on the peer {@link ComThread} and returns
         * its return value.
         *
         * @param method The {@link ComMethod} to invoke
         * @param args The arguments of the method
         * @return Returns the return value of the invoked method
         */
        public synchronized Object invoke(final ComMethod method, final Object[] args) {
            invCache = null;
            this.method = method;
            this.args = args;

            try {
                return execute(thread);
            } finally {
                invCache = this;
            }
        }

        /**
         * Called from {@link ComThread} to actually carry out the execution.
         *
         * @return Returns the return value of the invoked method
         */
        @Override
        public synchronized Object call() {
            final Object r = method.invoke(ptr, args);
            // clear fields that are no longer necessary
            method = null;
            args = null;
            return r;
        }
    }

    /**
     * We cache up to one InvocationThunk.
     */
    InvocationThunk invCache;

    /**
     * Invokes QueryInterface but immediately releases that pointer.
     * Useful for checking if an object implements a particular interface.
     */
    private final class QITestTask extends Task<Long> {
        private final GUID iid;

        public QITestTask(final GUID iid) {
            this.iid = iid;
        }

        @Override
        public Long call() {
            final long nptr = Native.queryInterface(ptr, iid);
            if (nptr != 0) {
                Native.release(nptr);
            }
            return nptr;
        }
    }
}
