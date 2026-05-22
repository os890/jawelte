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
package example.contentdiff;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

class JsonWithValuesTest {

    @Test
    void elPlaceholderResolvesAgainstWithValuesBinding() {
        // The expected document carries ${currentUser}; .withValues(Map)
        // resolves it to "alice" before the diff runs.
        String actual = "{\"createdBy\":\"alice\",\"itemCount\":3}";
        String expected = "{\"createdBy\":\"${currentUser}\",\"itemCount\":${expectedItems}}";

        ContentDiff.forJson(actual)
                .withValues(Map.of(
                        "currentUser", "alice",
                        "expectedItems", 3))
                .expectedContent(expected)
                .assertEquals();
    }

    @Test
    void elPropertyNavigationWorksOnBoundBeans() {
        // The bound object is a Map; EL property navigation
        // (${order.id}, ${order.customer.name}) resolves dotted keys
        // through the standard EL property accessor.
        Map<String, Object> order = Map.of(
                "id", 42,
                "customer", Map.of("name", "Alice"));

        String actual   = "{\"orderId\":42,\"customer\":\"Alice\"}";
        String expected = "{\"orderId\":${order.id},\"customer\":\"${order.customer.name}\"}";

        ContentDiff.forJson(actual)
                .withValues(Map.of("order", order))
                .expectedContent(expected)
                .assertEquals();
    }
}
