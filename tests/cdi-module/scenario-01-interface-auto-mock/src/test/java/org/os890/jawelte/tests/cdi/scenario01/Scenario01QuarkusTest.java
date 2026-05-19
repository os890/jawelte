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
package org.os890.jawelte.tests.cdi.scenario01;

/**
 * Quarkus companion of {@link Scenario01Test}. Annotated only with
 * {@code @io.quarkus.test.junit.QuarkusTest}; inherits the
 * {@code @EnableTestBeans}, the {@code @Inject AuditService} field,
 * and the test method from the parent.
 *
 * <p>Activated only under the {@code -Pquarkus} profile (the OWB and
 * Weld profiles filter this class out of surefire's include set so it
 * doesn't try to boot ArC alongside the SE container).
 *
 * <p>Reference implementation lives on the {@code quarkus-full-poc}
 * branch; the layout here demonstrates the subclass pattern the
 * triple-runtime architecture proposes — see
 * {@code docs/triple-runtime-architecture.md} for the rationale.
 */
// @io.quarkus.test.junit.QuarkusTest  -- uncomment under -Pquarkus
class Scenario01QuarkusTest extends Scenario01Test {
}
