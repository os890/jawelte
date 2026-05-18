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
package org.os890.jawelte.tests.quarkus.scenario32;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

@EnableTestBeans(manageContainer = false)
class Scenario32Test {

    private static SeContainer externalContainer;

    @Inject
    BeanManager beanManager;

    @BeforeAll
    static void bootExternalContainer() {
        externalContainer = SeContainerInitializer.newInstance().initialize();
    }

    @AfterAll
    static void closeExternalContainer() {
        if (externalContainer != null) {
            externalContainer.close();
        }
    }

    @Test
    void requestScopeIsActivatedAndTestClassFieldsArePopulatedWhenContainerIsExternal() {
        assertThat(beanManager).isNotNull();
        boolean active = beanManager.getContext(RequestScoped.class).isActive();
        assertThat(active).isTrue();
    }
}
