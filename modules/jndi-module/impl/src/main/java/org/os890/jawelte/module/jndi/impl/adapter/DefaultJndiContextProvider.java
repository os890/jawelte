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

import jakarta.annotation.Priority;

import org.os890.jawelte.module.jndi.api.port.JndiContextProvider;

/**
 * Default {@link JndiContextProvider} shipped by jndi-module/impl:
 * installs Apache XBean's naming provider once per JVM and returns its
 * writable global context.
 *
 * <p>xbean-naming is a {@code provided} dependency, so the installation
 * itself is written against xbean's own types in
 * {@link XbeanNamingTree} — see there for what "installing" means and
 * why it happens only once. {@code provided} is what makes that safe to
 * do: the dependency is not transitive, so nothing that takes
 * jndi-module/impl inherits a naming provider from it. The same shape
 * jpa-module uses for {@code xbean-finder-shaded}.
 *
 * <p><b>Why the probe exists at all.</b> This adapter cannot function
 * without xbean — it <em>is</em> the xbean adapter — so the probe is not
 * here for its own sake. It is here so that a consumer can express "this
 * classpath has no naming provider" by leaving xbean-naming out, which
 * is the cheapest possible way to construct that state. The alternative
 * expression — leaving jndi-module/impl itself out, so that
 * {@link org.os890.jawelte.core.api.port.TestContext#loadService(Class)}
 * finds no provider and answers {@code null} — is not available to a
 * consumer that pulls this module in transitively at runtime scope, which
 * every module binding into the tree does. Without the probe, each of
 * those consumers would need a dependency exclusion in every scenario
 * that exercises the degradation.
 *
 * <p>The probe is also what keeps the {@code java.naming.*} system
 * properties out of a JVM that cannot use them: they are set inside
 * {@link XbeanNamingTree}, which is only touched once the probe has
 * succeeded. jta-module's old bootstrap set them first and discovered
 * xbean's absence afterwards, leaving a JVM whose only naming provider
 * was its container's with {@code java.naming.factory.initial} naming a
 * class it could not load.
 *
 * <p>It is repeated per call rather than cached: the answer depends on
 * the thread's context classloader, and one {@code Class.forName}
 * against an already-loaded class costs nothing next to the JNDI
 * operation the caller is about to perform.
 *
 * <p>{@code @Priority(Integer.MAX_VALUE)} so a consumer can register a
 * provider for a different naming implementation at a lower priority via
 * {@code META-INF/services}.
 */
@Priority(Integer.MAX_VALUE)
public class DefaultJndiContextProvider implements JndiContextProvider {

    /**
     * XBean's {@code InitialContextFactory}, and the class this adapter
     * probes for. Named as a string rather than through
     * {@code GlobalContextManager.class} on purpose: resolving that
     * literal is exactly the load this probe exists to avoid.
     */
    private static final String GLOBAL_CONTEXT_MANAGER =
            "org.apache.xbean.naming.global.GlobalContextManager";

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public DefaultJndiContextProvider() {
    }

    @Override
    public Context writableRoot() {
        if (!namingProviderPresent()) {
            return null;
        }
        return XbeanNamingTree.writableRoot();
    }

    /**
     * Whether xbean-naming is on the classpath of the calling thread.
     *
     * @return {@code true} when the provider can be installed;
     *         {@code false} is the port's documented "no naming provider
     *         in this JVM" answer rather than an error
     */
    private static boolean namingProviderPresent() {
        try {
            Class.forName(GLOBAL_CONTEXT_MANAGER, false,
                    Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException noNamingProvider) {
            // Whoever supplies the InitialContextFactory instead (a
            // Jakarta-EE container, a consumer's own provider) owns its
            // own root context; there is nothing for this adapter to do.
            return false;
        }
    }
}
