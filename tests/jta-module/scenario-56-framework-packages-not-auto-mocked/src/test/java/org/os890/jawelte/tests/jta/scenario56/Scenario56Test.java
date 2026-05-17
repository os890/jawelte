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
package org.os890.jawelte.tests.jta.scenario56;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.cdi.impl.adapter.filter.DefaultExcludedPackageFilter;

/**
 * Reproduces the auto-mock leak observed when jta-module is on the
 * classpath but its framework packages are not in cdi-module's
 * auto-mock exclude list: an unsatisfied injection of an arjuna /
 * narayana / geronimo type ended up routed through
 * {@code MockitoMockFactory}, which fails with
 * {@code NoClassDefFoundError: org/mockito/Mockito} as soon as the
 * consumer leaves Mockito off its test classpath.
 *
 * <p>The contract this scenario locks in: jawelte's auto-mock layer
 * never registers a synthetic mock bean whose exposed types belong to
 * a framework package (here: {@code com.arjuna.*},
 * {@code org.jboss.narayana.*}, {@code org.apache.geronimo.*}). Mocks
 * are for user packages only.
 */
class Scenario56Test {

    @Test
    void narayanaInternalTypesAreExcludedFromAutoMock() {
        DefaultExcludedPackageFilter filter = new DefaultExcludedPackageFilter();
        assertThat(filter.isExcluded(com.arjuna.ats.jta.cdi.TransactionContext.class))
                .as("com.arjuna.* (narayana internals) must be in the "
                        + "auto-mock exclude list jta-module ships")
                .isTrue();
    }

    @Test
    void geronimoTransactionTypesAreExcludedFromAutoMock() {
        DefaultExcludedPackageFilter filter = new DefaultExcludedPackageFilter();
        assertThat(filter.isExcluded(
                org.apache.geronimo.transaction.manager.TransactionManagerImpl.class))
                .as("org.apache.geronimo.transaction.* must be in the "
                        + "auto-mock exclude list jta-module ships")
                .isTrue();
    }
}
