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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * {@code @Transactional} service that resolves the
 * {@code @TransactionScoped} bean inside a JTA tx. The test reads
 * the bean's id through this service to drive a bean-store lookup
 * inside the active transaction.
 */
@ApplicationScoped
public class PerTxBeanReader {

    @Inject
    private PerTxBean perTxBean;

    /** No-arg constructor for CDI. */
    public PerTxBeanReader() {
    }

    /**
     * Return the bean's id inside a JTA tx. Two consecutive calls
     * return different ids (different tx → different bean instance).
     *
     * @return the {@code PerTxBean.id} for the current tx
     */
    @Transactional
    public String readIdInsideJtaTx() {
        // First dereference inside the @Transactional method —
        // the proxy forwards to TransactionScopedContext, which
        // creates the bean on first access in this tx and stores
        // it on the per-thread bean-store frame.
        return perTxBean.getId();
    }

    /**
     * Same as {@link #readIdInsideJtaTx()} but throws after reading
     * the id, to drive the rollback code path. The caller can then
     * confirm the bean's {@code @PreDestroy} fired even on rollback.
     */
    @Transactional
    public String readIdAndRollback() {
        String capturedId = perTxBean.getId();
        throw new RuntimeException("scenario 17 — intentional rollback driver: " + capturedId);
    }
}
