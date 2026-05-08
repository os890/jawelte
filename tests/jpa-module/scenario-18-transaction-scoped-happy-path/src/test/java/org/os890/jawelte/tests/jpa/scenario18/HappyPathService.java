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
package org.os890.jawelte.tests.jpa.scenario18;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** {@code @Transactional} service that dereferences the @TransactionScoped bean twice. */
@ApplicationScoped
public class HappyPathService {

    @Inject
    private HappyPathTracker tracker;

    /** No-arg constructor for CDI. */
    public HappyPathService() {
    }

    /**
     * Touch the @TransactionScoped tracker twice within one tx; both lookups
     * resolve to the same proxy and address the same contextual instance.
     *
     * @return the per-instance touch count after the second touch
     */
    @Transactional
    public int touchTwiceInOneTx() {
        tracker.touch();
        tracker.touch();
        return tracker.getTouchCount();
    }
}
