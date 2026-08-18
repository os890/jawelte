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
package org.os890.jawelte.tests.wiremock.scenario27;

import java.lang.reflect.Proxy;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.cdi.api.port.MockFactory;

import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * A {@link MockFactory} that always succeeds for an interface,
 * replacing the shipped Mockito one for this scenario.
 *
 * <p>Without it the scenario would prove nothing: on this JDK
 * {@code MockitoMockFactory} answers {@code null} for every type
 * (#128), so auto-mock registers nothing and no collision can occur
 * whether or not wiremock-module records what it supplies.
 *
 * <p>The three types wiremock-module supplies are all classes, not
 * interfaces, so a proxy cannot stand in for them. For {@link WireMock}
 * this returns a real instance aimed at a port nothing listens on —
 * which is all auto-mock needs to register a competing bean, and is what
 * makes the collision reachable. Anything the module has recorded never
 * reaches this factory at all.
 *
 * <p>{@code @Priority(100)} beats the shipped factory's
 * {@code Integer.MAX_VALUE}.
 */
@Priority(100)
public class StubMockFactory implements MockFactory {

    /** No-arg constructor required by {@code ServiceLoader}. */
    public StubMockFactory() {
    }

    /** A port nothing is listening on, so a stand-in cannot work by luck. */
    private static final int DEAD_PORT = 1;

    @Override
    public <T> T create(Class<T> rawType) {
        if (rawType == WireMock.class) {
            return rawType.cast(new WireMock(DEAD_PORT));
        }
        if (!rawType.isInterface()) {
            return null;
        }
        return rawType.cast(Proxy.newProxyInstance(
                rawType.getClassLoader(),
                new Class<?>[] {rawType},
                (proxy, method, args) -> null));
    }
}
