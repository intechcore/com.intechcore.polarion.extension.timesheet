package com.intechcore.polarion.extension.timesheet.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScopeInfo {
    private String path; // value passed back as scope_path: "/" (root), project id, or a group location path
    private String name;
    private String type; // root | group | project
    private int depth;
}
