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
package org.os890.jawelte.core.api.event;

/**
 * CDI event fired by the {@code TestBeanContainerPort} implementation
 * inside its {@code beforeAll}, after the CDI container is ready but
 * before {@code TestModuleLifecyclePort.beforeAll} is called. Module
 * lifecycle ports observing this event are guaranteed to do so before
 * their own {@code beforeAll} runs.
 */
public class ContainerStarted {

    private final Class<?> testClass;

    /**
     * Construct a {@code ContainerStarted} event.
     *
     * @param testClass the test class for which the container was started
     */
    public ContainerStarted(Class<?> testClass) {
        this.testClass = testClass;
    }

    /**
     * Get the test class for which the container was started.
     *
     * @return the test class
     */
    public Class<?> getTestClass() {
        return testClass;
    }
}
