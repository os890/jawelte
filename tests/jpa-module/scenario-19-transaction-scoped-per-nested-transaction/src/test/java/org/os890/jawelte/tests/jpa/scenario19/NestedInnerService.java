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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Inner @Transactional service — its tx scope creates a fresh tracker. */
@ApplicationScoped
public class NestedInnerService {

    @Inject
    private NestedTracker tracker;

    /** No-arg constructor for CDI. */
    public NestedInnerService() {
    }

    /** Touch the tracker inside the inner tx scope. */
    @Transactional
    public void touchTrackerInInnerTx() {
        tracker.touch();
    }
}
