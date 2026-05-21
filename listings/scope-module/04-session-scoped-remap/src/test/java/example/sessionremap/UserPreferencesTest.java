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
package example.sessionremap;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Both test methods mutate UserPreferences. If @SessionScoped were
 * still active the second method would observe the first method's
 * write; under scope-module's remap to @TestMethodScoped each method
 * sees a fresh instance with the default theme.
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserPreferencesTest {

    @Inject
    UserPreferences userPreferences;

    @Test
    @Order(1)
    void firstMethodMutatesTheme() {
        assertThat(userPreferences.getThemeName()).isEqualTo("default");
        userPreferences.setThemeName("dark");
        assertThat(userPreferences.getThemeName()).isEqualTo("dark");
    }

    @Test
    @Order(2)
    void secondMethodSeesAFreshInstance() {
        assertThat(userPreferences.getThemeName())
                .as("the @SessionScoped bean was remapped to @TestMethodScoped, so the prior write is gone")
                .isEqualTo("default");
    }
}
