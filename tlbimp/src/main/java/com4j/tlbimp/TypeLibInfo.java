
package com4j.tlbimp;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com4j.COM4J;
import com4j.ComException;
import com4j.GUID;

/**
 * Locates
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public final class TypeLibInfo {

    /**
     * Human readable library name.
     */
    public final String libName;

    /**
     * Type library file.
     */
    public final File typeLibrary;

    /**
     * Type library version.
     */
    public final Version version;

    /**
     * Locale ID.
     */
    public final int lcid;

    public TypeLibInfo(final String libName, final File typeLibrary, final Version version, final int lcid) {
        this.libName = libName;
        this.typeLibrary = typeLibrary;
        this.version = version;
        this.lcid = lcid;
    }

    /**
     * Locates the type library file from the LIBID (a GUID) and an optional version number.
     *
     * @param libid
     *     String of the form "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
     * @param version
     *     Optional version number. If null, the function searches for the latest version.
     *
     * @throws ComException
     *     If it fails to find the type library.
     */
    public static TypeLibInfo locate(final GUID libid, String version) throws BindingException {
        // make sure to load the com4j.dll
        COM4J.IID_IUnknown.toString();

        // check if libid is correct
        if (libid == null) {
            throw new IllegalArgumentException();
        }
        final String libKey = "TypeLib\\" + libid;
        try {
            Native.readRegKey(libKey);
        } catch (final ComException e) {
            throw new BindingException(Messages.INVALID_LIBID.format(libid), e);
        }

        if (version == null) {
            // find the latest version
            final List<Version> versions = new ArrayList<>();
            for (final String v : Native.enumRegKeys(libKey)) {
                versions.add(new Version(v));
            }
            Collections.sort(versions);

            if (versions.size() == 0) {
                throw new BindingException(Messages.NO_VERSION_AVAILABLE.format());
            }

            version = versions.get(versions.size() - 1).toString();
        }

        final String verKey = "TypeLib\\" + libid + "\\" + version;
        String libName;
        try {
            libName = Native.readRegKey(verKey);
        } catch (final ComException e) {
            throw new BindingException(Messages.INVALID_VERSION.format(version), e);
        }

        final Set<Integer> lcids = new HashSet<>();

        for (final String lcid : Native.enumRegKeys(verKey)) {
            try {
                lcids.add(Integer.valueOf(lcid));
            } catch (final NumberFormatException e) {
                ; // ignore "FLAGS" and "HELPDIR"
            }
        }

        int lcid;
        if (lcids.contains(0)) {
            lcid = 0;
        } else {
            lcid = lcids.iterator().next();
        }

        String fileName;
        try {
            fileName = Native.readRegKey(verKey + "\\" + lcid + "\\win32");
        } catch (final ComException e) {
            throw new BindingException(Messages.NO_WIN32_TYPELIB.format(libid, version), e);
        }

        return new TypeLibInfo(libName, new File(fileName), new Version(version), lcid);
    }

    /**
     * Represents the version number of the form "x.y"
     */
    public static final class Version implements Comparable<Version> {
        public final int major;
        public final int minor;
        public final String version;

        public Version(final String name) {
            final int idx = name.indexOf('.');
            major = Integer.valueOf(name.substring(0, idx), 16);
            minor = Integer.valueOf(name.substring(idx + 1), 16);
            version = name;
        }

        @Override
        public int compareTo(final Version rhs) {
            if (this.major != rhs.major) {
                return this.major - rhs.major;
            } else {
                return this.minor - rhs.minor;
            }
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Version)) {
                return false;
            }

            final Version version = (Version) o;

            if (major != version.major) {
                return false;
            }
            if (minor != version.minor) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return 29 * major + minor;
        }

        @Override
        public String toString() {
            return version;
        }
    }
}
