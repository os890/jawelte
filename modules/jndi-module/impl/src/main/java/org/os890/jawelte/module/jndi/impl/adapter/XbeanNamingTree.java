/*
 * Copyright 2026 os890
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.os890.jawelte.module.jndi.impl.adapter;

import javax.naming.Context;
import javax.naming.NamingException;

import org.apache.xbean.naming.context.WritableContext;
import org.apache.xbean.naming.global.GlobalContextManager;

/**
 * The xbean-naming installation, kept in a class of its own so that
 * loading it can be made conditional.
 *
 * <p>Every xbean type this module needs is referenced here and nowhere
 * else. That is the point of the class: on a JVM without xbean-naming
 * the resulting {@link NoClassDefFoundError} has exactly one place it
 * can originate, and {@link DefaultJndiContextProvider} turns it into
 * the port's documented {@code null}.
 *
 * <p><b>What installing means.</b> A {@link WritableContext} is set as
 * xbean's global context — without it {@link GlobalContextManager} hands
 * out a sentinel that fails every operation with "Global context has not
 * been set" — and the standard {@code java.naming.factory.initial} /
 * {@code java.naming.factory.url.pkgs} properties are pointed at xbean,
 * which is what makes a plain {@code new InitialContext()} and the
 * {@code java:} URL scheme work.
 *
 * <p><b>The root is built before the properties are written</b>, and
 * that order is load-bearing rather than incidental: constructing it is
 * the first touch of an xbean type, so on a classpath without
 * xbean-naming this method fails before it has claimed that xbean is the
 * JVM's naming provider. Setting the properties first would leave a JVM
 * whose only provider is its container's with
 * {@code java.naming.factory.initial} naming a class it cannot load —
 * which is what jta-module's old bootstrap did, and what
 * {@code tests/jndi-module} scenario 02 now asserts against.
 *
 * <p><b>Installed exactly once.</b> The second step <em>replaces</em> the
 * root, so running it twice would discard everything bound so far.
 * Routing every module through this one place is what makes a single
 * guard sufficient — two modules carrying their own copy of this logic
 * would each guard their own flag and the later one would wipe the
 * earlier one's bindings.
 */
abstract class XbeanNamingTree {

    /**
     * Package prefix for the {@code java:} URL context factory shipped by
     * xbean-naming. Without it, lookups like
     * {@code java:/TransactionManager} fail with "no URL context".
     */
    private static final String URL_PKGS = "org.apache.xbean.naming";

    private static boolean installed;

    /** Suppress instantiation; the class is a static-method holder. */
    protected XbeanNamingTree() {
    }

    /**
     * The shared writable root, installing the provider on first call.
     *
     * <p>{@code synchronized} on the method, with a plain {@code boolean}
     * flag: this runs a handful of times per JVM, so there is no
     * fast path worth double-checking and nothing that needs to be
     * {@code volatile} — every read and write of the flag happens under
     * this lock.
     *
     * @return the writable root context, never {@code null}
     * @throws IllegalStateException when xbean rejects the root context
     * @throws NoClassDefFoundError when xbean-naming is not on the
     *         classpath — {@link DefaultJndiContextProvider} translates
     *         this into the port's {@code null}
     */
    static synchronized Context writableRoot() {
        if (!installed) {
            install();
            installed = true;
        }
        return GlobalContextManager.getGlobalContext();
    }

    private static void install() {
        Context root = newRoot();
        System.setProperty(Context.INITIAL_CONTEXT_FACTORY, GlobalContextManager.class.getName());
        System.setProperty(Context.URL_PKG_PREFIXES, URL_PKGS);
        GlobalContextManager.setGlobalContext(root);
    }

    private static Context newRoot() {
        try {
            return new WritableContext();
        } catch (NamingException rootRejected) {
            throw new IllegalStateException(
                    "Failed to build the xbean-naming global context", rootRejected);
        }
    }
}
