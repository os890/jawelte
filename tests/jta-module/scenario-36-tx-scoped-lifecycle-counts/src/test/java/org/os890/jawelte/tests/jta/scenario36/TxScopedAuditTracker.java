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
package org.os890.jawelte.tests.jta.scenario36;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.transaction.TransactionScoped;

/**
 * {@code @TransactionScoped} audit tracker — one instance per
 * {@code @Transactional} invocation. Static counters record
 * {@code @PostConstruct} and {@code @PreDestroy} fires so the test
 * can assert one complete lifecycle per JTA tx.
 */
@TransactionScoped
public class TxScopedAuditTracker implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Incremented on every {@code @PostConstruct}. */
    public static final AtomicInteger POST_CONSTRUCT_COUNT = new AtomicInteger();

    /** Incremented on every {@code @PreDestroy}. */
    public static final AtomicInteger PRE_DESTROY_COUNT = new AtomicInteger();

    private String mark;

    /** Default constructor required by CDI. */
    public TxScopedAuditTracker() {
    }

    /** Reset both counters to zero. */
    public static void reset() {
        POST_CONSTRUCT_COUNT.set(0);
        PRE_DESTROY_COUNT.set(0);
    }

    /** Force materialisation of the contextual instance. */
    public void touch() {
    }

    /**
     * Per-instance state — null on a freshly-constructed instance.
     *
     * @return the mark, or {@code null} when never set
     */
    public String getMark() {
        return mark;
    }

    /**
     * Set the per-instance mark.
     *
     * @param newMark the new mark
     */
    public void setMark(String newMark) {
        this.mark = newMark;
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
