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
package example.customresolver;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import org.os890.jawelte.core.api.port.ConfigResolver;

/**
 * Custom {@link ConfigResolver} that returns a fixed in-memory
 * value for {@code app.greeting}. Made the active resolver by
 * carrying {@code @Alternative @Priority(...)} — CDI 4 picks the
 * highest-priority globally-enabled alternative over the framework
 * default (which lives in {@code core/impl} without
 * {@code @Alternative}).
 */
@ApplicationScoped
@Alternative
@Priority(100)
public class FixedConfigResolver implements ConfigResolver {

    private static final Map<String, String> FIXTURE = Map.of(
            "app.greeting", "hello-from-test-resolver"
    );

    @Override
    public Optional<String> resolve(String dotKey) {
        return Optional.ofNullable(FIXTURE.get(dotKey));
    }

    @Override
    public Iterable<String> resolveKeys() {
        return FIXTURE.keySet();
    }

    @Override
    public List<String> resolveAliasKeysFor(String logicalKey) {
        return List.of();
    }
}
