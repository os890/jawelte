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
package org.os890.jawelte.module.testcontrol.impl.adapter.data;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Proxy-driven transaction template that {@link TestDataHandler}
 * uses to run a seed phase inside a managed transaction. The proxy
 * intercept on the {@link Transactional}-annotated method begins a
 * transaction through the active {@code TransactionStrategy}
 * (provided by jta-module &mdash; Geronimo, Narayana, or Atomikos
 * depending on the test profile) <em>before</em> the lambda runs,
 * pushes the {@link EntityManager} for the requested persistence
 * unit onto the active-PU stack so
 * {@code DbSeed.forPersistenceUnit()} can resolve a connection,
 * runs the lambda, and commits on normal return (or rolls back on
 * exception) when the {@code @Transactional} interceptor unwinds.
 *
 * <p>Each call to {@link #runInTransaction(String, Runnable)} is a
 * fresh, short-lived transaction independent of any transaction the
 * test method itself opens later via its own {@code @Transactional}
 * annotation &mdash; the seed data is durable and visible to other
 * threads (batch jobs, separate connections) by the time the test
 * method's <code>beforeEach</code> chain reaches jpa-module's
 * adapter.
 *
 * <p><b>Per-PU isolation.</b> {@link TestDataHandler} calls this
 * template once per phase (dbIn, dbUpdate) per distinct persistence
 * unit in the {@code @TestControl(testData=…)} array. Multi-PU
 * scenarios get one transaction per PU per phase; single-PU
 * scenarios just get two transactions total (one for dbIn, one for
 * dbUpdate).
 *
 * <p><b>EntityManager lookup.</b> Resolved via CDI inside the
 * {@code @Transactional} method so the lookup happens AFTER the
 * interceptor has begun the transaction (the context may be
 * {@code @TransactionScoped} under JTA mode). Single-PU CDI beans
 * are registered by jpa-module with the {@code @Default} qualifier;
 * multi-PU ones are registered with {@code @Named(persistenceUnitName)}.
 * The template selects the right one via {@link NamedLiteral} when
 * {@code puName} is non-null.
 */
@ApplicationScoped
public class TestDataSeedTransactionTemplate {

    /** No-arg constructor required by the CDI runtime. */
    public TestDataSeedTransactionTemplate() {
    }

    /**
     * Run {@code seedWork} inside a managed transaction targeting
     * the named persistence unit. The {@code @Transactional}
     * interceptor commits the transaction on normal return; an
     * exception thrown from {@code seedWork} causes a rollback.
     *
     * @param puName    the persistence-unit name from the entry's
     *                  {@code puName:} prefix, or {@code null} / empty
     *                  to target the default (single-active) PU
     * @param seedWork  the seed operations to execute inside the
     *                  transaction &mdash; typically one or more
     *                  {@code DbSeed.forPersistenceUnit(…).…execute()}
     *                  calls from {@link TestDataHandler}
     */
    @Transactional
    public void runInTransaction(String puName, Runnable seedWork) {
        EntityManager entityManager = lookupEntityManager(puName);
        // Force jpa-module's active-PU stack to populate for this PU
        // before DbSeed.forPersistenceUnit() resolves the connection.
        // Object methods (toString / equals / hashCode) are handled
        // locally by EntityManagerProxy and do NOT trigger
        // peekOrAutoBegin — so an Object-method call would not push
        // the EM, and the seed's connectionFor(puName) would then peek
        // an empty stack and throw. isOpen() is a real EntityManager
        // method, side-effect-free, and routes through the proxy's
        // peekOrAutoBegin path — which lazy-creates the EM for the
        // named PU, begins its EntityTransaction, and pushes onto the
        // per-thread stack. Matters most in multi-PU mode where the
        // strategy's "all-lazy" begin() leaves the frame empty until
        // a real EM method call lazy-joins each requested PU.
        entityManager.isOpen();
        seedWork.run();
    }

    private static EntityManager lookupEntityManager(String puName) {
        if (puName == null || puName.isEmpty()) {
            return CDI.current().select(EntityManager.class).get();
        }
        try {
            return CDI.current().select(EntityManager.class, NamedLiteral.of(puName)).get();
        } catch (UnsatisfiedResolutionException unsatisfied) {
            // CDI runtimes differ in whether they put the @Named value
            // in the exception message (Weld 5+ omits it, OWB includes
            // it). Re-throw with an explicit message that names the
            // offending PU so the user can grep their test output and
            // find the typo in @TestControl(testData="<puName>:…").
            throw new IllegalArgumentException(
                    "@TestControl(testData=\"" + puName + ":…\") references persistence unit '"
                            + puName + "' but no EntityManager CDI bean is registered with "
                            + "@Named(\"" + puName + "\"). Declare the persistence unit in "
                            + "META-INF/persistence.xml, or fix the typo in the puName: prefix.",
                    unsatisfied);
        }
    }
}
