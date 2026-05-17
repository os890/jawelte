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
package org.os890.jawelte.module.jpa.impl.adapter.connection;

import java.lang.annotation.Annotation;
import java.sql.Connection;
import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.persistence.EntityManager;

import org.hibernate.Session;
import org.os890.jawelte.module.jpa.api.port.PersistenceUnitConnectionResolver;
import org.os890.jawelte.module.jpa.impl.util.TransactionScopedEmHolder;

/**
 * Default {@link PersistenceUnitConnectionResolver} shipped by
 * jpa-module: returns the JDBC connection currently held by the
 * active {@link EntityManager} on the calling thread, obtained via
 * {@link EntityManager#unwrap(Class)} with
 * {@code Connection.class}.
 *
 * <p>The connection returned is the same one the active
 * {@code @Transactional} method writes through, so seed and verify
 * code observes the same uncommitted state.
 *
 * <p>{@code @Priority(Integer.MAX_VALUE)} so consumers can
 * substitute a JDBC-only impl (e.g. one that pulls a fresh
 * connection from a {@code DataSource} for cleanup) at a lower
 * priority via {@code META-INF/services}.
 */
@Priority(Integer.MAX_VALUE)
public class DefaultPersistenceUnitConnectionResolver implements PersistenceUnitConnectionResolver {

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public DefaultPersistenceUnitConnectionResolver() {
    }

    @Override
    public Connection connectionFor(String persistenceUnitName) {
        EntityManager entityManager = TransactionScopedEmHolder.peek(persistenceUnitName);
        if (entityManager == null) {
            // Under JTA, jta-module's strategy never populates the
            // holder (the JTA platform owns the EM <-> transaction
            // enlistment), so fall back to the CDI bean jpa-module
            // registered for the PU. Resolving through CDI lazily
            // creates / fetches the JTA-scoped EntityManager bean,
            // which is the one Hibernate already drives.
            entityManager = lookupCdiEntityManager(persistenceUnitName);
        }
        if (entityManager == null) {
            throw new IllegalStateException(
                    "No active EntityManager for persistence unit '" + persistenceUnitName
                            + "'. Was the call made outside a @Transactional or "
                            + "UserTransaction.begin() boundary?");
        }
        // JPA's spec-portable em.unwrap(Connection.class) is not
        // supported by Hibernate; route through Session.doReturningWork
        // which surfaces the active JDBC connection without enrolling
        // new work. The returned connection is the same one Hibernate
        // already drives, so seed / cleanup code shares the active
        // transaction.
        Session session = entityManager.unwrap(Session.class);
        return session.doReturningWork(connection -> connection);
    }

    private static EntityManager lookupCdiEntityManager(String persistenceUnitName) {
        try {
            BeanManager beanManager = CDI.current().getBeanManager();
            // Primary path — event-driven capture.
            //
            // 1. Look up the synthetic EM bean via CDI.select(...).get().
            //    That returns a CDI client proxy of the @TransactionScoped
            //    EM bean.
            // 2. Touch a harmless method on the proxy (isOpen()) to
            //    force the contextual instance to materialize. That
            //    runs jpa-module's produceWith lambda, which fires
            //    EntityManagerCreatedEvent.
            // 3. The @TransactionScoped JtaEntityManagerCapture
            //    observer consumes that event and stores the RAW
            //    EntityManager (not a proxy) under the PU name.
            // 4. Read the raw EM back from the capture bean.
            //
            // The raw EM is what the connection-resolver caller wants:
            // em.unwrap(Session.class) on a real SessionImpl returns
            // the real Session, side-stepping Weld's client-proxy
            // shortcut on unwrap-returns-this.
            EntityManager captured = lookupViaCapture(persistenceUnitName);
            if (captured != null) {
                return captured;
            }
            // Fallback path — direct contextual-instance lookup via
            // BeanManager.Context. Reached when the capture bean is
            // absent (older jpa-module-impl on the classpath, the
            // event got swallowed, etc.). Goes to the synthetic
            // bean's Context directly so we still avoid the proxy hop.
            return lookupViaContext(beanManager, persistenceUnitName);
        } catch (IllegalStateException notRunning) {
            // CDI container not started on this thread. Nothing to
            // fall back to.
            return null;
        }
    }

    private static EntityManager lookupViaCapture(String persistenceUnitName) {
        try {
            // Resolve the synthetic EM bean's proxy and force its
            // contextual instance to materialize. Multi-PU beans are
            // qualified with @Named(puName); single-PU beans with
            // @Default. Try @Named first, fall back to @Default.
            Instance<EntityManager> emInstance =
                    CDI.current().select(EntityManager.class,
                            NamedLiteral.of(persistenceUnitName));
            if (emInstance.isUnsatisfied()) {
                emInstance = CDI.current().select(EntityManager.class);
            }
            if (emInstance.isUnsatisfied()) {
                return null;
            }
            // Trigger produceWith on the @TransactionScoped EM bean
            // by calling any method on its proxy. isOpen() is a
            // side-effect-free probe that always exists on
            // EntityManager. The call wakes the proxy, the proxy
            // creates the contextual instance via produceWith, and
            // jpa-module's lambda fires EntityManagerCreatedEvent.
            emInstance.get().isOpen();
            // Read the raw (non-proxied) EM the
            // JtaEntityManagerCapture observer just stored.
            Instance<JtaEntityManagerCapture> captureInstance =
                    CDI.current().select(JtaEntityManagerCapture.class);
            if (!captureInstance.isResolvable()) {
                return null;
            }
            return captureInstance.get()
                    .forPersistenceUnit(persistenceUnitName)
                    .orElse(null);
        } catch (RuntimeException unavailable) {
            // No active @TransactionScoped context (called outside a
            // JTA tx), capture bean not on the classpath, event
            // delivery failed, etc. — let the caller try the
            // fallback path.
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static EntityManager lookupViaContext(BeanManager beanManager, String persistenceUnitName) {
        // jpa-module's JpaCdiExtension registers EntityManager beans
        // qualified with @Named(puName) in the multi-PU case and
        // @Default in the single-PU case. Try @Named first; if it's
        // not satisfied (single PU), fall back to @Default.
        Bean<?> bean = beanManager.resolve(
                beanManager.getBeans(EntityManager.class, NamedLiteral.of(persistenceUnitName)));
        if (bean == null) {
            bean = beanManager.resolve(beanManager.getBeans(EntityManager.class));
        }
        if (bean == null) {
            return null;
        }
        Bean<EntityManager> emBean = (Bean<EntityManager>) bean;
        Class<? extends Annotation> scope = emBean.getScope();
        Context context = beanManager.getContext(scope);
        CreationalContext<EntityManager> creationalContext =
                beanManager.createCreationalContext(emBean);
        return context.get(emBean, creationalContext);
    }

    @Override
    public Connection connectionForActivePersistenceUnit() {
        Set<String> activeUnits = TransactionScopedEmHolder.currentFramePersistenceUnits();
        if (activeUnits.isEmpty()) {
            throw new IllegalStateException(
                    "No active persistence unit on the calling thread. "
                            + "Was the call made outside a @Transactional or "
                            + "UserTransaction.begin() boundary?");
        }
        if (activeUnits.size() > 1) {
            throw new IllegalStateException(
                    "Multiple active persistence units on the calling thread: "
                            + activeUnits + ". Use connectionFor(String) with an "
                            + "explicit persistence unit name.");
        }
        String onlyActiveUnit = activeUnits.iterator().next();
        return connectionFor(onlyActiveUnit);
    }
}
