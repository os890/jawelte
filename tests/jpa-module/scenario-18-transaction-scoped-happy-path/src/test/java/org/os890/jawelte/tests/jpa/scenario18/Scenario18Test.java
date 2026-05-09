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
package org.os890.jawelte.tests.jpa.scenario18;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * {@code @TransactionScoped} happy path: a bean dereferenced inside an active
 * {@code @Transactional} is created on first touch, retains its state across
 * subsequent touches in the same tx, and is destroyed on commit.
 */
@EnableTestBeans
public class Scenario18Test {

    @Inject
    private HappyPathService service;

    /** No-arg constructor for CDI. */
    public Scenario18Test() {
    }

    /** One @PostConstruct + one @PreDestroy per @Transactional invocation. */
    @Test
    public void txScopedBeanLifecycleAndStatePersistAcrossTwoTouches() {
        HappyPathTracker.reset();

        int touchCountAfterTwoCalls = service.touchTwiceInOneTx();

        assertThat(touchCountAfterTwoCalls)
                .as("the same @TransactionScoped instance must be returned for two "
                        + "lookups within the same tx — its per-instance counter must reflect both touches")
                .isEqualTo(2);
        assertThat(HappyPathTracker.POST_CONSTRUCT_COUNT)
                .as("@PostConstruct fires exactly once on first dereference inside the tx")
                .hasValue(1);
        assertThat(HappyPathTracker.PRE_DESTROY_COUNT)
                .as("@PreDestroy fires exactly once when the tx commits")
                .hasValue(1);
    }
}
