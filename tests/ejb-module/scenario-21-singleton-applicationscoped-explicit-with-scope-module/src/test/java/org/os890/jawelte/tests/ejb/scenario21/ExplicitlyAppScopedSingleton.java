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
package org.os890.jawelte.tests.ejb.scenario21;

import jakarta.ejb.Singleton;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Test author explicitly declared {@code @ApplicationScoped}.
 * scope-module is on the classpath (so the
 * {@code BeanScopeMapper provider} fallback would have
 * picked {@code @TestClassScoped}), but the user-declared CDI scope
 * wins.
 */
@Singleton
@ApplicationScoped
public class ExplicitlyAppScopedSingleton {

    /** Required public no-arg constructor. */
    public ExplicitlyAppScopedSingleton() {
    }
}
