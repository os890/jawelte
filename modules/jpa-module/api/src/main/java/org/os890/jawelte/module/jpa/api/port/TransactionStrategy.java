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
package org.os890.jawelte.module.jpa.api.port;

import java.util.Map;

import jakarta.persistence.PersistenceUnitTransactionType;
import jakarta.persistence.RollbackException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;

/**
 * Pluggable transaction-management seam. The default impl shipped by
 * jpa-module/impl is RESOURCE_LOCAL
 * ({@code DefaultResourceLocalTransactionStrategy} at
 * {@code @Priority(Integer.MAX_VALUE)}); an alternative strategy
 * (e.g. JTA-backed) may be supplied by another module at a lower
 * {@code @Priority}.
 *
 * <p>Consumers obtain the active impl through
 * {@code TestContext.loadService(TransactionStrategy.class)} — the
 * project-wide single canonical entry point for prioritized SPI
 * lookups.
 *
 * <p>The strategy is purely a transaction-lifecycle facade: it knows
 * nothing about {@code @TransactionScoped} bean stores or
 * {@code EntityManager} bookkeeping. Those concerns live in the
 * interceptor and the per-thread EM stack respectively.
 */
public interface TransactionStrategy {

    /**
     * Initialise the strategy with the bag of properties the EMF was
     * bootstrapped with. Called exactly once before any other method
     * on the same instance. The argument is non-null but may be
     * empty.
     *
     * @param entityManagerFactoryProperties the EMF's bootstrap
     *                                       properties; never
     *                                       {@code null}
     * @throws IllegalStateException if invoked more than once on the
     *                               same instance
     */
    void initialize(Map<String, Object> entityManagerFactoryProperties);

    /**
     * Begin a new transaction on the calling thread. RESOURCE_LOCAL
     * strategies must not be called when a transaction is already
     * active on the same thread.
     *
     * @throws IllegalStateException if a transaction is already active
     *                               on the calling thread (RESOURCE_LOCAL)
     */
    void begin();

    /**
     * Commit the active transaction on the calling thread. The
     * rollback-only flag must not be set.
     *
     * @throws IllegalStateException if no transaction is active on
     *                               the calling thread
     * @throws RollbackException     if the rollback-only flag is set
     */
    void commit();

    /**
     * Roll back the active transaction on the calling thread.
     *
     * @throws IllegalStateException if no transaction is active on the
     *                               calling thread
     */
    void rollback();

    /**
     * Whether a transaction is currently active on the calling
     * thread.
     *
     * @return {@code true} if a transaction is active, {@code false}
     *         otherwise
     */
    boolean isActive();

    /**
     * Mark the active transaction as rollback-only. A subsequent
     * {@link #commit()} must throw {@code RollbackException}; a
     * {@link #rollback()} call still succeeds.
     *
     * @throws IllegalStateException if no transaction is active on the
     *                               calling thread
     */
    void setRollbackOnly();

    /**
     * Read the rollback-only flag of the active transaction.
     *
     * @return {@code true} if the rollback-only flag is set,
     *         {@code false} otherwise
     * @throws IllegalStateException if no transaction is active on the
     *                               calling thread
     */
    boolean getRollbackOnly();

    /**
     * The concrete {@link TransactionManager} the strategy is backed
     * by, or {@code null} for RESOURCE_LOCAL strategies that do not
     * delegate to a JTA TM.
     *
     * @return the JTA {@code TransactionManager} or {@code null}
     */
    TransactionManager getTransactionManager();

    /**
     * The {@link UserTransaction} {@code JpaCdiExtension} registers
     * as the synthetic CDI {@code UserTransaction} bean while this
     * strategy is active. RESOURCE_LOCAL strategies typically return
     * a delegating helper that drives this same {@code TransactionStrategy}
     * (so {@code @Inject UserTransaction} stays consistent with
     * {@code @Transactional}); JTA strategies return the JTA
     * implementation's standard {@code UserTransaction} so consumers
     * see the real Jakarta-EE shape.
     *
     * <p>Symmetric with {@link #getTransactionManager()}: each
     * strategy reports the public Jakarta {@code transaction-api}
     * handle that goes with it.
     *
     * @return the {@code UserTransaction} to expose; never {@code null}
     */
    UserTransaction userTransaction();

    /**
     * The transaction model the strategy implements:
     * {@link PersistenceUnitTransactionType#RESOURCE_LOCAL} or
     * {@link PersistenceUnitTransactionType#JTA}.
     *
     * @return the transaction type
     */
    PersistenceUnitTransactionType getTransactionType();

    /**
     * Release any resources held by the strategy. Idempotent; errors
     * are logged and not propagated.
     */
    void shutdown();
}
