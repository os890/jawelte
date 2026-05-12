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
package org.os890.jawelte.module.dbtestdata.impl.util;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Compares an expected cell value (as written in the dataset, with
 * special markers) against an actual cell value (as read from the
 * database). The match order is fixed by the api contract:
 *
 * <ol>
 *   <li>{@code [NULL]} (case-sensitive uppercase) — SQL NULL;</li>
 *   <li>{@code ~regex} — regex match against the actual value (the
 *       regex takes precedence over boolean / numeric normalisation,
 *       so {@code ~true} is the literal regex {@code true} and not
 *       the boolean);</li>
 *   <li>{@code uuid'…'} — parse the expected as a UUID and compare
 *       against the actual (binary 16-byte form or canonical hex
 *       string);</li>
 *   <li>boolean normalisation — case-insensitive built-in lists,
 *       extended by builder / MP Config values;</li>
 *   <li>numeric ({@link BigDecimal#compareTo(BigDecimal)}) —
 *       trailing zeros are not significant;</li>
 *   <li>fallback — {@link String#equals(Object)} after toString().</li>
 * </ol>
 *
 * <p>The comparator carries the boolean true / false lists chosen
 * by the builder (built-in plus extensions) so it can run the
 * normalisation step without re-resolving MP Config on every call.
 * Instances are immutable and thread-safe.
 */
public class MarkerComparator {

    /** {@code [NULL]} sentinel — case-sensitive uppercase only. */
    public static final String NULL_MARKER = "[NULL]";

    /** Regex marker prefix — values starting with {@code ~} are always regex. */
    public static final String REGEX_PREFIX = "~";

    private static final String UUID_PREFIX = "uuid'";

    private static final String UUID_SUFFIX = "'";

    private static final List<String> DEFAULT_TRUE_VALUES = List.of("true", "1", "yes", "y", "on");

    private static final List<String> DEFAULT_FALSE_VALUES = List.of("false", "0", "no", "n", "off");

    private final Set<String> trueValues;

    private final Set<String> falseValues;

    /**
     * Build a comparator with the configured boolean extension lists.
     * Built-in values are always recognised; the {@code extra*}
     * arguments extend them (no replacement).
     *
     * @param extraTrueValues  extra values that normalise to
     *                         {@code true}
     * @param extraFalseValues extra values that normalise to
     *                         {@code false}
     */
    public MarkerComparator(List<String> extraTrueValues, List<String> extraFalseValues) {
        this.trueValues = buildBooleanSet(DEFAULT_TRUE_VALUES, extraTrueValues);
        this.falseValues = buildBooleanSet(DEFAULT_FALSE_VALUES, extraFalseValues);
    }

    private static Set<String> buildBooleanSet(List<String> defaults, List<String> extras) {
        Set<String> result = new HashSet<>();
        for (String defaultValue : defaults) {
            result.add(defaultValue.toLowerCase(Locale.ROOT));
        }
        for (String extra : extras) {
            result.add(extra.toLowerCase(Locale.ROOT));
        }
        return result;
    }

    /**
     * Whether {@code expected} matches {@code actual} under the api
     * contract's marker rules.
     *
     * @param expected the dataset's cell value as written by the
     *                 test author (possibly carrying a marker)
     * @param actual   the database's cell value; {@code null}
     *                 represents SQL NULL
     * @return {@code true} when the cells match
     */
    public boolean matches(String expected, Object actual) {
        if (NULL_MARKER.equals(expected)) {
            return actual == null;
        }
        if (actual == null) {
            return false;
        }
        if (expected.startsWith(REGEX_PREFIX)) {
            String pattern = expected.substring(REGEX_PREFIX.length());
            return Pattern.matches(pattern, asString(actual));
        }
        if (expected.startsWith(UUID_PREFIX) && expected.endsWith(UUID_SUFFIX)
                && expected.length() > UUID_PREFIX.length() + UUID_SUFFIX.length()) {
            return uuidMatches(expected, actual);
        }
        if (isBoolean(expected)) {
            String actualString = asString(actual);
            if (isBoolean(actualString)) {
                return normaliseBoolean(expected).equals(normaliseBoolean(actualString));
            }
            return false;
        }
        BigDecimal expectedNumeric = tryParseDecimal(expected);
        if (expectedNumeric != null) {
            BigDecimal actualNumeric = tryParseDecimal(asString(actual));
            if (actualNumeric != null) {
                return expectedNumeric.compareTo(actualNumeric) == 0;
            }
        }
        return expected.equals(asString(actual));
    }

    private boolean isBoolean(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return trueValues.contains(lower) || falseValues.contains(lower);
    }

    private Boolean normaliseBoolean(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (trueValues.contains(lower)) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    private static boolean uuidMatches(String expected, Object actual) {
        String hex = expected.substring(UUID_PREFIX.length(), expected.length() - UUID_SUFFIX.length());
        UUID expectedUuid;
        try {
            expectedUuid = UUID.fromString(hex);
        } catch (IllegalArgumentException parseFailure) {
            return false;
        }
        if (actual instanceof byte[] bytes) {
            if (bytes.length != 16) {
                return false;
            }
            return uuidFromBytes(bytes).equals(expectedUuid);
        }
        String actualString = asString(actual);
        try {
            return UUID.fromString(actualString).equals(expectedUuid);
        } catch (IllegalArgumentException parseFailure) {
            return false;
        }
    }

    private static UUID uuidFromBytes(byte[] bytes) {
        long msb = 0;
        long lsb = 0;
        for (int byteIndex = 0; byteIndex < 8; byteIndex++) {
            msb = (msb << 8) | (bytes[byteIndex] & 0xff);
        }
        for (int byteIndex = 8; byteIndex < 16; byteIndex++) {
            lsb = (lsb << 8) | (bytes[byteIndex] & 0xff);
        }
        return new UUID(msb, lsb);
    }

    private static BigDecimal tryParseDecimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException parseFailure) {
            return null;
        }
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes);
        }
        return value.toString();
    }
}
