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
package example.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

@EnableTestBeans
class LifecyclePortOrderTest {

    @Test
    void priorityAscendingOrder() {
        // beforeAll runs in @Priority-ascending order — early (10),
        // then late (20). afterAll runs in reverse (LIFO) — late
        // first, then early. The test method observes the beforeAll
        // half-log here; the afterAll half is recorded after this
        // test returns, so the test cannot assert it directly. The
        // beforeAll half on its own already pins the ordering rule.
        assertThat(LifecycleLog.EVENTS).startsWith("early.beforeAll", "late.beforeAll");
    }
}
