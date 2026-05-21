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

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import org.os890.jawelte.core.api.port.TestInstanceFactoryPort;

/**
 * Custom TestInstanceFactoryPort. Records every test class it was
 * asked to instantiate and returns a reflection-built instance.
 *
 * <p>Realistic alternatives: a CDI container (cdi-module), a Spring
 * ApplicationContext, an arbitrary DI framework, or a hand-rolled
 * test-instance lookup. The contract is just "give JUnit something
 * fully-initialised to run @Test on".
 */
public class RecordingTestInstanceFactory implements TestInstanceFactoryPort {

    public static final List<Class<?>> INSTANTIATED = new CopyOnWriteArrayList<>();

    @Override
    public Object createInstance(Class<?> testClass) {
        INSTANTIATED.add(testClass);
        try {
            var constructor = testClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to instantiate " + testClass.getName(), e);
        }
    }
}
