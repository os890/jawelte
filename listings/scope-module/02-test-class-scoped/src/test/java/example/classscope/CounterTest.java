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
package example.classscope;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Two test methods sharing the same {@code @TestClassScoped} bean.
 * The first method increments and asserts {@code 1}; the second
 * method increments AGAIN and asserts {@code 2} — proving the
 * counter survived across the method boundary. The
 * {@code @TestMethodOrder} pins the execution order so the
 * second assertion's value is deterministic.
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CounterTest {

    @Inject
    Counter counter;

    @Test
    @Order(1)
    void firstMethodIncrementsToOne() {
        assertThat(counter.increment()).isEqualTo(1);
    }

    @Test
    @Order(2)
    void secondMethodSeesTheSameCounterAtTwo() {
        assertThat(counter.increment()).isEqualTo(2);
    }
}
