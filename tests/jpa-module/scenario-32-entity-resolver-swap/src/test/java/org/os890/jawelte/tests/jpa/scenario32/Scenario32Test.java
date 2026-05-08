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
package org.os890.jawelte.tests.jpa.scenario32;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.EntityResolver;

/**
 * A test-only {@link CountingEntityResolver} at {@code @Priority(100)}
 * registered through {@code META-INF/services} wins the
 * {@code TestContext.loadService} priority sort over jpa-module's
 * default impl — locks in the swappability claim for the entity-resolution port.
 */
@EnableTestBeans
public class Scenario32Test {

    /** No-arg constructor for CDI. */
    public Scenario32Test() {
    }

    /** TestContext.loadService returns the @Priority(100) test-only impl. */
    @Test
    public void customEntityResolverWinsThePrioritySort() {
        EntityResolver active = TestContext.loadService(EntityResolver.class);

        assertThat(active)
                .as("a test-only EntityResolver at @Priority(100) must win over the "
                        + "addon's @Priority(MAX_VALUE) JpaMetamodelEntityResolver")
                .isInstanceOf(CountingEntityResolver.class);
    }
}
