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
package org.os890.jawelte.tests.jpa.scenario65;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Cross-bean {@code @Transactional} propagation when the entry-point
 * bean has no {@code EntityManager} of its own.
 *
 * <p>{@link OuterService} carries the {@code @Transactional} but does
 * not inject an {@code EntityManager}; {@link InnerService} (no
 * {@code @Transactional}) does. The test method below is plain JUnit
 * — explicitly NOT {@code @Transactional} — so the only transaction
 * boundary is {@code OuterService.persistViaInner}'s. The persist
 * inside {@code InnerService.persistMarker} must therefore land
 * because the strategy's {@code begin(OuterService.class)} eagerly
 * opens the single PU (single-PU shortcut), the
 * {@code TransactionScopedContext} activates, and InnerService's
 * EM proxy resolves to the same active EM via the per-thread stack.
 *
 * <p>The verification call goes through
 * {@code OuterService.countViaInner} which is itself
 * {@code @Transactional}, so we never need a tx on the test method.
 */
@EnableTestBeans
public class Scenario65Test {

    @Inject
    private OuterService outerService;

    /** No-arg constructor for CDI. */
    public Scenario65Test() {
    }

    /**
     * The test method has no {@code @Transactional}; the persist
     * inside {@code InnerService} only succeeds because the
     * {@code @Transactional} on {@code OuterService.persistViaInner}
     * propagates the active scope into the inner bean's EM.
     */
    @Test
    public void outerBeanTransactionalPropagatesToInnerBeanEntityManagerUsage() {
        outerService.persistViaInner();

        long count = outerService.countViaInner();
        assertThat(count)
                .as("the persist inside InnerService.persistMarker (no @Transactional) must "
                        + "land because OuterService.persistViaInner's @Transactional propagated "
                        + "the active scope; the inner bean's @Inject EntityManager proxy resolved "
                        + "to the same per-thread EM")
                .isEqualTo(1L);
    }
}
