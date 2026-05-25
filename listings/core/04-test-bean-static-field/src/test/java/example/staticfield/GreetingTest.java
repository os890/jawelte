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
package example.staticfield;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.TestBean;

@EnableTestBeans
class GreetingTest {

    /**
     * Literal value held on a static field — {@code @TestBean} on
     * a static field registers the field's value as the bean
     * instance with scope {@code @Singleton}, bean types of the
     * declared field type plus {@code Object}, and the field's
     * CDI qualifiers.
     */
    @TestBean
    public static final Greeting WELCOME_GREETING = new Greeting("hello-from-static-field");

    /**
     * Same recipe, but the field value comes from
     * {@code Mockito.mock(...)} — the synthetic bean is a fully
     * stubbable Mockito mock that the test method below configures
     * with {@code when(...)}. Idiomatic for sticking a hand-built
     * mock into the container without needing an
     * {@code @Alternative} class or a producer-method class.
     */
    @TestBean
    public static final Clock CLOCK = mock(Clock.class);

    /**
     * The injection point lives in a production-shaped bean
     * (WelcomeService, in src/main) — not on the test class. That
     * is what makes this listing demonstrate "@TestBean publishes
     * to the container" rather than just "static fields are
     * accessible from anywhere".
     */
    @Inject
    WelcomeService welcomeService;

    @Test
    void serviceReceivesTheStaticFieldsViaCdi() {
        Instant pinned = Instant.parse("2026-05-20T12:00:00Z");
        when(CLOCK.now()).thenReturn(pinned);

        assertThat(welcomeService.compose())
                .as("WelcomeService composed its String from the @TestBean Greeting + the stubbed @TestBean Clock")
                .isEqualTo("hello-from-static-field @ 2026-05-20T12:00:00Z");
    }

    /**
     * Identity check: the bean instance the container hands to
     * WelcomeService is the same object that lives on this test
     * class's static field. The static field IS the bean — there
     * is no copying or proxy wrapping for {@code @Singleton}.
     */
    @Inject
    Greeting greetingDirect;

    @Test
    void staticFieldValueIsTheSameInstanceTheContainerHandsOut() {
        assertThat(greetingDirect).isSameAs(WELCOME_GREETING);
    }
}
