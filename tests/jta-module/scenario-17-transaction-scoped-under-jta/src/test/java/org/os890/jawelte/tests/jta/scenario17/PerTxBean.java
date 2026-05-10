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
package org.os890.jawelte.tests.jta.scenario17;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;
import jakarta.transaction.TransactionScoped;

/**
 * {@code @TransactionScoped} bean used by scenario 17 / 18 to prove
 * the bean-store lifecycle works under JTA the same way it does
 * under RESOURCE_LOCAL. Per-instance ids and {@code @PreDestroy}
 * counters let the test see each tx's bean as a distinct object and
 * verify that {@code @PreDestroy} fires when the JTA tx completes.
 */
@TransactionScoped
public class PerTxBean implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** Static so the test can read it across CDI proxy lookups. */
    public static final AtomicInteger PRE_DESTROY_COUNT = new AtomicInteger();

    private final String id = UUID.randomUUID().toString();

    /** No-arg constructor required by CDI for passivating-capable scopes. */
    public PerTxBean() {
    }

    /**
     * Return the bean's per-instance id. Two lookups inside the same
     * JTA tx return the same instance — same id; lookups in different
     * txs return different instances — different ids.
     *
     * @return the id
     */
    public String getId() {
        return id;
    }

    /** Bumps {@link #PRE_DESTROY_COUNT}. */
    @PreDestroy
    public void onPreDestroy() {
        PRE_DESTROY_COUNT.incrementAndGet();
    }
}
