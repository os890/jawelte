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
package org.os890.jawelte.tests.ejb.scenario28;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * TICKET-007 scenario 28 — user-declared
 * {@code @jakarta.transaction.Transactional} attributes survive
 * unchanged. The default mapper skips the implicit
 * {@code TxType.REQUIRED} addition when the class already carries
 * a {@code @Transactional}; only the user's
 * {@code @Transactional(REQUIRES_NEW)} reaches the resolved
 * AnnotatedType.
 */
@EnableTestBeans
class Scenario28Test {

    @Inject
    BeanManager beanManager;

    @Test
    void userDeclaredTransactionalAttributesAreNotOverridden() {
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(RequiresNewSingleton.class));
        assertThat(bean).isNotNull();

        // The AnnotatedType the runtime resolved must contain exactly
        // ONE @Transactional with REQUIRES_NEW — adding the default
        // REQUIRED literal on top would either produce two distinct
        // bindings or silently overwrite the user's TxType.
        List<Transactional> transactionals = beanManager
                .createAnnotatedType(RequiresNewSingleton.class)
                .getAnnotations()
                .stream()
                .filter(annotation -> annotation instanceof Transactional)
                .map(annotation -> (Transactional) annotation)
                .toList();

        assertThat(transactionals)
                .as("ejb-module must not add a second @Transactional on a class that already declares one")
                .hasSize(1);
        assertThat(transactionals.get(0).value())
                .as("user-declared TxType must survive")
                .isEqualTo(Transactional.TxType.REQUIRES_NEW);
    }
}
