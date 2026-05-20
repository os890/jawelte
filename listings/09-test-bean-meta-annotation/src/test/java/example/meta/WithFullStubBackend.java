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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Aggregating meta-annotation built out of two smaller building-block
 * meta-annotations. Carries no {@code @TestBean} declaration of its
 * own — it just composes {@link WithStubEmail} and
 * {@link WithStubClock}. jawelte's {@code TestBeanScanner} walks
 * the annotation graph recursively (cycle-safe; skips {@code java.}
 * and {@code jakarta.} packages), so the two leaf {@code @TestBean}
 * declarations behind these meta-annotations both get picked up
 * when a test class carries only {@code @WithFullStubBackend}.
 *
 * <p>Pattern for larger test suites: keep one small reusable
 * meta-annotation per stub (one job each), then assemble them into
 * named profiles like this one.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WithStubEmail
@WithStubClock
public @interface WithFullStubBackend {
}
