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
package example.stateless;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * @Stateless gets rewritten to @RequestScoped at ProcessAnnotatedType
 * time. Both @Test methods increment the counter once; both see 1
 * because the @RequestScoped lifecycle hands each method a fresh
 * instance.
 */
@EnableTestBeans
class CounterTest {

    @Inject
    Counter counter;

    @Test
    void firstMethodSeesFreshCounter() {
        assertThat(counter.increment()).isEqualTo(1);
    }

    @Test
    void secondMethodAlsoSeesFreshCounter() {
        assertThat(counter.increment()).isEqualTo(1);
    }
}
