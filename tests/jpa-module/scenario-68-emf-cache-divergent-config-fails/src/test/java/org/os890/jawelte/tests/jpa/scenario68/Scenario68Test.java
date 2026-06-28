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
package org.os890.jawelte.tests.jpa.scenario68;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import java.util.Map;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.jpa.impl.util.EmfCache;

/**
 * The JVM-wide {@code EmfCache} is keyed by persistence-unit name and is
 * reused across test classes (the jpa-module performance win). Because the
 * key is the name alone, two test classes that declare the same PU name must
 * resolve to the same configuration — otherwise the second would silently run
 * against the first's {@code EntityManagerFactory} (and in-memory database).
 *
 * <p>This scenario drives {@link EmfCache#getOrCreate} directly (no CDI
 * container) with stub factories and asserts the fail-fast contract:
 * <ul>
 *   <li>same name + divergent config → {@link IllegalStateException};</li>
 *   <li>same name + identical config → cached factory reused, supplier not
 *       invoked;</li>
 *   <li>object-valued (per-bootstrap) entries are ignored in the comparison,
 *       so they never trigger a false divergence.</li>
 * </ul>
 */
public class Scenario68Test {

    private static final String PU = "scenario68-divergence-pu";

    private static final String URL_KEY = "jakarta.persistence.jdbc.url";

    private static final String BEAN_MANAGER_KEY = "jakarta.persistence.bean.manager";

    /** No-arg constructor. */
    public Scenario68Test() {
    }

    @AfterEach
    void evictSharedCacheEntry() {
        // EmfCache is JVM-static — drop this PU so methods stay independent.
        EmfCache.evict(PU);
    }

    @Test
    public void sameNameDivergentConfigFailsFast() {
        EntityManagerFactory first = mock(EntityManagerFactory.class);
        EntityManagerFactory cached = EmfCache.getOrCreate(
                PU, Map.of(URL_KEY, "jdbc:h2:mem:scenario68;DB_CLOSE_DELAY=-1"), () -> first);
        assertThat(cached).isSameAs(first);

        assertThatThrownBy(() -> EmfCache.getOrCreate(
                PU, Map.of(URL_KEY, "jdbc:h2:mem:scenario68-other;DB_CLOSE_DELAY=-1"),
                () -> mock(EntityManagerFactory.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PU)
                .hasMessageContaining(URL_KEY);
    }

    @Test
    public void sameNameIdenticalConfigReusesWithoutInvokingSupplier() {
        EntityManagerFactory first = mock(EntityManagerFactory.class);
        Map<String, Object> props = Map.of(URL_KEY, "jdbc:h2:mem:scenario68;DB_CLOSE_DELAY=-1");
        EmfCache.getOrCreate(PU, props, () -> first);

        EntityManagerFactory reused = EmfCache.getOrCreate(PU, Map.copyOf(props),
                () -> fail("supplier must not be invoked when the same config is already cached"));
        assertThat(reused).isSameAs(first);
    }

    @Test
    public void objectValuedEntriesAreIgnoredInTheComparison() {
        EntityManagerFactory first = mock(EntityManagerFactory.class);
        Map<String, Object> firstProps = Map.of(
                URL_KEY, "jdbc:h2:mem:scenario68;DB_CLOSE_DELAY=-1",
                BEAN_MANAGER_KEY, new Object());
        EmfCache.getOrCreate(PU, firstProps, () -> first);

        // Same string config but a different per-bootstrap object reference
        // must NOT count as a divergent configuration.
        Map<String, Object> secondProps = Map.of(
                URL_KEY, "jdbc:h2:mem:scenario68;DB_CLOSE_DELAY=-1",
                BEAN_MANAGER_KEY, new Object());
        EntityManagerFactory reused = EmfCache.getOrCreate(PU, secondProps,
                () -> fail("supplier must not run; only an object-valued entry changed"));
        assertThat(reused).isSameAs(first);
    }
}
