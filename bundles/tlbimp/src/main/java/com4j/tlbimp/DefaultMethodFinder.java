
package com4j.tlbimp;

import java.util.HashMap;
import java.util.Map;

import com4j.GUID;
import com4j.tlbimp.def.IInterface;
import com4j.tlbimp.def.IMethod;

/**
 * Finds default methods from {@link IInterface}.
 *
 * @author Kohsuke Kawaguchi
 */
final class DefaultMethodFinder {
    private final Map<GUID, IMethod> cache = new HashMap<>();

    public IMethod getDefaultMethod(final IInterface intf) {
        final GUID guid = intf.getGUID();
        if (cache.containsKey(guid)) {
            return cache.get(guid);
        }

        final IMethod r = doGetDefaultMethod(intf);
        cache.put(guid, r);
        return r;
    }

    private IMethod doGetDefaultMethod(final IInterface intf) {
        final int len = intf.countMethods();
        for (int i = 0; i < len; i++) {
            final IMethod m = intf.getMethod(i);
            if (m.getDispId() == 0) {
                return m;
            }
        }
        return null;
    }
}
