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
package org.os890.jawelte.module.jta.impl.adapter.jndi;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * One-time-per-JVM setup of an in-process JNDI provider so the
 * {@code JndiArtifactBinder} can bind {@code UserTransaction} /
 * {@code TransactionManager} /
 * {@code TransactionSynchronizationRegistry} under standard names and
 * any consumer of those names (Narayana's CDI integration, Hibernate's
 * JNDI-aware code paths, application code that follows the Jakarta-EE
 * lookup convention) finds them.
 *
 * <p>The system properties {@code java.naming.factory.initial} and
 * {@code java.naming.factory.url.pkgs} are set once, idempotently.
 * jta-module never compile-depends on a JNDI provider; the provider
 * is supplied at runtime (the project's test classpath uses
 * {@code org.apache.xbean:xbean-naming}). Production deployments
 * either run in a Jakarta-EE container that ships JNDI already
 * (skip this bootstrap) or pull a naming provider themselves.
 *
 * <p>Idempotent: callers can invoke {@link #ensureInitialized()} any
 * number of times — the first call sets the properties, subsequent
 * calls verify they're still set to a working factory and otherwise
 * no-op.
 */
public abstract class JndiBootstrap {

    /**
     * The {@code InitialContextFactory} the project's test classpath
     * brings (Apache XBean's). Plays nicely with the {@code java:}
     * URL scheme via {@code java.naming.factory.url.pkgs}.
     */
    private static final String XBEAN_INITIAL_FACTORY =
            "org.apache.xbean.naming.global.GlobalContextManager";

    /**
     * Package prefix for the {@code java:} URL context factory shipped
     * by {@code xbean-naming}. Without this, lookups like
     * {@code java:/TransactionManager} fail with "no URL context".
     */
    private static final String XBEAN_URL_PKGS = "org.apache.xbean.naming";

    private static volatile boolean initialized;

    /** Suppress instantiation; the class is a static-method holder. */
    protected JndiBootstrap() {
    }

    /**
     * Idempotently ensure JNDI is usable in this JVM. First call sets
     * the standard {@code java.naming.*} system properties; subsequent
     * calls return without re-setting (assumes the factory installed
     * the first time is still in place).
     *
     * @return a {@link Context} ready for binds / lookups (or
     *         {@code null} when the JVM has no JNDI provider on the
     *         classpath — the bootstrap couldn't install xbean-naming
     *         either, callers must surface this as an error)
     */
    public static Context ensureInitialized() {
        if (!initialized) {
            synchronized (JndiBootstrap.class) {
                if (!initialized) {
                    System.setProperty(Context.INITIAL_CONTEXT_FACTORY, XBEAN_INITIAL_FACTORY);
                    System.setProperty(Context.URL_PKG_PREFIXES, XBEAN_URL_PKGS);
                    installXbeanGlobalContext();
                    initialized = true;
                }
            }
        }
        try {
            return new InitialContext();
        } catch (NamingException factoryAbsent) {
            // The system properties point at a factory class that isn't
            // on the classpath. Reset the initialized flag so a later
            // caller (e.g. after a different naming provider gets added
            // dynamically) gets another chance, and propagate as a
            // runtime exception — silent JNDI failure would surface
            // much later as obscure "tm not found" errors.
            initialized = false;
            throw new IllegalStateException(
                    "No JNDI InitialContextFactory on the classpath. jta-module's JndiArtifactBinder "
                            + "needs one (xbean-naming on the test classpath, or any other provider "
                            + "registered via java.naming.factory.initial).",
                    factoryAbsent);
        }
    }

    /**
     * Install a fresh {@code WritableContext} as xbean-naming's
     * global context, the writable root every {@code InitialContext}
     * binds / looks up against. Without it
     * {@code GlobalContextManager} hands out a sentinel that fails
     * every operation with "Global context has not been set".
     *
     * <p>Reflection-only so jta-module/impl doesn't compile-depend on
     * xbean-naming — the JNDI provider is a runtime concern: tests
     * bring xbean-naming, production deployments bring whichever
     * provider their container ships.
     */
    private static void installXbeanGlobalContext() {
        try {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            Class<?> globalContextManager = Class.forName(
                    "org.apache.xbean.naming.global.GlobalContextManager", true, tccl);
            Class<?> writableContext = Class.forName(
                    "org.apache.xbean.naming.context.WritableContext", true, tccl);
            Context root = (Context) writableContext.getDeclaredConstructor().newInstance();
            globalContextManager.getMethod("setGlobalContext", Context.class).invoke(null, root);
        } catch (ClassNotFoundException notXbean) {
            // Not running with xbean-naming. The InitialContextFactory
            // a consumer wires up themselves (or their container
            // provides) is responsible for its own root context.
        } catch (ReflectiveOperationException unexpected) {
            throw new IllegalStateException(
                    "Failed to install xbean-naming global context", unexpected);
        }
    }
}
