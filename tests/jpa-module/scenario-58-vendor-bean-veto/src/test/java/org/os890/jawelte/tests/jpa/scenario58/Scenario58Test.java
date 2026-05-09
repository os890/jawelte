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
package org.os890.jawelte.tests.jpa.scenario58;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import org.apache.geronimo.transaction.fake.FakeGeronimoBean;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

import com.arjuna.ats.jta.cdi.fake.FakeNarayanaBean;

/**
 * jpa-module's CDI Extension vetoes Narayana / Geronimo
 * transaction-related beans at {@code ProcessAnnotatedType} time
 * so they do not collide with the synthetic
 * {@code UserTransaction} / {@code TransactionStrategy}
 * registrations. The CDI container must therefore resolve the
 * stand-in beans as {@code Unsatisfied}, while a regular bean in a
 * non-vetoed package resolves normally.
 */
@EnableTestBeans
public class Scenario58Test {

    /** No-arg constructor for CDI. */
    public Scenario58Test() {
    }

    /** Vetoed packages produce no bean; regular packages still work. */
    @Test
    public void vendorVetoExcludesNarayanaAndGeronimoStandIns() {
        Instance<FakeNarayanaBean> narayana = CDI.current().select(FakeNarayanaBean.class);
        assertThat(narayana.isUnsatisfied())
                .as("com.arjuna.ats.jta.cdi.* beans must be vetoed by JpaCdiExtension")
                .isTrue();

        Instance<FakeGeronimoBean> geronimo = CDI.current().select(FakeGeronimoBean.class);
        assertThat(geronimo.isUnsatisfied())
                .as("org.apache.geronimo.transaction.* beans must be vetoed by JpaCdiExtension")
                .isTrue();

        Instance<RegularBean> regular = CDI.current().select(RegularBean.class);
        assertThat(regular.isResolvable())
                .as("non-vetoed packages must resolve normally — veto is targeted, not wholesale")
                .isTrue();
    }
}
