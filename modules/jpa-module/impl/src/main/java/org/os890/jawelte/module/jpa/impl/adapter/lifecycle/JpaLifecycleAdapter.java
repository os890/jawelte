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

import java.lang.reflect.Method;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.persistence.EntityManagerFactory;
import jakarta.transaction.Transactional;

import org.opentest4j.TestAbortedException;
import org.os890.jawelte.core.api.event.AfterTestTransaction;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;
import org.os890.jawelte.module.jpa.api.JpaConfiguredPersistenceUnit;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;
import org.os890.jawelte.module.jpa.api.port.DbCleanupStrategy;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;
import org.os890.jawelte.module.jpa.impl.adapter.context.TransactionScopedContext;
import org.os890.jawelte.module.jpa.impl.util.EmfCache;
import org.os890.jawelte.module.jpa.impl.util.FileModeState;
import org.os890.jawelte.module.jpa.impl.util.JpaActivePersistenceUnits;
import org.os890.jawelte.module.jpa.impl.util.TestMethodTransactionMarker;
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
 * stay in {@link EmfCache} for the next test class — except for
 * file-mode runs, which evict their EMFs so the H2 file lock
 * releases and the next test class can take it.
 *
 * <p>{@code @PersistenceConfig(fileMode=true)} engages a "skip
 * subsequent methods" debug mode: the first {@code @Test} method
 * runs and writes into the H2 file; every subsequent
 * {@code @Test} is aborted via
 * {@link TestAbortedException} so the developer can inspect the
 * file with the data shape that produced the first method's
 * outcome. Per-method DB cleanup is also skipped in file mode.
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
        PersistenceConfig persistenceConfig = testContext.getTestClass().getAnnotation(PersistenceConfig.class);
        JpaConfiguredPersistenceUnit.set(
                persistenceConfig == null ? "" : persistenceConfig.persistenceUnitName());
        if (isFileMode(testContext)) {
            String filePath = resolveFileModePath(testContext);
            testContext.bindMetadata(FileModeState.class, new FileModeState(filePath));
        }
    }

    @Override
    public void beforeEach(TestContext testContext) {
        FileModeState fileModeState = testContext.getMetadata(FileModeState.class).orElse(null);
        if (fileModeState != null && fileModeState.isFirstMethodExecuted()) {
            throw new TestAbortedException(
                    "[jawelte] @PersistenceConfig(fileMode=true): skipping subsequent test methods so the H2 "
                            + "file state from the first method is preserved for inspection. "
                            + "DB file directory: " + fileModeState.getFilePath());
        }
        beginTransactionForTransactionalTestMethod(testContext);
    }

    @Override
    public void afterEach(TestContext testContext) {
        RuntimeException primary = null;

        primary = completeTransactionForTransactionalTestMethod(testContext, primary);

        try {
            TransactionStrategy strategy = TestContext.loadService(TransactionStrategy.class);
            if (strategy.isActive()) {
                strategy.rollback();
            }
        } catch (RuntimeException orphanFailure) {
            if (primary == null) {
                primary = orphanFailure;
            } else {
                primary.addSuppressed(orphanFailure);
            }
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

        FileModeState fileModeState = testContext.getMetadata(FileModeState.class).orElse(null);
        if (fileModeState == null) {
            try {
                runCleanup();
            } catch (RuntimeException cleanupFailure) {
                if (primary == null) {
                    primary = cleanupFailure;
                } else {
                    primary.addSuppressed(cleanupFailure);
                }
            }
        } else {
            fileModeState.markFirstMethodExecuted();
        }

        try {
            // Defense-in-depth: drain TransactionScopedEmHolder's per-thread stacks
            // so a stray test method that pushed but never popped (e.g. an
            // exception path that bypassed both completion paths AND the
            // orphan-rollback try block above) does not leak EntityManager
            // state into the next test method on the same thread. Mirrors POC's
            // afterEach drain (punch-list §2.2).
            TransactionScopedEmHolder.clearForCurrentThread();
        } catch (RuntimeException clearFailure) {
            if (primary == null) {
                primary = clearFailure;
            } else {
                primary.addSuppressed(clearFailure);
            }
        }

        if (primary != null) {
            throw primary;
        }
    }

    @Override
    public void afterAll(TestContext testContext) {
        FileModeState fileModeState = testContext.getMetadata(FileModeState.class).orElse(null);
        if (fileModeState != null) {
            for (String persistenceUnitName : JpaActivePersistenceUnits.get()) {
                EmfCache.evict(persistenceUnitName);
            }
            testContext.unbindMetadata(FileModeState.class);
        }
        TransactionScopedEmHolder.clearForCurrentThread();
        JpaActivePersistenceUnits.reset();
        JpaConfiguredPersistenceUnit.reset();
    }

    private static void beginTransactionForTransactionalTestMethod(TestContext testContext) {
        Method testMethod = testContext.getMetadata(Method.class).orElse(null);
        if (testMethod == null || !testMethod.isAnnotationPresent(Transactional.class)) {
            return;
        }
        TransactionStrategy strategy = TestContext.loadService(TransactionStrategy.class);
        TransactionScopedContext transactionScopedContext = TransactionScopedContext.current();
        strategy.begin();
        if (transactionScopedContext != null) {
            transactionScopedContext.activate();
        }
        testContext.bindMetadata(TestMethodTransactionMarker.class, TestMethodTransactionMarker.INSTANCE);
    }

    private static RuntimeException completeTransactionForTransactionalTestMethod(
            TestContext testContext, RuntimeException primaryIn) {
        if (testContext.getMetadata(TestMethodTransactionMarker.class).isEmpty()) {
            return primaryIn;
        }
        RuntimeException primary = primaryIn;
        try {
            TransactionStrategy strategy = TestContext.loadService(TransactionStrategy.class);
            if (strategy.isActive()) {
                Throwable executionException = testContext.getMetadata(Throwable.class).orElse(null);
                if (executionException != null) {
                    strategy.rollback();
                } else {
                    strategy.commit();
                }
            }
        } catch (RuntimeException completionFailure) {
            if (primary == null) {
                primary = completionFailure;
            } else {
                primary.addSuppressed(completionFailure);
            }
        } finally {
            try {
                TransactionScopedContext transactionScopedContext = TransactionScopedContext.current();
                if (transactionScopedContext != null) {
                    transactionScopedContext.deactivate();
                }
            } catch (RuntimeException deactivateFailure) {
                if (primary == null) {
                    primary = deactivateFailure;
                } else {
                    primary.addSuppressed(deactivateFailure);
                }
            }
            testContext.unbindMetadata(TestMethodTransactionMarker.class);
        }
        return primary;
    }

    private static String resolveFileModePath(TestContext testContext) {
        PersistenceConfig persistenceConfig = testContext.getTestClass().getAnnotation(PersistenceConfig.class);
        if (persistenceConfig == null) {
            return "";
        }
        if (!persistenceConfig.filePath().isEmpty()) {
            return persistenceConfig.filePath();
        }
        return System.getProperty("user.home") + "/" + testContext.getTestClass().getSimpleName() + "_db";
    }

    private static void fireAfterTestTransaction(TestContext testContext) {
        // committed = "the test method body completed normally". When the
        // current test method threw, the wrapping @Transactional rolls back
        // (and so do orphan UTs); committed is false. When no Throwable
        // is bound (test passed, or a non-JUnit driver never bound the
        // metadata), default to true — matches the pre-§5.1 behaviour
        // for the no-info path.
        boolean committed = testContext.getMetadata(Throwable.class).isEmpty();
        // Prefer the actual test method name; fall back to the test class
        // simple name when no Method metadata was bound (e.g. a non-JUnit
        // driver). The pre-§5.1 code passed the class name in both cases —
        // the field's contract said "test method name", so the fallback is
        // the regression-safe path, not the canonical one.
        String methodName = testContext.getMetadata(Method.class)
                .map(Method::getName)
                .orElseGet(() -> testContext.getTestClass().getSimpleName());
        testContext.getMetadata(SeContainer.class).ifPresent(seContainer ->
                seContainer.getBeanManager().getEvent().fire(
                        new AfterTestTransaction(committed, methodName)));
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
