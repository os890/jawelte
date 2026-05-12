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
package org.os890.jawelte.tests.ejb.scenario17;

import jakarta.ejb.Singleton;

import org.os890.jawelte.module.scope.api.TestClassScoped;

/**
 * Test author explicitly declared {@code @TestClassScoped} alongside
 * {@code @Singleton}. The user-declared scope wins over both
 * ejb-module's default mapping AND scope-module's
 * {@code TestBeanDefaultScope} override (which also resolves to
 * {@code @TestClassScoped} in this scenario — the user-declared
 * annotation just happens to match).
 */
@Singleton
@TestClassScoped
public class UserScopedSingleton {

    /** Required public no-arg constructor. */
    public UserScopedSingleton() {
    }
}
