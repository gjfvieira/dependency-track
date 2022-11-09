package org.dependencytrack.resources.v1.vo;

public final class DefectDojoResponse {
    private String engagementId;
    private String reimport;
    private String project;
    private boolean success;

    public DefectDojoResponse(String engagementId, String reimport, String project, boolean success) {
        this.engagementId = engagementId;
        this.reimport = reimport;
        this.project = project;
        this.success = success;
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

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean getSuccess() {
        return this.success;
    }
}
