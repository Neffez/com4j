
package com4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used with {@link DISPID} to indicate that the Java method is
 * a property get operation.
 *
 * <p>
 * This annotation corresponds to {@code propget} IDL annotation,
 * and {@code DISPATCH_PROPERTYGET} constant.
 *
 * @author Kohsuke Kawaguchi
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PropGet {
}
