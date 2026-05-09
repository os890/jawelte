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
package org.os890.jawelte.tests.jpa.scenario52;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Application-scoped façade whose @Transactional method materialises the tracker. */
@ApplicationScoped
public class TxScopedAuditService {

    @Inject
    private TxScopedAuditTracker tracker;

    /** Default constructor required by CDI. */
    public TxScopedAuditService() {
    }

    /**
     * Open a tx, touch the tracker (forces a fresh
     * {@link TxScopedAuditTracker} instance for this tx), commit.
     */
    @Transactional
    public void invokeOnceWithinTx() {
        tracker.touch();
    }

    /**
     * Open a tx, set the tracker's per-instance mark, commit. The
     * tracker instance is destroyed on commit; a subsequent
     * {@code @Transactional} call gets a freshly-constructed tracker
     * whose mark is {@code null}.
     *
     * @param mark the mark to set on this tx's tracker instance
     */
    @Transactional
    public void setMarkInTx(String mark) {
        tracker.setMark(mark);
    }

    /**
     * Open a tx and read the tracker's mark. When this is the FIRST
     * dereference of the tracker in the current tx, CDI constructs a
     * fresh instance — its mark is {@code null}.
     *
     * @return the mark observed by the tx's freshly-constructed tracker
     */
    @Transactional
    public String readMarkInTx() {
        return tracker.getMark();
    }
}
