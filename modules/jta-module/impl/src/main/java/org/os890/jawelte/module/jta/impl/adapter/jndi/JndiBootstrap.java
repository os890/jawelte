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

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jndi.api.port.JndiContextProvider;

/**
 * jta-module's view of the JNDI naming tree, so
 * {@code JndiArtifactBinder} can bind {@code UserTransaction} /
 * {@code TransactionManager} /
 * {@code TransactionSynchronizationRegistry} under standard names and
 * any consumer of those names (Narayana's CDI integration, Hibernate's
 * JNDI-aware code paths, application code that follows the Jakarta-EE
 * lookup convention) finds them.
 *
 * <p><b>The naming tree itself is not owned here.</b> Installing an
 * in-process provider installs a fresh writable root, so doing it in
 * more than one place would mean a later module discarding an earlier
 * module's bindings — jta-module's transaction artifacts and whatever a
 * second binding module publishes would take turns wiping each other
 * out depending on boot order. The install
 * therefore lives behind jndi-module's {@link JndiContextProvider} port,
 * resolved through {@link TestContext#loadService(Class)}, and this
 * class only adds jta-module's own semantics on top: a
 * {@link Context} to hand back and an error message that names JTA
 * when nothing is available.
 */
public abstract class JndiBootstrap {

    /** Suppress instantiation; the class is a static-method holder. */
    protected JndiBootstrap() {
    }

    /**
     * Idempotently ensure JNDI is usable in this JVM, then return an
     * {@link InitialContext} for it.
     *
     * @return a {@link Context} ready for binds / lookups
     * @throws IllegalStateException when no naming provider is
     *         available — for jta-module that is a hard error, since
     *         the vendor integrations resolve their artifacts by name
     */
    public static Context ensureInitialized() {
        writableRoot();
        try {
            return new InitialContext();
        } catch (NamingException factoryAbsent) {
            throw new IllegalStateException(
                    "No JNDI InitialContextFactory on the classpath. jta-module's JndiArtifactBinder "
                            + "needs one (xbean-naming on the test classpath, or any other provider "
                            + "registered via java.naming.factory.initial).",
                    factoryAbsent);
        }
    }

    /**
     * The shared writable root, installing the naming provider on
     * first use.
     *
     * @return the writable root context, or {@code null} when this JVM
     *         has no naming provider at all — the caller decides
     *         whether that is fatal
     */
    public static Context writableRoot() {
        JndiContextProvider provider = TestContext.loadService(JndiContextProvider.class);
        if (provider == null) {
            return null;
        }
        return provider.writableRoot();
    }
}
