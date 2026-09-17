package com.intechcore.polarion.extension.timesheet.util;

import com.polarion.alm.shared.api.Scope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scope value travels from the widget through the report URL to the REST call as a plain
 * string, so every shape the user interface can send has to map back to the same scope.
 */
class ScopeFactoryImplTest {

    private final ScopeFactoryImpl factory = new ScopeFactoryImpl();

    @Test
    void global_isGlobal() {
        assertThat(factory.global().isGlobal()).isTrue();
    }

    @Test
    void project_returnsTheProjectScope() {
        Scope scope = factory.project("elibrary");

        assertThat(scope.projectId()).isEqualTo("elibrary");
        assertThat(scope.isGlobal()).isFalse();
    }

    @Test
    void project_withoutAnIdIsGlobal() {
        assertThat(factory.project(null).isGlobal()).isTrue();
        assertThat(factory.project("").isGlobal()).isTrue();
        assertThat(factory.project("   ").isGlobal()).isTrue();
    }

    @Test
    void project_trimsTheId() {
        assertThat(factory.project("  elibrary  ").projectId()).isEqualTo("elibrary");
    }

    @Test
    void fromPath_nullIsGlobal() {
        assertThat(factory.fromPath(null).isGlobal()).isTrue();
    }

    @Test
    void fromPath_rootIsGlobal() {
        assertThat(factory.fromPath("/").isGlobal()).isTrue();
        assertThat(factory.fromPath("  /  ").isGlobal()).isTrue();
    }

    @Test
    void fromPath_groupPath() {
        Scope scope = factory.fromPath("/drafts");

        assertThat(scope.isGlobal()).isFalse();
        assertThat(scope.projectId()).isNull();
    }

    @Test
    void fromPath_withoutASlashIsAProjectId() {
        assertThat(factory.fromPath("elibrary").projectId()).isEqualTo("elibrary");
    }
}
