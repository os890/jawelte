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
package org.os890.jawelte.tests.jpa.scenario60;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.jpa.api.port.EntityScanner;
import org.os890.jawelte.module.jpa.api.port.EntityScanner.Whitelist;

/**
 * Direct unit tests against
 * {@link EntityScanner.Whitelist#matches(String)} and
 * {@link EntityScanner.Whitelist#isEmpty()}. The whitelist is the
 * positive filter driven by the
 * {@code org.os890.jawelte.module.jpa.entity-scan.whitelist.packages}
 * and {@code .patterns} MicroProfile Config keys; the matcher logic
 * is exercised here to lock down its semantics independent of the
 * CDI bootstrap.
 */
public class Scenario60Test {

    /** No-arg constructor required by JUnit. */
    public Scenario60Test() {
    }

    /** An empty whitelist matches nothing — and isEmpty() is true. */
    @Test
    public void emptyWhitelistMatchesNothing() {
        Whitelist whitelist = Whitelist.empty();

        assertThat(whitelist.isEmpty()).isTrue();
        assertThat(whitelist.matches("com.example.Foo")).isFalse();
    }

    /** A literal package prefix lets matching FQCNs through. */
    @Test
    public void literalPackagePrefixMatchesByStartsWith() {
        Whitelist whitelist = new Whitelist(List.of("com.example.domain."), List.of());

        assertThat(whitelist.isEmpty()).isFalse();
        assertThat(whitelist.matches("com.example.domain.Foo")).isTrue();
        assertThat(whitelist.matches("com.example.domain.sub.Bar")).isTrue();
        assertThat(whitelist.matches("com.example.other.Baz")).isFalse();
    }

    /** A regex pattern matches via Pattern.matcher.matches(). */
    @Test
    public void regexPatternMatchesByFullMatch() {
        Whitelist whitelist = new Whitelist(
                List.of(),
                List.of(Pattern.compile("com\\.example\\.[a-z]+\\.entity\\..+")));

        assertThat(whitelist.isEmpty()).isFalse();
        assertThat(whitelist.matches("com.example.shop.entity.Order")).isTrue();
        assertThat(whitelist.matches("com.example.users.entity.User")).isTrue();
        assertThat(whitelist.matches("com.example.shop.dto.OrderDto")).isFalse();
        assertThat(whitelist.matches("com.example.entity.Order"))
                .as("requires the [a-z]+ middle segment — no match without it")
                .isFalse();
    }

    /** Either a literal OR a pattern is enough to pass. */
    @Test
    public void literalAndPatternAreCombinedAsLogicalOr() {
        Whitelist whitelist = new Whitelist(
                List.of("com.example.legacy."),
                List.of(Pattern.compile("com\\.example\\..*\\.Entity[A-Z].*")));

        assertThat(whitelist.matches("com.example.legacy.OldEntity"))
                .as("legacy package prefix wins")
                .isTrue();
        assertThat(whitelist.matches("com.example.shop.EntityFoo"))
                .as("regex match wins")
                .isTrue();
        assertThat(whitelist.matches("com.example.dto.UserDto"))
                .as("neither rule fires")
                .isFalse();
    }
}
