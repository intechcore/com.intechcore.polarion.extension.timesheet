package com.intechcore.polarion.extension.timesheet;

import com.polarion.alm.ui.server.navigation.NavigationExtender;
import com.polarion.alm.ui.server.navigation.NavigationExtenderNode;
import com.polarion.subterra.base.data.identification.IContextId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The topic Timesheet in the navigation of a project or of the repository: the report as a page of
 * its own, without a Live Report. In a project it shows that project only; in the repository the
 * scope may be changed.
 */
public class TimesheetNavigationExtender extends NavigationExtender {

    public static final String ID = "timesheet";

    private static final String APP_URL = "/polarion/timesheet-app/ui/app/index.html?feature=report";

    @NotNull
    @Override
    public String getId() {
        return ID;
    }

    @NotNull
    @Override
    public String getLabel() {
        return "Timesheet";
    }

    @Nullable
    @Override
    public String getIconUrl() {
        return "/polarion/timesheet-app/ui/images/menu/30x30/_parent.svg";
    }

    @Nullable
    @Override
    public String getPageUrl(@NotNull IContextId contextId) {
        String projectId = contextId.getContextName();
        if (projectId == null || projectId.isEmpty()) {
            return APP_URL + "&scope=%2F";
        }
        return APP_URL + "&scope=" + URLEncoder.encode(projectId, StandardCharsets.UTF_8) + "&scopeLocked=true";
    }

    @Override
    public boolean requiresToken() {
        return false;
    }

    @NotNull
    @Override
    public List<NavigationExtenderNode> getRootNodes(@NotNull IContextId contextId) {
        return new ArrayList<>();
    }
}
