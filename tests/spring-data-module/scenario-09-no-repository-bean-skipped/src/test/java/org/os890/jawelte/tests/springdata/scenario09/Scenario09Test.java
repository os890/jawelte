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
package org.os890.jawelte.tests.springdata.scenario09;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Scenario 9 — an interface annotated `@NoRepositoryBean` is skipped
 * by the extension. {@code BeanManager.getBeans(MarkerRepository.class)}
 * returns an empty set (no synthetic bean registered, the auto-mocker
 * is filtered out by the bundled `auto-mock.exclude-packages=org.springframework.data.`
 * default).
 */
@EnableTestBeans
public class Scenario09Test {

    @Inject
    private BeanManager beanManager;

    /** No-arg constructor for CDI. */
    public Scenario09Test() {
    }

    /** No bean is registered for the `@NoRepositoryBean`-marked interface. */
    @Test
    public void markerInterfaceProducesNoBean() {
        assertThat(beanManager.getBeans(MarkerRepository.class))
                .as("@NoRepositoryBean interfaces are skipped by the extension"
                        + " and excluded from auto-mocking (Spring Data package prefix)")
                .isEmpty();
    }
}
