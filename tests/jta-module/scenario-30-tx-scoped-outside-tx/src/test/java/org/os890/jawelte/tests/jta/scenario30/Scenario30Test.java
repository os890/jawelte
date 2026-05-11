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
package org.os890.jawelte.tests.jta.scenario30;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Port of jpa-module scenario 20 — dereferencing a
 * {@code @TransactionScoped} bean outside any
 * {@code @Transactional} or {@code UserTransaction.begin()} must
 * raise {@link ContextNotActiveException} under JTA the same way as
 * under RESOURCE_LOCAL.
 */
@EnableTestBeans
public class Scenario30Test {

    @Inject
    private OutsideTxTracker tracker;

    /** No-arg constructor for CDI. */
    public Scenario30Test() {
    }

    @Test
    public void dereferenceOutsideTxRaisesContextNotActiveException() {
        assertThatThrownBy(tracker::touch)
                .as("with no tx active on the calling thread, the proxy must surface ContextNotActiveException")
                .isInstanceOf(ContextNotActiveException.class);
    }
}
