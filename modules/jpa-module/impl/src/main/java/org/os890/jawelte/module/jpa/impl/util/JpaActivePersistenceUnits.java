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
package org.os890.jawelte.module.jpa.impl.util;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JVM-wide registry of the persistence units active for the
 * currently-executing test class. Set by
 * {@code JpaCdiExtension.beforeBeanDiscovery} (which reads
 * {@code @PersistenceConfig.persistenceUnits} and the discovered
 * {@code persistence.xml} contents) and read by the active
 * {@code TransactionStrategy} on
 * {@code begin()} / {@code commit()} / {@code rollback()}.
 *
 * <p>Holds <strong>names only</strong>, not
 * {@code EntityManagerFactory} instances — those live in
 * {@link EmfCache}, also keyed by name.
 *
 * <p>Per the parallel-safety constraint documented in the ticket
 * Summary, jpa-module collapses the test mode to one method in one
 * JVM at a time. The static state is therefore safe: at any given
 * point in time at most one test class drives it.
 *
 * <p>Insertion order is preserved (a {@link LinkedHashSet} backs
 * the registry) so consumers walking the active set see
 * persistence units in the order
 * {@link #set(Set)} received them; the
 * {@code JpaCdiExtension} preserves the order
 * {@code persistence.xml} documents declare them in.
 */
public abstract class JpaActivePersistenceUnits {

    private static final AtomicReference<Set<String>> CURRENT =
            new AtomicReference<>(Collections.unmodifiableSet(new LinkedHashSet<>()));

    /**
     * Suppressed-instantiation constructor. The class is
     * {@code abstract} so direct {@code new} is impossible; the
     * explicit declaration silences {@code javadoc -doclint:all} on
     * the otherwise synthesized default constructor.
     */
    protected JpaActivePersistenceUnits() {
    }

    /**
     * Replace the registry with the given persistence-unit name set.
     *
     * @param persistenceUnitNames the names to register; iterated in
     *                             order to seed the new
     *                             insertion-ordered set
     */
    public static void set(Set<String> persistenceUnitNames) {
        CURRENT.set(Collections.unmodifiableSet(new LinkedHashSet<>(persistenceUnitNames)));
    }

    /**
     * Read the active persistence-unit names. Empty when no test
     * class is currently bootstrapped (e.g. before the first
     * {@code beforeAll} or after the last {@code afterAll}).
     *
     * @return the active persistence-unit names; never {@code null}
     */
    public static Set<String> get() {
        return CURRENT.get();
    }

    /**
     * Reset the registry to an empty set. Called by
     * {@code JpaLifecycleAdapter.afterAll} so a stale set from one
     * test class does not leak into the next.
     */
    public static void reset() {
        CURRENT.set(Collections.unmodifiableSet(new LinkedHashSet<>()));
    }
}
