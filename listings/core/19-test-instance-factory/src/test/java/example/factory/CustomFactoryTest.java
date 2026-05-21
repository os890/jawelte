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
package example.factory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Proves the custom RecordingTestInstanceFactory was the one JUnit
 * called to instantiate this test class. Since the recorder runs
 * before any @Test body, by the time this assertion executes the list
 * already contains the test class.
 */
@EnableTestBeans
class CustomFactoryTest {

    @Test
    void customFactoryProducedThisTestInstance() {
        assertThat(RecordingTestInstanceFactory.INSTANTIATED)
                .as("the custom factory recorded the test class it instantiated")
                .contains(CustomFactoryTest.class);
    }
}
