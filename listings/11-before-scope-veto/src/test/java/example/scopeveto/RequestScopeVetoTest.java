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
package example.scopeveto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

@EnableTestBeans
class RequestScopeVetoTest {

    @Test
    void vetoerObservedBeforeScopeStartedAndCalledVeto() {
        // The observer fires inside cdi-module's beforeEach right
        // before it would call RequestContextController.activate();
        // when isVetoed() comes back true, the activate() + bind
        // pair is skipped. The flag below confirms our observer ran
        // and asked for the veto.
        assertThat(RequestScopeVetoer.VETOED).isTrue();
    }
}
