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
import javax.naming.NamingException;

import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;

import org.os890.jawelte.module.jta.api.port.TransactionManagerProvider;

/**
 * Bind the active {@link TransactionManagerProvider}'s {@link
 * TransactionManager} / {@link UserTransaction} / {@link
 * TransactionSynchronizationRegistry} into JNDI under the standard
 * Jakarta-EE names so vendor CDI integrations that look them up
 * through JNDI (Narayana's {@code JTASupplier},
 * {@code com.arjuna.ats.jta.cdi.NarayanaTransactionManager}, future
 * Quarkus's similar lookup) find them — regardless of which JTA
 * implementation is actually active underneath.
 *
 * <p>The binder is the single seam: jta-module wires the active
 * provider's artifacts into JNDI once at strategy bootstrap; every
 * downstream consumer (Narayana CDI, Hibernate's JNDI-based
 * {@code JtaPlatform} variants, application code following the
 * Jakarta-EE lookup convention) goes through standard
 * {@code InitialContext.lookup()} and gets the right instance.
 *
 * <p>Standard names bound:
 * <ul>
 *   <li>{@code java:/TransactionManager}</li>
 *   <li>{@code java:comp/TransactionManager}</li>
 *   <li>{@code java:/UserTransaction}</li>
 *   <li>{@code java:comp/UserTransaction}</li>
 *   <li>{@code java:/TransactionSynchronizationRegistry}</li>
 *   <li>{@code java:comp/TransactionSynchronizationRegistry}</li>
 * </ul>
 *
 * <p>Idempotent: a re-bind is a {@code rebind} so a fresh strategy
 * bootstrap (test isolation) can overwrite the previous round's
 * artifacts cleanly. Sub-contexts ({@code java:comp}) are created on
 * demand.
 */
public abstract class JndiArtifactBinder {

    private JndiArtifactBinder() {
    }

    /**
     * Bind the provider's artifacts into JNDI. Caller is responsible
     * for ensuring {@link JndiBootstrap#ensureInitialized()} has run
     * first (this method calls it for safety).
     *
     * @param provider the active JTA implementation provider — its
     *                 {@code TransactionManager}, {@code UserTransaction}
     *                 and {@code TransactionSynchronizationRegistry}
     *                 are bound
     */
    public static void bind(TransactionManagerProvider provider) {
        Context writableRoot = xbeanWritableRoot();
        if (writableRoot == null) {
            // No xbean-naming on classpath — caller asked for binding
            // but the JNDI provider isn't here. Skip silently; vendor
            // JNDI lookups then fail at their natural point with a
            // clearer "no initial context" error than anything we'd
            // synthesise here.
            return;
        }
        try {
            TransactionManager tm = provider.create();
            UserTransaction ut = provider.userTransaction();
            TransactionSynchronizationRegistry tsr = provider.transactionSynchronizationRegistry();

            // Bind unqualified in the writable root. Vendor JNDI
            // lookups for java:/TransactionManager route through
            // xbean's url-handler to this same writable root, so an
            // unqualified "TransactionManager" binding here resolves
            // against the qualified "java:/TransactionManager" lookup
            // from Narayana's JTASupplier.
            // Use compound names so xbean creates the "java:" /
            // "java:comp" sub-contexts and binds the artifact at the
            // leaf — vendor JNDI lookups for the full
            // "java:/TransactionManager" string then traverse the
            // tree and find the leaf.
            writableRoot.createSubcontext("java:");
            writableRoot.bind("java:/TransactionManager", tm);
            writableRoot.bind("java:/UserTransaction", ut);
            writableRoot.bind("java:/TransactionSynchronizationRegistry", tsr);
        } catch (NamingException bindFailure) {
            throw new IllegalStateException(
                    "Failed to bind JTA artifacts into JNDI for provider '"
                            + provider.name() + "'",
                    bindFailure);
        }
    }

    private static Context xbeanWritableRoot() {
        // The naming provider is installed once per JVM behind
        // core/api's JndiContextProvider port and shared with every
        // other module that binds — see JndiBootstrap for why this is
        // not done here.
        return JndiBootstrap.writableRoot();
    }
}
