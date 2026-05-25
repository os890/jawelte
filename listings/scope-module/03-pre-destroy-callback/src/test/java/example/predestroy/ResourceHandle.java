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

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

import org.os890.jawelte.module.scope.api.TestMethodScoped;

/**
 * A @TestMethodScoped bean with a @PreDestroy method. Because the
 * scope is destroyed at the end of every afterEach, the destroy hook
 * fires once per test method.
 */
@TestMethodScoped
public class ResourceHandle {

    // Test-harness counter (not part of the bean's production responsibility).
    // A real ResourceHandle would not expose a static counter; we use one
    // here so the test method can observe @PreDestroy firing.
    public static final AtomicInteger DESTROY_COUNT = new AtomicInteger();

    private int accessCount;

    public void recordAccess() {
        accessCount++;
    }

    public int getAccessCount() {
        return accessCount;
    }

    @PreDestroy
    void close() {
        DESTROY_COUNT.incrementAndGet();
    }
}
