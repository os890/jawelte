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
package org.os890.jawelte.tests.jta.scenario45;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import org.apache.geronimo.transaction.fake.FakeGeronimoBean;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

import com.arjuna.ats.jta.cdi.fake.FakeNarayanaBean;

/**
 * {@code JtaCdiExtension}'s vendor-veto observer keeps
 * {@code org.apache.geronimo.transaction.*} CDI beans out of the
 * test container — Geronimo doesn't ship a CDI integration jpa-module
 * delegates to, so any Geronimo-internal beans that sneak in
 * transitively must not collide with jpa-module's synthetic
 * {@code UserTransaction} / {@code TransactionStrategy} registrations.
 *
 * <p>{@code com.arjuna.ats.jta.cdi.*} is <strong>not</strong> vetoed:
 * when Narayana's CDI integration is on the classpath jpa-module
 * delegates {@code @Transactional} + {@code @TransactionScoped} to it
 * via the {@code CdiTransactionalSupportProvider} seam. Vetoing the
 * Narayana CDI package wholesale would defeat that delegation. This
 * scenario therefore asserts the Narayana stand-in resolves normally
 * — the veto is narrowly scoped, not a blanket "JTA vendor packages
 * are off-limits" rule.
 */
@EnableTestBeans
public class Scenario45Test {

    /** No-arg constructor for CDI. */
    public Scenario45Test() {
    }

    @Test
    public void geronimoIsVetoedNarayanaIsNotAndRegularBeansResolve() {
        Instance<FakeGeronimoBean> geronimo = CDI.current().select(FakeGeronimoBean.class);
        assertThat(geronimo.isUnsatisfied())
                .as("org.apache.geronimo.transaction.* beans must be vetoed by JtaCdiExtension")
                .isTrue();

        Instance<FakeNarayanaBean> narayana = CDI.current().select(FakeNarayanaBean.class);
        assertThat(narayana.isResolvable())
                .as("com.arjuna.ats.jta.cdi.* beans must NOT be blanket-vetoed — "
                        + "delegation to Narayana's CDI integration depends on those beans staying alive")
                .isTrue();

        Instance<RegularBean> regular = CDI.current().select(RegularBean.class);
        assertThat(regular.isResolvable())
                .as("non-vetoed packages must resolve normally — veto is targeted, not wholesale")
                .isTrue();
    }
}
