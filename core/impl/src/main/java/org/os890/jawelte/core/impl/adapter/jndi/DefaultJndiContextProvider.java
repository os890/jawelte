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
package org.os890.jawelte.core.impl.adapter.jndi;

import javax.naming.Context;

import jakarta.annotation.Priority;

import org.os890.jawelte.core.api.port.JndiContextProvider;

/**
 * Default {@link JndiContextProvider} shipped by core/impl: installs
 * Apache XBean's naming provider once per JVM and returns its writable
 * global context.
 *
 * <p>The installation is two steps. First the standard
 * {@code java.naming.factory.initial} /
 * {@code java.naming.factory.url.pkgs} system properties are pointed at
 * xbean, so a plain {@code new InitialContext()} and the {@code java:}
 * URL scheme both work. Then a {@code WritableContext} is installed as
 * xbean's global context — without it {@code GlobalContextManager}
 * hands out a sentinel that fails every operation with "Global context
 * has not been set".
 *
 * <p><b>Installed exactly once.</b> The second step replaces the root,
 * so running it twice would discard everything bound so far. The
 * {@code installed} flag guards it, and routing every module through
 * this one provider is what makes that guard sufficient — two modules
 * carrying their own copy of this logic would each guard their own flag
 * and the later one would wipe the earlier one's bindings.
 *
 * <p><b>Reflection-only.</b> core/impl does not compile-depend on
 * xbean-naming: the naming provider is a runtime concern. Tests bring
 * xbean-naming, a Jakarta-EE deployment brings its container's provider,
 * and a JVM with neither gets {@code null} — the documented "no naming
 * available" answer, not an exception.
 *
 * <p>{@code @Priority(Integer.MAX_VALUE)} so a consumer can register a
 * provider for a different naming implementation at a lower priority
 * via {@code META-INF/services}.
 */
@Priority(Integer.MAX_VALUE)
public class DefaultJndiContextProvider implements JndiContextProvider {

    /**
     * XBean's {@code InitialContextFactory}. Plays nicely with the
     * {@code java:} URL scheme via {@code java.naming.factory.url.pkgs}.
     */
    private static final String XBEAN_INITIAL_FACTORY =
            "org.apache.xbean.naming.global.GlobalContextManager";

    /**
     * Package prefix for the {@code java:} URL context factory shipped
     * by {@code xbean-naming}. Without this, lookups like
     * {@code java:/TransactionManager} fail with "no URL context".
     */
    private static final String XBEAN_URL_PKGS = "org.apache.xbean.naming";

    private static final Object INSTALL_LOCK = new Object();

    private static volatile boolean installed;

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public DefaultJndiContextProvider() {
    }

    @Override
    public Context writableRoot() {
        ensureInstalled();
        return globalContext();
    }

    private static void ensureInstalled() {
        if (installed) {
            return;
        }
        synchronized (INSTALL_LOCK) {
            if (installed) {
                return;
            }
            System.setProperty(Context.INITIAL_CONTEXT_FACTORY, XBEAN_INITIAL_FACTORY);
            System.setProperty(Context.URL_PKG_PREFIXES, XBEAN_URL_PKGS);
            installGlobalContext();
            installed = true;
        }
    }

    /**
     * Install a fresh {@code WritableContext} as xbean-naming's global
     * context — the writable root every {@code InitialContext} binds
     * and looks up against.
     *
     * <p>Only ever called from inside {@link #ensureInstalled()}'s
     * double-checked guard: it replaces the root, so a second call
     * would throw away every binding made against the first one.
     */
    private static void installGlobalContext() {
        try {
            Class<?> globalContextManager = loadXbeanClass(
                    "org.apache.xbean.naming.global.GlobalContextManager");
            Class<?> writableContext = loadXbeanClass(
                    "org.apache.xbean.naming.context.WritableContext");
            Context root = (Context) writableContext.getDeclaredConstructor().newInstance();
            globalContextManager.getMethod("setGlobalContext", Context.class).invoke(null, root);
        } catch (ClassNotFoundException noNamingProvider) {
            // No xbean-naming on the classpath. Whoever supplies the
            // InitialContextFactory instead (a Jakarta-EE container,
            // a consumer's own provider) owns its own root context.
            // writableRoot() reports the absence by returning null.
        } catch (ReflectiveOperationException unexpected) {
            throw new IllegalStateException(
                    "Failed to install the xbean-naming global context", unexpected);
        }
    }

    private static Context globalContext() {
        try {
            Class<?> globalContextManager = loadXbeanClass(
                    "org.apache.xbean.naming.global.GlobalContextManager");
            return (Context) globalContextManager.getMethod("getGlobalContext").invoke(null);
        } catch (ClassNotFoundException noNamingProvider) {
            return null;
        } catch (ReflectiveOperationException unexpected) {
            throw new IllegalStateException(
                    "Failed to access the xbean-naming global context", unexpected);
        }
    }

    private static Class<?> loadXbeanClass(String className) throws ClassNotFoundException {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        return Class.forName(className, true, tccl);
    }
}
