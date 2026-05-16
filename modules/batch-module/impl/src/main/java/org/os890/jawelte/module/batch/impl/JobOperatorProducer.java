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
package org.os890.jawelte.module.batch.impl;

import jakarta.batch.operations.JobOperator;
import jakarta.batch.runtime.BatchRuntime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * The api↔library bridge for {@link JobOperator}. Resolves the
 * upstream {@code JobOperator} via the standard Jakarta Batch entry
 * point {@link BatchRuntime#getJobOperator()} and exposes it as an
 * {@link ApplicationScoped @ApplicationScoped} CDI bean — cached for
 * the container's lifetime (observably once per test class under
 * cdi-module's per-class container).
 *
 * <p>{@code BatchRuntime.getJobOperator()} performs a
 * {@link java.util.ServiceLoader} lookup under the thread context
 * classloader. In JUnit Jupiter the test thread's TCCL is the test
 * classpath, so no custom classloader bridge is needed — a
 * spec-compliant jBatch implementation on the test classpath is
 * found automatically.
 *
 * <p><b>No-implementation error.</b> When no jBatch implementation
 * is on the classpath, {@link BatchRuntime#getJobOperator()} either
 * returns {@code null} or throws an implementation-specific
 * exception. Either way this producer wraps the failure into an
 * {@link IllegalStateException} with a descriptive message so the
 * test author gets a clear classpath hint instead of an opaque
 * stack frame from the spec entry point.
 */
@ApplicationScoped
public class JobOperatorProducer {

    /** No-arg constructor required by the CDI runtime. */
    public JobOperatorProducer() {
    }

    /**
     * Produce the singleton {@link JobOperator}. Looked up exactly
     * once per CDI container via
     * {@link BatchRuntime#getJobOperator()}; the result is cached
     * by CDI's {@code @ApplicationScoped} scope for every
     * subsequent {@code @Inject JobOperator} site.
     *
     * @return the upstream {@link JobOperator}; never null
     * @throws IllegalStateException if no jBatch implementation is
     *         on the test classpath
     */
    @Produces
    @ApplicationScoped
    public JobOperator produceJobOperator() {
        JobOperator operator;
        try {
            operator = BatchRuntime.getJobOperator();
        } catch (RuntimeException re) {
            throw new IllegalStateException(
                    "No JobOperator found via ServiceLoader. Add a jBatch implementation to the test classpath.",
                    re);
        }
        if (operator == null) {
            throw new IllegalStateException(
                    "No JobOperator found via ServiceLoader. Add a jBatch implementation to the test classpath.");
        }
        return operator;
    }
}
