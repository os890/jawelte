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
package org.os890.jawelte.tests.ejb.scenario04;

import jakarta.ejb.Stateless;

/**
 * {@code @Stateless} bean. Two injection points must resolve to
 * different instances because the default mapping is
 * {@code @Dependent} (per-injection-point fresh instance).
 */
@Stateless
public class StatelessTagger {

    /**
     * Required public no-arg constructor.
     */
    public StatelessTagger() {
    }

    /**
     * Identity probe — returns this instance.
     *
     * @return {@code this}
     */
    public StatelessTagger self() {
        return this;
    }
}
