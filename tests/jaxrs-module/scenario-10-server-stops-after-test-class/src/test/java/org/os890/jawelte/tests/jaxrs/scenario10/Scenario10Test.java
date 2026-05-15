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
package org.os890.jawelte.tests.jaxrs.scenario10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Scenario 10 — uses {@code EngineTestKit} to run
 * {@link Scenario10Subject} as an inner JUnit suite, which boots
 * and (in its {@code afterAll}) shuts down the embedded server.
 * After the inner suite completes, the outer test attempts a TCP
 * connect to the captured port and expects
 * {@link ConnectException} — proving the port was released.
 */
class Scenario10Test {

    @Test
    void serverPortIsReleasedAfterTestClassCompletes() throws IOException, InterruptedException {
        Scenario10Subject.clearCapturedUrl();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario10Subject.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.succeeded(1).failed(0));

        String url = Scenario10Subject.getCapturedUrl();
        assertThat(url)
                .as("the inner subject captured the live URL while the server was up")
                .isNotNull();

        URI uri = URI.create(url);
        // Jetty (CXF's transport) returns from stop() before the OS
        // fully releases the listening socket. Retry the probe up to
        // ~2s; the first attempt that raises ConnectException satisfies
        // the assertion.
        ConnectException lastFailure = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(uri.getHost(), uri.getPort()), 200);
            } catch (ConnectException refused) {
                lastFailure = refused;
                break;
            }
            Thread.sleep(50);
        }
        assertThat(lastFailure)
                .as("expected ConnectException on TCP probe to %s within ~2s — the port "
                        + "should be released by JaxRsLifecycleAdapter.afterAll", url)
                .isNotNull();
    }
}
