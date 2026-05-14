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
package org.os890.jawelte.tests.testcontrol.scenario24;

import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.testcontrol.api.TestControl;

/**
 * Scenario 24 — {@code @TestControl} on a superclass method, no
 * override in the subclass. Java inheritance exposes the parent
 * method on the subclass; testcontrol resolves it through JUnit
 * Jupiter's {@code AnnotationSupport.findAnnotation} class-hierarchy
 * walk and binds the parent's annotation on
 * {@link org.os890.jawelte.core.api.port.TestContext}. The body of
 * the test (and its assertion that the bound
 * {@link TestControl#testDataBasePath()} matches the parent's
 * declaration) lives in {@link Scenario24Base}.
 */
@EnableTestBeans
public class Scenario24Test extends Scenario24Base {
}
