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
package org.os890.jawelte.module.dbtestdata.impl.adapter.persistence;

/**
 * Static slot for the persistence-unit name captured off the active
 * test class's {@code @PersistenceConfig}. Populated by
 * {@code DbTestDataArcContextContributor} during cdi-module's
 * {@code beforeAll}; read by {@link DefaultPersistenceUnitNameSupplier}
 * on demand.
 *
 * <p>The previous OWB / Weld path stored the value on the CDI
 * Extension instance and read it back through
 * {@code BeanManager.getExtension(...)}. ArC's {@code BeanManager}
 * implementation does not support {@code getExtension(...)} (it
 * throws {@code UnsupportedOperationException}), so we route through
 * a JVM-static slot the contributor sets and the supplier reads — the
 * same idiom scope-module uses for its
 * {@code TestScopeCurrentStores}. Container bootstrap is sequential
 * per test class on the same JVM (cdi-module's
 * {@code Arc.shutdown()} precedes every new {@code Arc.initialize}),
 * so the slot is guaranteed to hold the right value while ArC is up
 * and consumer beans dereference the supplier.
 */
public abstract class CapturedPersistenceUnitNameHolder {

    private static volatile String value = "";

    /** Suppressed-instantiation constructor — pure static holder. */
    protected CapturedPersistenceUnitNameHolder() {
    }

    /**
     * Publish the captured {@code @PersistenceConfig.persistenceUnitName}.
     *
     * @param captured the persistence-unit name; never {@code null}
     */
    public static void set(String captured) {
        value = captured == null ? "" : captured;
    }

    /**
     * The most recently captured persistence-unit name, or the empty
     * string when no {@code @PersistenceConfig} was present.
     *
     * @return the captured value; never {@code null}
     */
    public static String get() {
        return value;
    }
}
