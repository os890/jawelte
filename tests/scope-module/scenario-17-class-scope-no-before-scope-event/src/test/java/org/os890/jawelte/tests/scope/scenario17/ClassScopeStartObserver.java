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
package org.os890.jawelte.tests.scope.scenario17;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.os890.jawelte.core.api.event.BeforeScopeStarted;
import org.os890.jawelte.module.scope.api.TestClassScoped;

/**
 * Observer that counts BeforeScopeStarted events whose scope is
 * TestClassScoped. The contract is: this counter must remain at 0,
 * since scope-module never fires the event for the class scope.
 */
@ApplicationScoped
public class ClassScopeStartObserver {

    static final AtomicInteger CLASS_SCOPE_START_COUNT = new AtomicInteger();

    public ClassScopeStartObserver() {
    }

    void onBeforeScopeStarted(@Observes BeforeScopeStarted event) {
        if (event.getScope() == TestClassScoped.class) {
            CLASS_SCOPE_START_COUNT.incrementAndGet();
        }
    }
}
