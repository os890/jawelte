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
package org.os890.jawelte.tests.ejb.scenario25;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * TICKET-007 scenario 25 — an additional mapper returns an EMPTY
 * annotation list to claim {@link ClaimedByEmptyList}. The chain
 * stops on the empty-list claim and the terminal mapper does not
 * run for that class. The bean is still discovered (the
 * {@code @Singleton} stereotype declaration in
 * {@code EjbAnnotationExtension} keeps the class bean-defining); the
 * scope is the EJB baseline {@code @ApplicationScoped} inherited
 * from the stereotype because no explicit scope was added by either
 * the additional or the terminal mapper.
 */
@EnableTestBeans
class Scenario25Test {

    @Inject
    BeanManager beanManager;

    @Test
    void emptyListClaimSuppressesTerminal() {
        // Sanity: terminal observed SOMETHING — there are always
        // a handful of beans CDI delivers to ProcessAnnotatedType.
        // ClaimedByEmptyList specifically must NOT be in that set.
        assertThat(TestScenarioRecordingTerminal.OBSERVED).doesNotContain(ClaimedByEmptyList.class);

        Bean<?> bean = beanManager.resolve(beanManager.getBeans(ClaimedByEmptyList.class));
        assertThat(bean).isNotNull();
    }
}
