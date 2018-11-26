
package com4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Provides faster number &lt;-> enum conversion.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
abstract class EnumDictionary<T extends Enum<T>> {
    protected final Class<T> clazz;

    private EnumDictionary(final Class<T> clazz) {
        this.clazz = clazz;
        assert clazz.isEnum();
    }

    /**
     * Looks up a dictionary from an enum class.
     */
    public static <T extends Enum<T>> EnumDictionary<T> get(final Class<T> clazz) {
        EnumDictionary<T> dic = registry.get(clazz);
        if (dic == null) {
            final boolean sparse = ComEnum.class.isAssignableFrom(clazz);
            if (sparse) {
                dic = new Sparse<>(clazz);
            } else {
                dic = new Continuous<>(clazz);
            }
            registry.put(clazz, dic);
        }
        return dic;
    }

    /**
     * Convenience method to be invoked by JNI.
     */
    static <T extends Enum<T>> T get(final Class<T> clazz, final int v) {
        return get(clazz).constant(v);
    }

    /**
     * Gets the integer value for the given enum constant.
     */
    abstract int value(Enum<T> t);

    /**
     * Gets the enum constant object from its integer value.
     */
    abstract T constant(int v);

    /**
     * For enum constants that doesn't use any {@link ComEnum}.
     */
    static class Continuous<T extends Enum<T>> extends EnumDictionary<T> {
        private final T[] consts;

        private Continuous(final Class<T> clazz) {
            super(clazz);
            consts = clazz.getEnumConstants();
        }

        @Override
        public int value(final Enum<T> t) {
            return t.ordinal();
        }

        @Override
        public T constant(final int v) {
            return consts[v];
        }
    }

    /**
     * For enum constants with {@link ComEnum}.
     */
    static class Sparse<T extends Enum<T>> extends EnumDictionary<T> {
        private final Map<Integer, T> fromValue = new HashMap<>();

        private Sparse(final Class<T> clazz) {
            super(clazz);

            final T[] consts = clazz.getEnumConstants();
            for (final T v : consts) {
                fromValue.put(((ComEnum) v).comEnumValue(), v);
            }
        }

        @Override
        public int value(final Enum<T> t) {
            return ((ComEnum) t).comEnumValue();
        }

        @Override
        public T constant(final int v) {
            final T t = fromValue.get(v);
            if (t == null) {
                throw new IllegalArgumentException(clazz.getName() + " has no constant of the value " + v);
            }
            return t;
        }
    }

    private static final Map<Class<? extends Enum>, EnumDictionary> registry = Collections
            .synchronizedMap(new WeakHashMap<Class<? extends Enum>, EnumDictionary>());
}
