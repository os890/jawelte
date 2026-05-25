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
package example.predestroy;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Each test method uses ResourceHandle once. The @PreDestroy on the
 * bean fires at the end of every afterEach, so by the time the second
 * method runs the counter reflects one destroy from the first method;
 * after the second method's afterEach the counter is 2.
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResourceHandleTest {

    @Inject
    ResourceHandle resourceHandle;

    @Test
    @Order(1)
    void firstMethodUsesTheHandle() {
        resourceHandle.recordAccess();
        // PreDestroy has NOT fired yet for the current method — it runs at end of afterEach.
        assertThat(ResourceHandle.DESTROY_COUNT.get()).isEqualTo(0);
    }

    @Test
    @Order(2)
    void secondMethodSeesPriorMethodsPreDestroy() {
        resourceHandle.recordAccess();
        // After method 1 returned, scope-module fired @PreDestroy on the
        // method-1 instance. Method 2 is now using a fresh instance.
        assertThat(ResourceHandle.DESTROY_COUNT.get()).isEqualTo(1);
    }
}
