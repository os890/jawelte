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
package org.os890.jawelte.module.cdi.impl.filter;

import java.util.Optional;

import jakarta.annotation.Priority;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.api.port.WhitelistFilter;
import org.os890.jawelte.module.cdi.impl.util.FrameworkAllowlist;
import org.os890.jawelte.module.cdi.impl.util.TestBeanScanner;

/**
 * Default {@link WhitelistFilter}. Allows a type when:
 * <ul>
 *   <li>it lives under a framework allowlisted package
 *       ({@link FrameworkAllowlist}), <strong>or</strong></li>
 *   <li>it is a {@code @TestBean} target on the active test class
 *       — read from the {@link TestContext}'s metadata where the
 *       scan result was bound during {@code BeforeBeanDiscovery}.</li>
 * </ul>
 *
 * <p>Annotated {@code @Priority(Integer.MAX_VALUE)} so any
 * user-supplied implementation with a lower priority value wins via
 * the project-wide {@code ServicePriorityResolver}.
 *
 * <p>Consulted by cdi-module's CDI Extension only when
 * {@code @EnableTestBeans(limitToTestBeans=true)} is in effect; in
 * normal mode the Extension never invokes this filter at all.
 */
@Priority(Integer.MAX_VALUE)
public class DefaultWhitelistFilter implements WhitelistFilter {

    /** No-arg constructor required by {@code ServiceLoader}. */
    public DefaultWhitelistFilter() {
    }

    @Override
    public boolean isAllowed(Class<?> rawType) {
        if (rawType == null) {
            return false;
        }
        if (FrameworkAllowlist.isAllowlisted(rawType)) {
            return true;
        }
        Optional<TestBeanScanner.Result> scanResult = activeScanResult();
        return scanResult.map(result -> result.isTarget(rawType)).orElse(false);
    }

    private static Optional<TestBeanScanner.Result> activeScanResult() {
        try {
            TestContext active = TestContext.get();
            return active.getMetadata(TestBeanScanner.Result.class);
        } catch (IllegalStateException noActiveContext) {
            return Optional.empty();
        }
    }
}
