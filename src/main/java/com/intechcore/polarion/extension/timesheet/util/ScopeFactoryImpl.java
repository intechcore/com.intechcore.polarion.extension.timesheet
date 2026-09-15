package com.intechcore.polarion.extension.timesheet.util;

import com.polarion.alm.shared.api.Scope;
import com.polarion.alm.shared.api.ScopeFactory;
import com.polarion.alm.shared.api.impl.ScopeImpl;
import com.polarion.portal.internal.shared.navigation.ProjectGroupScope;
import com.polarion.portal.internal.shared.navigation.ProjectScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScopeFactoryImpl implements ScopeFactory {

    @Override
    public @NotNull Scope global() {
        return new ScopeImpl(new ProjectGroupScope(""));
    }

    @Override
    public Scope project(String projectId) {
        if (projectId == null) {
            return global();
        } else {
            projectId = projectId.trim();
            if (projectId.isEmpty()) {
                return global();
            } else {
                return new ScopeImpl(new ProjectScope(projectId));
            }
        }
    }

    @Override
    public @NotNull Scope fromPath(@Nullable String path) {
        if (path == null) {
            return global();
        } else {
            path = path.trim();
            if (path.startsWith("/")) {
                if ("/".equals(path)) {
                    return global();
                } else {
                    return new ScopeImpl(new ProjectGroupScope(path));
                }
            } else {
                return project(path);
            }
        }
    }
}
