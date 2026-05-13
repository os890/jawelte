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

import java.sql.Connection;
import java.util.Set;

import jakarta.annotation.Priority;
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
