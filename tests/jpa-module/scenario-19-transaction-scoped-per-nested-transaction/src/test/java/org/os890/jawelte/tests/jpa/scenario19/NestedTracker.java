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
package org.os890.jawelte.tests.jpa.scenario19;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.transaction.TransactionScoped;

/** {@code @TransactionScoped} bean used to measure per-nesting-level lifecycles. */
@TransactionScoped
public class NestedTracker implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Incremented on every {@code @PostConstruct}. */
    public static final AtomicInteger POST_CONSTRUCT_COUNT = new AtomicInteger();

    /** Incremented on every {@code @PreDestroy}. */
    public static final AtomicInteger PRE_DESTROY_COUNT = new AtomicInteger();

    /** Reset both counters to zero. */
    public static void reset() {
        POST_CONSTRUCT_COUNT.set(0);
        PRE_DESTROY_COUNT.set(0);
    }

    /** No-arg constructor required by CDI. */
    public NestedTracker() {
    }

    @PostConstruct
    void onCreate() {
        POST_CONSTRUCT_COUNT.incrementAndGet();
    }

    @PreDestroy
    void onDestroy() {
        PRE_DESTROY_COUNT.incrementAndGet();
    }

    /** Force the contextual proxy to materialise its bean instance. */
    public void touch() {
    }
}
