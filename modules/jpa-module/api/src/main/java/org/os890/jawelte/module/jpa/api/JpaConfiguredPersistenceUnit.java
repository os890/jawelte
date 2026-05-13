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
package org.os890.jawelte.module.jpa.api;

import java.util.concurrent.atomic.AtomicReference;

/**
 * JVM-wide accessor for the
 * {@link PersistenceConfig#persistenceUnitName()} value of the
 * currently-executing test class. The jpa-module lifecycle reads
 * the annotation during {@code beforeAll} (it owns the
 * {@link org.os890.jawelte.core.api.port.TestContext} reference at
 * that point) and stores the resulting string here so that consumers
 * with no {@code TestContext} parameter can still observe the
 * configured name later, inside {@code @Test} methods.
 *
 * <p>db-testdata-module's {@code DbSeed.forPersistenceUnit()} /
 * {@code DbDiff.forPersistenceUnit()} read this value to decide
 * whether to route to a named persistence unit or to delegate to the
 * thread-active resolution. The string is empty when no annotation
 * is present, when {@code persistenceUnitName} is its default empty
 * value, or before {@code beforeAll} fires.
 *
 * <p>Per jpa-module's parallel-safety constraint (at most one test
 * class drives the JVM at a time), the static state here is safe.
 * The {@code AtomicReference} guards visibility only; concurrent
 * test classes are not in scope.
 */
public abstract class JpaConfiguredPersistenceUnit {

    private static final AtomicReference<String> CURRENT = new AtomicReference<>("");

    /**
     * Suppressed-instantiation constructor — the class is
     * {@code abstract} so direct {@code new} is impossible.
     */
    protected JpaConfiguredPersistenceUnit() {
    }

    /**
     * Replace the registered name with the supplied value;
     * {@code null} is normalised to the empty string so
     * {@link #get()} never returns {@code null}.
     *
     * @param name the value of {@link PersistenceConfig#persistenceUnitName()}
     *             on the test class, or {@code null} when the
     *             annotation is absent
     */
    public static void set(String name) {
        CURRENT.set(name == null ? "" : name);
    }

    /**
     * Read the registered name. Empty when no test class is
     * currently bootstrapped, when the test class has no
     * {@link PersistenceConfig} annotation, or when its
     * {@code persistenceUnitName} is the default empty value.
     *
     * @return the configured name; never {@code null}
     */
    public static String get() {
        return CURRENT.get();
    }

    /**
     * Reset the registry to the empty string. Called by jpa-module's
     * lifecycle on {@code afterAll} so a stale value from one test
     * class does not leak into the next.
     */
    public static void reset() {
        CURRENT.set("");
    }
}
