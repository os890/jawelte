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

/** Inner @Transactional layer; its own tx scope yields its own tracker. */
@ApplicationScoped
public class NestedInnerService {

    @Inject
    private NestedTxScopedTracker tracker;

    /** Default constructor required by CDI. */
    public NestedInnerService() {
    }

    /**
     * Open a nested tx, sample the tracker id from the inner scope,
     * commit.
     *
     * @return the inner-scope tracker id
     */
    @Transactional
    public UUID captureInnerTrackerId() {
        return tracker.getInstanceId();
    }
}
