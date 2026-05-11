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

    /** Open a JTA tx, touch the tracker, commit. */
    @Transactional
    public void invokeOnceWithinTx() {
        tracker.touch();
    }

    /**
     * Open a JTA tx, set the tracker's per-instance mark, commit.
     *
     * @param mark the mark
     */
    @Transactional
    public void setMarkInTx(String mark) {
        tracker.setMark(mark);
    }

    /**
     * Open a JTA tx and read the tracker's mark.
     *
     * @return the mark
     */
    @Transactional
    public String readMarkInTx() {
        return tracker.getMark();
    }
}
