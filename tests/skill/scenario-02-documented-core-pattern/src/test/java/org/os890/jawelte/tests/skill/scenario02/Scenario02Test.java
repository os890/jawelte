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
package org.os890.jawelte.tests.skill.scenario02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.TestBean;

/**
 * The two code samples in SKILL.md, run as written.
 *
 * <p>The first is the opening example - inject the bean under test and
 * let its collaborator be auto-mocked. The second is the variant the
 * skill gives for stubbing and verifying, which exists because the
 * obvious alternative is the collision pinned by
 * {@code tests/cdi-module} scenario 64.
 */
@EnableTestBeans
class Scenario02Test {

    @TestBean
    static final AuditService AUDIT = mock(AuditService.class);

    @Inject
    private Greeter greeter;

    @Test
    void theBeanUnderTestIsInjectedAndItsCollaboratorIsSupplied() {
        assertThat(greeter.greet("world")).isEqualTo("hello world");
    }

    @Test
    void theCollaboratorCanBeStubbedAndVerified() {
        when(AUDIT.audit("greet")).thenReturn("logged");

        assertThat(greeter.greet("world")).isEqualTo("hello world");

        verify(AUDIT).audit("greet");
    }
}
