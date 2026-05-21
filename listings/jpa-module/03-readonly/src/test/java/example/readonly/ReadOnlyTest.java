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
package example.readonly;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;

@EnableTestBeans
@PersistenceConfig(persistenceUnitName = "notesPU")
class ReadOnlyTest {

    @Inject
    NoteService noteService;

    @Test
    void readOnlyPersistIsDiscardedAfterCommit() {
        Long assignedId = noteService.tryToPersistButDiscard("transient");
        assertThat(assignedId)
                .as("the persist+flush inside the method assigned an id (in-memory)")
                .isNotNull();
        assertThat(noteService.count())
                .as("after the method returns, the rollback discarded the row")
                .isEqualTo(0L);
    }

    @Test
    void plainTransactionalStillCommits() {
        noteService.persistNote("real");
        assertThat(noteService.count()).isEqualTo(1L);
    }
}
