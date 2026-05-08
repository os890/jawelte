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
package org.os890.jawelte.tests.jpa.scenario63;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.port.ConfigResolver;
import org.os890.jawelte.core.api.port.TestContext;

/**
 * A consumer-supplied {@link ConfigResolver} controls the
 * persistence-property prefix walk that drives Hibernate's
 * bootstrap: the test-only {@link InjectingConfigResolver}
 * registers at {@code @Priority(50)} via
 * {@code META-INF/services} and adds one synthetic key
 * (<code>…persistence-property.hibernate.format_sql</code>) to
 * {@link ConfigResolver#resolveKeys()} with a value of
 * <code>true</code> from {@link ConfigResolver#resolve(String)}.
 *
 * <p>Pre-§5.4, jpa-module's bootstrap walked
 * <code>ConfigProvider.getConfig().getPropertyNames()</code>
 * directly — bypassing whatever resolver the consumer plugged
 * in — and the synthetic property never reached the EMF. After
 * §5.4 the prefix walk goes through the resolver's
 * {@code resolveKeys()} + {@code resolve(...)}, so the override
 * surfaces on the EMF's properties map.
 *
 * <p>Two assertions:
 * <ol>
 *   <li>The active resolver is the {@code @Priority(50)}
 *       wrapper (sanity check that the SPI sort picked it).</li>
 *   <li>{@code emf.getProperties().get("hibernate.format_sql")}
 *       equals <code>"true"</code> — the synthetic property
 *       reached Hibernate's bootstrap exactly once and is
 *       observable on the resulting EMF.</li>
 * </ol>
 */
@EnableTestBeans
public class Scenario63Test {

    @Inject
    private EntityManagerFactory entityManagerFactory;

    /** No-arg constructor for CDI. */
    public Scenario63Test() {
    }

    /** ConfigResolver swap is picked up + the prefix walk goes through it. */
    @Test
    public void consumerConfigResolverControlsPersistencePropertyPrefixWalk() {
        ConfigResolver active = TestContext.loadService(ConfigResolver.class);
        assertThat(active)
                .as("InjectingConfigResolver at @Priority(50) must win the SPI sort over the "
                        + "default ConfigResolverAdapter — without that, the rest of this test "
                        + "isn't actually exercising the consumer-resolver path")
                .isInstanceOf(InjectingConfigResolver.class);

        Object formatSql = entityManagerFactory.getProperties().get("hibernate.format_sql");
        assertThat(formatSql)
                .as("the persistence-property prefix walk must route through "
                        + "ConfigResolver.resolveKeys() + resolve(...), so the synthetic "
                        + "'%s' = '%s' returned by InjectingConfigResolver reaches Hibernate's "
                        + "bootstrap properties. Pre-§5.4 the walk read MP Config directly "
                        + "and the override was silently lost.",
                        InjectingConfigResolver.INJECTED_KEY, InjectingConfigResolver.INJECTED_VALUE)
                .isEqualTo(InjectingConfigResolver.INJECTED_VALUE);
    }
}
