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
package org.os890.jawelte.module.wiremock.api.event;

/**
 * CDI event fired by {@code WireMockLifecycleAdapter} in
 * {@code afterAll}, once every registered
 * {@code WireMockServer} has been asked to {@code stop()} (and
 * after any per-server stop failures have been aggregated into
 * the throwable's suppressed-list, but before the registry is
 * cleared and before the event-throwing observer chain runs).
 *
 * <p>This is the deterministic alternative to a TCP probe for
 * verifying server lifecycle in tests: observers that need to
 * react to "all servers are now stopped" can {@code @Observes}
 * this type instead of polling the released port. The contract
 * is "wiremock-module has finished asking its servers to stop"
 * — the {@code WireMockServer.stop()} call may have returned
 * before the OS released the listening socket, and observers
 * making external assertions about port availability still
 * need to handle that timing race themselves.
 *
 * <p>The event carries no payload — the lifecycle adapter
 * doesn't expose stop diagnostics through it. Stop failures
 * propagate through the regular afterAll exception path; the
 * event fires regardless (the lifecycle adapter's
 * {@code finally} block sees to it).
 *
 * <p>Fires inside the CDI container's still-active lifetime
 * (cdi-module's {@code TestBeanContainerPort.afterAll} closes
 * the {@code SeContainer} only after every
 * {@code TestModuleLifecyclePort.afterAll} has returned).
 * Observers should be {@code @ApplicationScoped} — the
 * scope-module's {@code @TestClassScoped} context has already
 * been deactivated by the time wiremock-module's
 * {@code afterAll} runs.
 */
public class WireMockServersStopped {

    /** No-arg constructor — the event carries no payload. */
    public WireMockServersStopped() {
    }
}
