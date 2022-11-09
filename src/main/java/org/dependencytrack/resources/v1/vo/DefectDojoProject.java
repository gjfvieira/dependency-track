package org.dependencytrack.resources.v1.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.Pattern;

public final class DefectDojoProject {
    @Pattern(regexp = "\\d+", message = "engagementId must be a digit")
    private String engagementId;

    @Pattern(regexp = "^true$|^false$", message = "reimport must be a boolean value allowed input: true or false")
    private String reimport;

    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", message = "The project must be a valid 36 character UUID")
    private String project;

    @JsonCreator
    public DefectDojoProject(
            @JsonProperty(value = "project", required = false) String project,
            @JsonProperty(value = "engagementId", required = false) String engagementId,
            @JsonProperty(value = "reimport", required = false) String reimport) {
        this.project = project;
        this.engagementId = engagementId;
        this.reimport = reimport;
    }

    public String getEngagementId() {
        return engagementId;
    }

    public void setEngagementId(String engagementId) {
        this.engagementId = engagementId;
    }

    public String getReimport() {
        return reimport;
    }

    public void setReimport(boolean reimport) {
        this.reimport = String.valueOf(reimport);
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }
}

