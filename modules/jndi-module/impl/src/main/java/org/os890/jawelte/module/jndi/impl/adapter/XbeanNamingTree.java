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

import java.util.Map;

import javax.naming.Context;
import javax.naming.NamingException;

import org.apache.xbean.naming.context.ContextAccess;
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

    /**
     * Whether {@link #install()} has already run.
     *
     * <p>{@code volatile} is deliberate belt-and-braces rather than a
     * requirement of the current code: every read and write of this flag
     * happens inside {@link #writableRoot()}, which is
     * {@code static synchronized}, and a monitor release
     * <em>happens-before</em> every subsequent acquire of that same
     * monitor — so a stale read is not possible as the code stands.
     *
     * <p>It is marked anyway because the guarantee is a property of
     * <em>where</em> the accesses are, not of the field: the moment
     * someone adds an unsynchronized fast path in front of the lock —
     * the double-checked-locking shape — {@code volatile} stops being
     * redundant and becomes load-bearing. Having it here means that edit
     * cannot introduce a data race by omission.
     */
    private static volatile boolean installed;

    /** Suppress instantiation; the class is a static-method holder. */
    protected XbeanNamingTree() {
    }

    /**
     * The shared writable root, installing the provider on first call.
     *
     * <p>{@code synchronized} on the method rather than a
     * double-checked lock around a smaller critical section: this runs a
     * handful of times per JVM, so there is no fast path worth the extra
     * shape. Both accesses to {@link #installed} are inside this method,
     * which is what makes the lock alone sufficient for visibility — see
     * that field for why it carries {@code volatile} regardless.
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

    /**
     * Build the root with reference dereferencing switched off, so the
     * tree hands back the object that was bound.
     *
     * <p>xbean's convenience constructors leave
     * {@code supportReferenceable} on, and with it {@code addBinding}
     * does not store what the caller bound: for a value that is
     * {@link javax.naming.Referenceable} and yields a non-null
     * {@link javax.naming.Reference}, the <em>Reference</em> is stored in
     * its place, and every later lookup reconstructs a fresh object from
     * it. Two lookups of one name then disagree, a lookup never returns
     * the bound instance, and anything holding state behind the
     * interface — a connection pool, canonically — gets one copy of that
     * state per lookup.
     *
     * <p>{@code supportReferenceable = false} is the flag that decides
     * this. With the substitution gone the remaining three have nothing
     * left to act on: reference caching, the dereference-difference check
     * and the dereference-bound assumption all describe handling of
     * reconstructed objects that no longer occur, so they are passed
     * {@code false} to say so rather than left to a default that implies
     * otherwise.
     *
     * @return a fresh, modifiable root context
     */
    private static Context newRoot() {
        try {
            return new WritableContext("", Map.of(), ContextAccess.MODIFIABLE,
                    false,  // cacheReferences
                    false,  // supportReferenceable
                    false,  // checkDereferenceDifferent
                    false); // assumeDereferenceBound
        } catch (NamingException rootRejected) {
            throw new IllegalStateException(
                    "Failed to build the xbean-naming global context", rootRejected);
        }
    }
}
