package org.dependencytrack.resources.v1;

import alpine.common.logging.Logger;
import alpine.model.ConfigProperty;
import alpine.server.auth.PermissionRequired;
import org.dependencytrack.auth.Permissions;
import org.dependencytrack.integrations.defectdojo.DefectDojoUploader;
import org.dependencytrack.model.*;
import org.dependencytrack.persistence.QueryManager;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import org.dependencytrack.model.Project;
import org.dependencytrack.resources.v1.vo.DefectDojoProject;
import org.dependencytrack.resources.v1.vo.DefectDojoResponse;

import javax.validation.Validator;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.*;

import static org.dependencytrack.integrations.defectdojo.DefectDojoUploader.ENGAGEMENTID_PROPERTY;
import static org.dependencytrack.integrations.defectdojo.DefectDojoUploader.REIMPORT_PROPERTY;
import static org.dependencytrack.model.ConfigPropertyConstants.*;

@Path("/v1/integrations")
@Api(value = "integrations", authorizations = @Authorization(value = "X-Api-Key"))
public class IntegrationsResource extends AbstractConfigPropertyResource {

    private static final Logger LOGGER = Logger.getLogger(org.dependencytrack.resources.v1.IntegrationsResource.class);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Returns a list of all integrations' status",
            response = ConfigProperty.class,
            responseContainer = "List"
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized")
    })
    @PermissionRequired(Permissions.Constants.VIEW_PORTFOLIO)
    public Response getIntegrations() {
        try (QueryManager qm = new QueryManager(getAlpineRequest())) {
            final List<ConfigProperty> integrations = new ArrayList<>();
            integrations.add(qm.getConfigProperty(DEFECTDOJO_ENABLED.getGroupName(), DEFECTDOJO_ENABLED.getPropertyName()));
            integrations.add(qm.getConfigProperty(KENNA_ENABLED.getGroupName(), KENNA_ENABLED.getPropertyName()));
            integrations.add(qm.getConfigProperty(FORTIFY_SSC_ENABLED.getGroupName(), FORTIFY_SSC_ENABLED.getPropertyName()));
            return Response.ok(integrations).build();
        }
    }

    /*
    * DefectDojo Integrations Section
    */

    @GET
    @Path("/defectdojo/configuration")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Returns DefectDojo Configuration Properties",
            response = ConfigProperty.class,
            responseContainer = "List"
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized")
    })
    @PermissionRequired(Permissions.Constants.VIEW_PORTFOLIO)
    public Response getDefectDojoConfiguration() {
        try (QueryManager qm = new QueryManager(getAlpineRequest())) {
            final List<ConfigProperty> defectDojoConfig = new ArrayList<>();
            defectDojoConfig.add(qm.getConfigProperty(DEFECTDOJO_ENABLED.getGroupName(), DEFECTDOJO_ENABLED.getPropertyName()));
            defectDojoConfig.add(qm.getConfigProperty(DEFECTDOJO_REIMPORT_ENABLED.getGroupName(), DEFECTDOJO_REIMPORT_ENABLED.getPropertyName()));
            defectDojoConfig.add(qm.getConfigProperty(DEFECTDOJO_SYNC_CADENCE.getGroupName(), DEFECTDOJO_SYNC_CADENCE.getPropertyName()));
            defectDojoConfig.add(qm.getConfigProperty(DEFECTDOJO_URL.getGroupName(), DEFECTDOJO_URL.getPropertyName()));
            return Response.ok(defectDojoConfig).build();
        }
    }

    @GET
    @Path("/defectdojo/projects")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Returns all the DefectDojo configured projects' UUID and values",
            response = DefectDojoProject.class,
            responseContainer = "List"
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized")
    })
    @PermissionRequired(Permissions.Constants.VIEW_PORTFOLIO)
    public Response getDefectDojoProjects() {
        try (QueryManager qm = new QueryManager(getAlpineRequest())) {
            final List<DefectDojoProject> response = new ArrayList<>();
            for (final Project project : qm.getAllProjects()) {
                final ProjectProperty engagementId = qm.getProjectProperty(project, DEFECTDOJO_ENABLED.getGroupName(), ENGAGEMENTID_PROPERTY);
                final ProjectProperty reimport = qm.getProjectProperty(project, DEFECTDOJO_ENABLED.getGroupName(), REIMPORT_PROPERTY);
                if (engagementId != null && engagementId.getPropertyValue() != null){;
                    response.add( new DefectDojoProject(
                            project.getUuid().toString(),
                            engagementId.getPropertyValue(),
                            reimport != null ? reimport.getPropertyValue() : "false")
                    );
                }
            }
            return Response.ok(response).build();
        }
    }

    @POST
    @Path("/defectdojo/upload/{uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Uploads a project to DefectDojo and returns the project",
            response = DefectDojoResponse.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 403, message = "Access to the specified project is forbidden"),
            @ApiResponse(code = 404, message = "The project could not be found"),
            @ApiResponse(code = 412, message = "The project is not configured for DefectDojo integration")
    })
    @PermissionRequired(Permissions.Constants.VULNERABILITY_MANAGEMENT)
    public Response uploadDefectDojo(
            @ApiParam(value = "The UUID of the Project", required = true)
            @PathParam("uuid") String uuid, DefectDojoProject request) {
        try (QueryManager qm = new QueryManager()) {
            final Project project = qm.getObjectByUuid(Project.class, uuid, Project.FetchGroup.ALL.name());
            if (project != null) {
                if (qm.hasAccess(super.getPrincipal(), project)) {
                    var uploader = new DefectDojoUploader();
                    uploader.setQueryManager(qm);
                    final Validator validator = super.getValidator();
                    failOnValidationError(
                            validator.validateProperty(request, "project"),
                            validator.validateProperty(request, "engagementId"),
                            validator.validateProperty(request, "reimport")
                    );

                    if (uploader.isEnabled()){
                        LOGGER.debug("Initializing integration point: " + uploader.name() + " for project: " + project.getUuid());
                        final List<Finding> findings = qm.getFindings(project);
                        final InputStream payload = uploader.process(project, findings);
                        boolean success;
                        final DefectDojoResponse response;
                        uploader.setReimport(request.getReimport());
                        if (request.getEngagementId() != null){
                            uploader.setEngagementId(request.getEngagementId());
                            response = submitDojoUpload(project, uploader, payload);
                        }
                        else{
                            if (uploader.isProjectConfigured(project)) {
                                response = submitDojoUpload(project, uploader, payload);
                            }
                            else {
                                return Response.status(Response.Status.PRECONDITION_FAILED).entity("The project is not configured for DefectDojo integration (Missing engagementId).").build();
                            }
                        }
                        return Response.ok(response).build();
                    }
                    else{
                        return Response.status(Response.Status.PRECONDITION_FAILED).entity("DefectDojo integration is not enabled").build();
                    }
                } else {
                    return Response.status(Response.Status.FORBIDDEN).entity("Access to the specified project is forbidden").build();
                }
            } else {
                return Response.status(Response.Status.NOT_FOUND).entity("The project could not be found.").build();
            }
        }
    }

    private static DefectDojoResponse submitDojoUpload(Project project, DefectDojoUploader uploader, InputStream payload) {
        final DefectDojoResponse response;
        boolean success;
        LOGGER.debug("Uploading findings to " + uploader.name() + " for project: " + project.getUuid());
        success = uploader.upload(project, payload);
        response = new DefectDojoResponse(uploader.getEngagementId(), uploader.getReimport(), project.getUuid().toString(), success);
        return response;
    }

}
