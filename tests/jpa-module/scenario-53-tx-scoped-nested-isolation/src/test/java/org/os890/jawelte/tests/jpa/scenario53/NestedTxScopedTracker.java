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
package org.os890.jawelte.tests.jpa.scenario53;

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.transaction.TransactionScoped;

/**
 * Per-tx tracker — each contextual instance carries a unique
 * {@link UUID} so the test can compare identities across nested
 * boundaries.
 */
@TransactionScoped
public class NestedTxScopedTracker implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Number of {@code @PostConstruct} fires across the test. */
    public static final AtomicInteger POST_CONSTRUCT_COUNT = new AtomicInteger();

    /** Number of {@code @PreDestroy} fires across the test. */
    public static final AtomicInteger PRE_DESTROY_COUNT = new AtomicInteger();

    private final UUID instanceId = UUID.randomUUID();

    private String value;

    /** Reset both counters to zero. */
    public static void reset() {
        POST_CONSTRUCT_COUNT.set(0);
        PRE_DESTROY_COUNT.set(0);
    }

    /** Default constructor required by CDI. */
    public NestedTxScopedTracker() {
    }

    /**
     * Distinct id assigned at construction; identical id across two
     * proxy calls means the same contextual instance was reused.
     *
     * @return the per-instance id
     */
    public UUID getInstanceId() {
        return instanceId;
    }

    /**
     * Per-instance state used by the state-isolation test: outer
     * sets one value, inner sets another on its own instance, and
     * outer reads its value back to verify the inner write did not
     * leak across the tx-scope boundary.
     *
     * @return the value, or {@code null} when never set
     */
    public String getValue() {
        return value;
    }

    /**
     * Set the per-instance value.
     *
     * @param newValue the new value
     */
    public void setValue(String newValue) {
        this.value = newValue;
    }

    @PostConstruct
    void onCreate() {
        POST_CONSTRUCT_COUNT.incrementAndGet();
    }

    @PreDestroy
    void onDestroy() {
        PRE_DESTROY_COUNT.incrementAndGet();
    }
}
