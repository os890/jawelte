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
package org.os890.jawelte.module.jta.api.port;

import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;

/**
 * Pluggable JTA-implementation discovery. Each provider produces a
 * fully-initialized {@link TransactionManager} and the standard
 * {@link UserTransaction} that goes with it.
 *
 * <p>{@code jta-module/impl} ships default providers for Geronimo,
 * Narayana, and Atomikos. Consumers add their own providers via
 * {@code META-INF/services} + {@code jakarta.annotation.Priority};
 * lower priority wins per the project's
 * {@code ServicePriorityResolver} convention.
 *
 * <p>Consumers obtain the active provider through
 * {@code TestContext.loadService(TransactionManagerProvider.class)} —
 * the project-wide single canonical entry point for prioritized SPI
 * lookups.
 */
public interface TransactionManagerProvider {

    /**
     * Cheap, side-effect-free check: are this provider's JTA classes
     * actually loadable on the current classpath? Implementations
     * typically inspect {@link Class#forName(String)} on a marker
     * class. The {@code JtaTransactionStrategy} skips providers whose
     * classes are absent before attempting the more expensive
     * {@link #create()} call.
     *
     * @return {@code true} if the provider's JTA classes are loadable;
     *         {@code false} otherwise. Never throws.
     */
    boolean isAvailable();

    /**
     * Returns a fully initialised {@link TransactionManager}. Called
     * once per JVM by the strategy, which caches the result. Must be
     * called only after {@link #isAvailable()} returned {@code true}.
     *
     * @return a fully initialised {@code TransactionManager}
     * @throws RuntimeException if creation fails
     */
    TransactionManager create();

    /**
     * Returns the standard {@link UserTransaction} provided by this
     * JTA implementation. Used as the synthetic CDI
     * {@code UserTransaction} bean's source while the JTA strategy is
     * active.
     *
     * @return the JTA implementation's standard {@code UserTransaction}
     * @throws RuntimeException if the JTA implementation does not
     *                          expose one
     */
    UserTransaction userTransaction();

    /**
     * Returns the standard {@link TransactionSynchronizationRegistry}
     * provided by this JTA implementation. Bound into JNDI under
     * {@code java:/TransactionSynchronizationRegistry} (and the
     * {@code java:comp} equivalent) by jta-module's JNDI artifact
     * binder so vendor CDI integrations (Narayana, future Quarkus,
     * etc.) that resolve TSR through JNDI find it.
     *
     * @return the JTA implementation's standard
     *         {@code TransactionSynchronizationRegistry}
     * @throws RuntimeException if the JTA implementation does not
     *                          expose one
     */
    TransactionSynchronizationRegistry transactionSynchronizationRegistry();

    /**
     * Release any resources held by the provider. Idempotent; errors
     * are logged at {@code WARNING} and not propagated, so JVM
     * shutdown hooks and launcher-session listeners do not break on
     * residual cleanup failures.
     */
    void shutdown();

    /**
     * Human-readable provider name used for logging and diagnostics
     * (e.g. {@code "Geronimo"}, {@code "Narayana"}, {@code "Atomikos"}).
     *
     * @return a non-null human-readable name
     */
    String name();
}
