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
package org.os890.jawelte.tests.core.scenario05;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.os890.jawelte.core.api.port.TestBeanContainerPort;
import org.os890.jawelte.core.api.port.TestContext;

public class RecordingContainerPort implements TestBeanContainerPort {

    public static final List<Class<?>> testClassesSeen = new CopyOnWriteArrayList<>();

    public RecordingContainerPort() {
    }

    @Override
    public void beforeAll(TestContext testContext) {
        testClassesSeen.add(testContext.getTestClass());
    }

    @Override
    public void beforeEach(TestContext testContext) {
        testClassesSeen.add(testContext.getTestClass());
    }

    @Override
    public void postProcessTestInstance(TestContext testContext, Object testInstance) {
        testClassesSeen.add(testContext.getTestClass());
    }

    @Override
    public void afterEach(TestContext testContext) {
        testClassesSeen.add(testContext.getTestClass());
    }

    @Override
    public void afterAll(TestContext testContext) {
        testClassesSeen.add(testContext.getTestClass());
    }
}
