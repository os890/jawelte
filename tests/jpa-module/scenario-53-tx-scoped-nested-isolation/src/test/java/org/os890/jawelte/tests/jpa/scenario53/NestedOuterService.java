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

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Outer @Transactional layer; samples the tracker id around the nested call. */
@ApplicationScoped
public class NestedOuterService {

    @Inject
    private NestedTxScopedTracker tracker;

    @Inject
    private NestedInnerService innerService;

    /** Default constructor required by CDI. */
    public NestedOuterService() {
    }

    /**
     * Open the outer tx, sample the tracker id, invoke the nested
     * tx, sample the outer-scope tracker id again, commit.
     *
     * @return the captured outer-before / inner / outer-after ids
     */
    @Transactional
    public NestedTxResult outerThenInner() {
        UUID outerBefore = tracker.getInstanceId();
        UUID innerId = innerService.captureInnerTrackerId();
        UUID outerAfter = tracker.getInstanceId();
        return new NestedTxResult(outerBefore, innerId, outerAfter);
    }

    /**
     * Set the outer-scope tracker's value, dispatch to inner (which
     * sets a different value on its own scope's tracker), then read
     * the outer value back. The inner write must not leak: the read
     * here returns the outer's original value, proving each tx-scope
     * frame owns an independent contextual instance.
     *
     * @param outerValue value set on the outer-scope tracker
     * @param innerValue value the inner tx sets on its own tracker
     * @return the outer-scope tracker's value AFTER the nested call
     */
    @Transactional
    public String outerSetsThenInnerSetsThenOuterReads(String outerValue, String innerValue) {
        tracker.setValue(outerValue);
        innerService.setInnerScopeValue(innerValue);
        return tracker.getValue();
    }
}
