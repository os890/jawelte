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
package example.meta;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Applies the aggregating meta-annotation only — and gets BOTH
 * {@code @TestBean} declarations (the email stub via
 * {@link WithStubEmail}, the clock stub via {@link WithStubClock})
 * because jawelte's scanner walks the meta-annotation graph
 * recursively.
 */
@EnableTestBeans
@WithFullStubBackend
class FullStubBackendTest {

    @Inject
    EmailService emailService;

    @Inject
    Clock clock;

    @Test
    void bothStubsActivated() {
        assertThat(emailService.send("alice@example.com")).isEqualTo("stub:alice@example.com");
        assertThat(clock.now()).isEqualTo(StubClock.PINNED);
    }
}
