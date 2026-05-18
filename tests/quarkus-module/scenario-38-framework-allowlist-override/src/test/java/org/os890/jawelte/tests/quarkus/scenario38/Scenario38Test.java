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
package org.os890.jawelte.tests.quarkus.scenario38;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.cdi.impl.util.FrameworkAllowlist;

import com.acme.AcmeService;

class Scenario38Test {

    @Test
    void frameworkAllowlistOverrideTakesEffectAndDropsBundledDefaults() {
        assertThat(FrameworkAllowlist.isAllowlisted(AcmeService.class)).isTrue();
        assertThat(FrameworkAllowlist.isAllowlisted(java.util.ArrayList.class)).isFalse();
    }
}
