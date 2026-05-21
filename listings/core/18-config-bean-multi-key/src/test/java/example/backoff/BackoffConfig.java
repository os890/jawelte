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
package example.backoff;

import java.time.Duration;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import org.os890.jawelte.core.api.ConfigBean;
import org.os890.jawelte.core.api.port.ConfigResolver;

/**
 * Production-shaped config bean: one required key (no default),
 * three optional keys with typed defaults, and a @PostConstruct
 * guard that throws when the required key is missing.
 */
@ConfigBean
public class BackoffConfig {

    @Inject
    ConfigResolver resolver;

    @PostConstruct
    void validate() {
        if (resolver.resolve("app.backoff.max-retries").isEmpty()) {
            throw new IllegalStateException(
                    "Required config key 'app.backoff.max-retries' is not set");
        }
    }

    /** Required — @PostConstruct throws if missing. */
    public int maxRetries() {
        return resolver.resolve("app.backoff.max-retries").map(Integer::parseInt).orElseThrow();
    }

    /** Default 100 ms. */
    public Duration initialDelay() {
        return resolver.resolve("app.backoff.initial-delay")
                .map(Duration::parse)                       // ISO-8601: "PT0.1S", "PT500MS"
                .orElse(Duration.ofMillis(100));
    }

    /** Default 2.0. */
    public double factor() {
        return resolver.resolve("app.backoff.factor").map(Double::parseDouble).orElse(2.0);
    }

    /** Default true (feature flag). */
    public boolean enabled() {
        return resolver.resolve("app.backoff.enabled").map(Boolean::parseBoolean).orElse(true);
    }
}
