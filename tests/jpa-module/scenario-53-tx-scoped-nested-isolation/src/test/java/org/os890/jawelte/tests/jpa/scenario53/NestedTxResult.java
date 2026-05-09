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
package org.os890.jawelte.tests.jpa.scenario53;

import java.util.UUID;

/**
 * Carries the three sampled tracker ids so the test can assert
 * outer-survives-nested-call AND inner-gets-its-own-instance.
 *
 * @param outerBefore tracker id sampled inside outer tx, before the nested call
 * @param innerId     tracker id sampled inside the inner (nested) tx
 * @param outerAfter  tracker id sampled inside outer tx, after the nested call
 */
public record NestedTxResult(UUID outerBefore, UUID innerId, UUID outerAfter) {
}
