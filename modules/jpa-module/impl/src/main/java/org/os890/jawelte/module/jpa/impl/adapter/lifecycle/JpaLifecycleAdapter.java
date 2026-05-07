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
package org.os890.jawelte.module.jpa.impl.adapter.lifecycle;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.persistence.EntityManagerFactory;

import org.os890.jawelte.core.api.event.AfterTestTransaction;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;
import org.os890.jawelte.module.jpa.api.port.DbCleanupStrategy;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;
import org.os890.jawelte.module.jpa.impl.util.EmfCache;
import org.os890.jawelte.module.jpa.impl.util.JpaActivePersistenceUnits;
import org.os890.jawelte.module.jpa.impl.util.TransactionScopedEmHolder;

/**
 * {@link TestModuleLifecyclePort} adapter shipped by jpa-module.
 * {@code @Priority(200)} runs after scope-module's
 * {@code ScopeLifecycleAdapter} (priority {@code 100}) so test
 * scopes are ready when JPA's per-method work runs; {@code afterEach}
 * fires in LIFO order, so jpa-module's per-method cleanup runs
 * before scope-module's scope deactivation, leaving the database
 * populated when {@code @TestMethodScoped} {@code @PreDestroy}
 * methods run (per scenario 41).
 *
 * <p>{@code beforeAll} / {@code beforeEach} are no-ops — the JPA
 * bootstrap is owned by {@code JpaCdiExtension.beforeBeanDiscovery}
 * (via the CDI runtime) and per-method transactions are owned by
 * {@code TransactionalInterceptor}.
 *
 * <p>{@code afterEach} runs in this order:
 *
 * <ol>
 *   <li>Orphan-rollback safety net — if the active
 *       {@link TransactionStrategy} reports {@code isActive() == true}
 *       (a stray {@code UserTransaction.begin()} without a matching
 *       commit), call {@code rollback()}.</li>
 *   <li>Fire {@link AfterTestTransaction} via
 *       {@link jakarta.enterprise.inject.spi.BeanManager#getEvent()}.</li>
 *   <li>If {@code @PersistenceConfig.fileMode == false}, resolve the
 *       active {@link DbCleanupStrategy} via
 *       {@link TestContext#loadService(Class)} and call
 *       {@code cleanAllTables(puName, emf)} for every active
 *       persistence unit.</li>
 * </ol>
 *
 * <p>{@code afterAll} clears the per-thread EM stack as a safety
 * net and resets {@link JpaActivePersistenceUnits}; cached EMFs
 * stay in {@link EmfCache} for the next test class.
 *
 * <p>Stateless across test classes — no instance fields. The same
 * adapter instance is reused for every test class running in the
 * JVM (per the project rule "all per-test-class state on
 * TestContext or framework-managed registries").
 */
@Priority(200)
public class JpaLifecycleAdapter implements TestModuleLifecyclePort {

    /** No-arg constructor used by {@code ServiceLoader}. */
    public JpaLifecycleAdapter() {
    }

    @Override
    public void beforeAll(TestContext testContext) {
        // No-op: EMFs are pre-warmed by JpaCdiExtension.beforeBeanDiscovery
        // during phase 1 of the bootstrap.
    }

    @Override
    public void beforeEach(TestContext testContext) {
        // No-op: per-method transactions are driven by
        // TransactionalInterceptor inside the test code.
    }

    @Override
    public void afterEach(TestContext testContext) {
        RuntimeException primary = null;

        try {
            TransactionStrategy strategy = TestContext.loadService(TransactionStrategy.class);
            if (strategy.isActive()) {
                strategy.rollback();
            }
        } catch (RuntimeException orphanFailure) {
            primary = orphanFailure;
        }

        try {
            fireAfterTestTransaction(testContext);
        } catch (RuntimeException eventFailure) {
            if (primary == null) {
                primary = eventFailure;
            } else {
                primary.addSuppressed(eventFailure);
            }
        }

        boolean fileMode = isFileMode(testContext);
        if (!fileMode) {
            try {
                runCleanup();
            } catch (RuntimeException cleanupFailure) {
                if (primary == null) {
                    primary = cleanupFailure;
                } else {
                    primary.addSuppressed(cleanupFailure);
                }
            }
        }

        if (primary != null) {
            throw primary;
        }
    }

    @Override
    public void afterAll(TestContext testContext) {
        TransactionScopedEmHolder.clearForCurrentThread();
        JpaActivePersistenceUnits.reset();
    }

    private static void fireAfterTestTransaction(TestContext testContext) {
        testContext.getMetadata(SeContainer.class).ifPresent(seContainer ->
                seContainer.getBeanManager().getEvent().fire(
                        new AfterTestTransaction(true, testContext.getTestClass().getSimpleName())));
    }

    private static boolean isFileMode(TestContext testContext) {
        PersistenceConfig persistenceConfig = testContext.getTestClass().getAnnotation(PersistenceConfig.class);
        return persistenceConfig != null && persistenceConfig.fileMode();
    }

    private static void runCleanup() {
        DbCleanupStrategy cleanup = TestContext.loadService(DbCleanupStrategy.class);
        if (cleanup == null) {
            return;
        }
        RuntimeException primary = null;
        for (String persistenceUnitName : JpaActivePersistenceUnits.get()) {
            EntityManagerFactory factory = EmfCache.getCached(persistenceUnitName).orElse(null);
            if (factory == null) {
                continue;
            }
            try {
                cleanup.cleanAllTables(persistenceUnitName, factory);
            } catch (RuntimeException perPuFailure) {
                if (primary == null) {
                    primary = perPuFailure;
                } else {
                    primary.addSuppressed(perPuFailure);
                }
            }
        }
        if (primary != null) {
            throw primary;
        }
    }
}
