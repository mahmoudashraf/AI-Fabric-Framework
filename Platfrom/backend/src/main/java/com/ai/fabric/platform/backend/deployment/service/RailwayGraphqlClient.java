package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.model.RailwayLogAttributeSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayLogEntrySummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayLogTagsSummary;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RailwayGraphqlClient {

    private static final Logger log = LoggerFactory.getLogger(RailwayGraphqlClient.class);

    private static final String PROJECTS_QUERY = """
        query workspaceProjects($workspaceId: String!) {
          projects(workspaceId: $workspaceId) {
            edges {
              node {
                id
                name
              }
            }
          }
        }
        """;

    private static final String API_TOKEN_WORKSPACES_QUERY = """
        query apiTokenWorkspaces {
          apiToken {
            workspaces {
              id
              name
            }
          }
        }
        """;

    private static final String PROJECT_SNAPSHOT_QUERY = """
        query project($id: String!) {
          project(id: $id) {
            id
            name
            services {
              edges {
                node {
                  id
                  name
                }
              }
            }
            environments {
              edges {
                node {
                  id
                  name
                }
              }
            }
          }
        }
        """;

    private static final String SERVICE_INSTANCE_QUERY = """
        query serviceInstance($environmentId: String!, $serviceId: String!) {
          serviceInstance(environmentId: $environmentId, serviceId: $serviceId) {
            id
            serviceId
            serviceName
            rootDirectory
            dockerfilePath
            healthcheckPath
            upstreamUrl
            source {
              repo
              image
            }
          }
        }
        """;

    private static final String SERVICE_QUERY = """
        query service($id: String!) {
          service(id: $id) {
            id
            name
            repoTriggers {
              edges {
                node {
                  id
                  serviceId
                  repository
                  branch
                  provider
                }
              }
            }
          }
        }
        """;

    private static final String SERVICE_DEPLOYMENTS_QUERY = """
        query serviceDeployments($id: String!, $limit: Int!) {
          service(id: $id) {
            id
            deployments(first: $limit) {
              edges {
                node {
                  id
                  status
                  url
                  staticUrl
                  createdAt
                }
              }
            }
          }
        }
        """;

    private static final String PROJECT_CREATE_MUTATION = """
        mutation projectCreate($input: ProjectCreateInput!) {
          projectCreate(input: $input) {
            id
            name
          }
        }
        """;

    private static final String ENVIRONMENT_CREATE_MUTATION = """
        mutation environmentCreate($input: EnvironmentCreateInput!) {
          environmentCreate(input: $input) {
            id
            name
          }
        }
        """;

    private static final String SERVICE_CREATE_MUTATION = """
        mutation serviceCreate($input: ServiceCreateInput!) {
          serviceCreate(input: $input) {
            id
            name
          }
        }
        """;

    private static final String SERVICE_CONNECT_MUTATION = """
        mutation serviceConnect($id: String!, $input: ServiceConnectInput!) {
          serviceConnect(id: $id, input: $input) {
            id
            name
          }
        }
        """;

    private static final String SERVICE_INSTANCE_UPDATE_MUTATION = """
        mutation serviceInstanceUpdate(
          $serviceId: String!,
          $environmentId: String!,
          $input: ServiceInstanceUpdateInput!
        ) {
          serviceInstanceUpdate(
            serviceId: $serviceId,
            environmentId: $environmentId,
            input: $input
          )
        }
        """;

    private static final String VARIABLE_COLLECTION_UPSERT_MUTATION = """
        mutation variableCollectionUpsert($input: VariableCollectionUpsertInput!) {
          variableCollectionUpsert(input: $input)
        }
        """;

    private static final String VARIABLES_QUERY = """
        query variables(
          $projectId: String!,
          $environmentId: String!,
          $serviceId: String,
          $unrendered: Boolean
        ) {
          variables(
            projectId: $projectId,
            environmentId: $environmentId,
            serviceId: $serviceId,
            unrendered: $unrendered
          )
        }
        """;

    private static final String SERVICE_INSTANCE_DEPLOY_MUTATION = """
        mutation serviceInstanceDeployV2($serviceId: String!, $environmentId: String!) {
          serviceInstanceDeployV2(serviceId: $serviceId, environmentId: $environmentId)
        }
        """;

    private static final String SERVICE_DELETE_MUTATION = """
        mutation serviceDelete($id: String!) {
          serviceDelete(id: $id)
        }
        """;

    private static final String ENVIRONMENT_DELETE_MUTATION = """
        mutation environmentDelete($id: String!) {
          environmentDelete(id: $id)
        }
        """;

    private static final String PROJECT_DELETE_MUTATION = """
        mutation projectDelete($id: String!) {
          projectDelete(id: $id)
        }
        """;

    private static final String DEPLOYMENT_QUERY = """
        query deployment($id: String!) {
          deployment(id: $id) {
            id
            status
            url
            staticUrl
            createdAt
          }
        }
        """;

    private static final String LOG_FIELDS = """
        timestamp
        severity
        message
        tags {
          deploymentId
          deploymentInstanceId
          environmentId
          projectId
          serviceId
          snapshotId
        }
        attributes {
          key
          value
        }
        """;

    private static final String DOMAINS_QUERY = """
        query domains($projectId: String!, $environmentId: String!, $serviceId: String!) {
          domains(
            projectId: $projectId
            environmentId: $environmentId
            serviceId: $serviceId
          ) {
            serviceDomains {
              id
              domain
            }
          }
        }
        """;

    private static final String SERVICE_DOMAIN_CREATE_MUTATION = """
        mutation serviceDomainCreate($input: ServiceDomainCreateInput!) {
          serviceDomainCreate(input: $input) {
            id
            domain
          }
        }
        """;

    private static final String ENVIRONMENT_STAGED_CHANGES_QUERY = """
        query environmentStagedChanges($environmentId: String!) {
          environmentStagedChanges(environmentId: $environmentId) {
            id
            status
            message
            lastAppliedError
          }
        }
        """;

    private static final String ENVIRONMENT_PATCH_COMMIT_STAGED_MUTATION = """
        mutation environmentPatchCommitStaged($environmentId: String!) {
          environmentPatchCommitStaged(environmentId: $environmentId)
        }
        """;

    private final ObjectMapper objectMapper;
    private final PlatformProvisioningProperties provisioningProperties;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public RailwayGraphqlClient(ObjectMapper objectMapper,
                                PlatformProvisioningProperties provisioningProperties) {
        this.objectMapper = objectMapper;
        this.provisioningProperties = provisioningProperties;
        this.requestTimeout = provisioningProperties.deploymentPollInterval().compareTo(Duration.ofSeconds(30)) > 0
            ? Duration.ofSeconds(30)
            : provisioningProperties.deploymentPollInterval().plusSeconds(10);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(this.requestTimeout)
            .build();
    }

    public RailwayProjectSnapshot findProjectByName(String workspaceId, String projectName) {
        JsonNode data = execute(PROJECTS_QUERY, Map.of("workspaceId", workspaceId));
        for (JsonNode edge : data.path("projects").path("edges")) {
            JsonNode node = edge.path("node");
            if (projectName.equals(node.path("name").asText())) {
                return getProject(node.path("id").asText());
            }
        }
        return null;
    }

    public List<RailwayProjectSnapshot> listProjectsInWorkspace(String workspaceId) {
        JsonNode data = execute(PROJECTS_QUERY, Map.of("workspaceId", workspaceId));
        List<RailwayProjectSnapshot> projects = new ArrayList<>();
        for (JsonNode edge : data.path("projects").path("edges")) {
            String projectId = text(edge.path("node").path("id"));
            if (projectId != null) {
                projects.add(getProject(projectId));
            }
        }
        return projects;
    }

    public List<RailwayWorkspaceSummary> listAccessibleWorkspaces() {
        JsonNode data = execute(API_TOKEN_WORKSPACES_QUERY, Map.of());
        List<RailwayWorkspaceSummary> workspaces = new ArrayList<>();
        for (JsonNode node : data.path("apiToken").path("workspaces")) {
            workspaces.add(new RailwayWorkspaceSummary(
                text(node.path("id")),
                text(node.path("name"))
            ));
        }
        return workspaces;
    }

    public RailwayProjectSnapshot createProject(String workspaceId,
                                                String projectName,
                                                String defaultEnvironmentName) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", projectName);
        input.put("workspaceId", workspaceId);
        input.put("defaultEnvironmentName", defaultEnvironmentName);

        JsonNode data = execute(PROJECT_CREATE_MUTATION, Map.of("input", input));
        String projectId = text(data.path("projectCreate").path("id"));
        if (projectId == null) {
            throw new RailwayProvisioningException("Railway projectCreate did not return a project id.");
        }
        log.info("Railway project created: name={}, id={}", projectName, projectId);
        return getProject(projectId);
    }

    public RailwayProjectSnapshot getProject(String projectId) {
        JsonNode data = execute(PROJECT_SNAPSHOT_QUERY, Map.of("id", projectId));
        JsonNode project = data.path("project");
        if (project.isMissingNode() || project.isNull()) {
            throw new RailwayProvisioningException("Railway project not found: " + projectId);
        }

        List<RailwayEnvironmentSummary> environments = new ArrayList<>();
        for (JsonNode edge : project.path("environments").path("edges")) {
            JsonNode node = edge.path("node");
            environments.add(new RailwayEnvironmentSummary(
                text(node.path("id")),
                text(node.path("name"))
            ));
        }

        List<RailwayServiceSummary> services = new ArrayList<>();
        for (JsonNode edge : project.path("services").path("edges")) {
            JsonNode node = edge.path("node");
            services.add(new RailwayServiceSummary(
                text(node.path("id")),
                text(node.path("name"))
            ));
        }

        return new RailwayProjectSnapshot(
            text(project.path("id")),
            text(project.path("name")),
            environments,
            services
        );
    }

    public RailwayServiceInstanceSummary getServiceInstance(String environmentId, String serviceId) {
        JsonNode data = execute(
            SERVICE_INSTANCE_QUERY,
            Map.of(
                "environmentId", environmentId,
                "serviceId", serviceId
            )
        );
        JsonNode instance = data.path("serviceInstance");
        if (instance.isMissingNode() || instance.isNull()) {
            throw new RailwayProvisioningException(
                "Railway service instance not found: environmentId=" + environmentId + ", serviceId=" + serviceId
            );
        }
        return new RailwayServiceInstanceSummary(
            text(instance.path("id")),
            text(instance.path("serviceId")),
            text(instance.path("serviceName")),
            text(instance.path("rootDirectory")),
            text(instance.path("dockerfilePath")),
            text(instance.path("healthcheckPath")),
            text(instance.path("upstreamUrl")),
            text(instance.path("source").path("repo")),
            text(instance.path("source").path("image"))
        );
    }

    public RailwayServiceSourceSummary getServiceSource(String serviceId) {
        JsonNode data = execute(SERVICE_QUERY, Map.of("id", serviceId));
        JsonNode service = data.path("service");
        if (service.isMissingNode() || service.isNull()) {
            throw new RailwayProvisioningException("Railway service not found: " + serviceId);
        }

        List<RailwayDeploymentTriggerSummary> triggers = new ArrayList<>();
        for (JsonNode edge : service.path("repoTriggers").path("edges")) {
            JsonNode node = edge.path("node");
            triggers.add(new RailwayDeploymentTriggerSummary(
                text(node.path("id")),
                text(node.path("serviceId")),
                text(node.path("repository")),
                text(node.path("branch")),
                text(node.path("provider"))
            ));
        }

        return new RailwayServiceSourceSummary(
            text(service.path("id")),
            text(service.path("name")),
            triggers
        );
    }

    public RailwayEnvironmentSummary createEnvironment(String projectId, String environmentName) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("projectId", projectId);
        input.put("name", environmentName);
        input.put("skipInitialDeploys", true);

        JsonNode data = execute(ENVIRONMENT_CREATE_MUTATION, Map.of("input", input));
        JsonNode node = data.path("environmentCreate");
        String id = text(node.path("id"));
        if (id == null) {
            throw new RailwayProvisioningException("Railway environmentCreate did not return an environment id.");
        }
        log.info("Railway environment created: name={}, id={}, projectId={}", environmentName, id, projectId);
        return new RailwayEnvironmentSummary(id, text(node.path("name")));
    }

    public RailwayServiceSummary createServiceFromRepository(String projectId,
                                                            String serviceName,
                                                            String repository,
                                                            String branch) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("repo", repository);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("projectId", projectId);
        input.put("name", serviceName);
        input.put("source", source);
        input.put("branch", branch);

        JsonNode data = execute(SERVICE_CREATE_MUTATION, Map.of("input", input));
        JsonNode node = data.path("serviceCreate");
        String id = text(node.path("id"));
        if (id == null) {
            throw new RailwayProvisioningException("Railway serviceCreate did not return a service id.");
        }
        log.info("Railway service created: name={}, id={}, projectId={}", serviceName, id, projectId);
        return new RailwayServiceSummary(id, text(node.path("name")));
    }

    public RailwayServiceSummary connectServiceToRepository(String serviceId,
                                                            String repository,
                                                            String branch) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("repo", repository);
        input.put("branch", branch);

        JsonNode data = execute(SERVICE_CONNECT_MUTATION, Map.of(
            "id", serviceId,
            "input", input
        ));
        JsonNode node = data.path("serviceConnect");
        String id = text(node.path("id"));
        if (id == null) {
            throw new RailwayProvisioningException("Railway serviceConnect did not return a service id.");
        }
        log.info("Railway service connected to repository: serviceId={}, repository={}, branch={}",
            serviceId,
            repository,
            branch);
        return new RailwayServiceSummary(id, text(node.path("name")));
    }

    public void updateServiceInstance(String serviceId,
                                      String environmentId,
                                      String rootDirectory,
                                      String dockerfilePath,
                                      String healthcheckPath) {
        Map<String, Object> input = new LinkedHashMap<>();
        if (rootDirectory != null && !rootDirectory.isBlank()) {
            input.put("rootDirectory", rootDirectory);
        }
        if (dockerfilePath != null && !dockerfilePath.isBlank()) {
            input.put("dockerfilePath", dockerfilePath);
        }
        input.put("healthcheckPath", healthcheckPath);

        execute(
            SERVICE_INSTANCE_UPDATE_MUTATION,
            Map.of(
                "serviceId", serviceId,
                "environmentId", environmentId,
                "input", input
            )
        );
        log.info(
            "Railway service instance updated: serviceId={}, environmentId={}, rootDirectory={}, dockerfilePath={}",
            serviceId,
            environmentId,
            rootDirectory,
            dockerfilePath
        );
    }

    public void upsertVariables(String projectId,
                                String environmentId,
                                String serviceId,
                                List<RailwayEnvVarInput> variables) {
        Map<String, String> variableMap = new LinkedHashMap<>();
        for (RailwayEnvVarInput variable : variables) {
            variableMap.put(variable.name(), variable.value());
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("projectId", projectId);
        input.put("environmentId", environmentId);
        input.put("serviceId", serviceId);
        input.put("variables", variableMap);
        input.put("replace", false);
        input.put("skipDeploys", true);

        execute(VARIABLE_COLLECTION_UPSERT_MUTATION, Map.of("input", input));
        log.info(
            "Railway variables upserted: serviceId={}, environmentId={}, keys={}",
            serviceId,
            environmentId,
            variableMap.keySet()
        );
    }

    public JsonNode getVariables(String projectId,
                                 String environmentId,
                                 String serviceId,
                                 boolean unrendered) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("projectId", projectId);
        variables.put("environmentId", environmentId);
        variables.put("serviceId", serviceId);
        variables.put("unrendered", unrendered);
        JsonNode data = execute(VARIABLES_QUERY, variables);
        JsonNode value = data.path("variables");
        return value.isMissingNode() || value.isNull() ? objectMapper.createObjectNode() : value.deepCopy();
    }

    public String deployService(String serviceId, String environmentId) {
        JsonNode data = execute(
            SERVICE_INSTANCE_DEPLOY_MUTATION,
            Map.of("serviceId", serviceId, "environmentId", environmentId)
        );
        String deploymentId = text(data.path("serviceInstanceDeployV2"));
        if (deploymentId == null) {
            throw new RailwayProvisioningException(
                "Railway serviceInstanceDeployV2 did not return a deployment id for service " + serviceId
            );
        }
        log.info(
            "Railway deployment triggered: deploymentId={}, serviceId={}, environmentId={}",
            deploymentId,
            serviceId,
            environmentId
        );
        return deploymentId;
    }

    public void deleteService(String serviceId) {
        execute(SERVICE_DELETE_MUTATION, Map.of("id", serviceId));
        log.info("Railway service deleted: serviceId={}", serviceId);
    }

    public void deleteEnvironment(String environmentId) {
        execute(ENVIRONMENT_DELETE_MUTATION, Map.of("id", environmentId));
        log.info("Railway environment deleted: environmentId={}", environmentId);
    }

    public void deleteProject(String projectId) {
        execute(PROJECT_DELETE_MUTATION, Map.of("id", projectId));
        log.info("Railway project deleted: projectId={}", projectId);
    }

    public List<RailwayDeploymentSummary> listServiceDeployments(String serviceId, int limit) {
        JsonNode data = execute(
            SERVICE_DEPLOYMENTS_QUERY,
            Map.of("id", serviceId, "limit", Math.max(limit, 1))
        );
        JsonNode service = data.path("service");
        if (service.isMissingNode() || service.isNull()) {
            throw new RailwayProvisioningException("Railway service not found: " + serviceId);
        }
        List<RailwayDeploymentSummary> deployments = new ArrayList<>();
        for (JsonNode edge : service.path("deployments").path("edges")) {
            JsonNode node = edge.path("node");
            deployments.add(new RailwayDeploymentSummary(
                text(node.path("id")),
                text(node.path("status")),
                text(node.path("url")),
                text(node.path("staticUrl")),
                text(node.path("createdAt"))
            ));
        }
        return deployments;
    }

    public RailwayDeploymentSummary getDeployment(String deploymentId) {
        JsonNode data = execute(DEPLOYMENT_QUERY, Map.of("id", deploymentId));
        JsonNode node = data.path("deployment");
        if (node.isMissingNode() || node.isNull()) {
            throw new RailwayProvisioningException("Railway deployment not found: " + deploymentId);
        }
        return new RailwayDeploymentSummary(
            text(node.path("id")),
            text(node.path("status")),
            text(node.path("url")),
            text(node.path("staticUrl")),
            text(node.path("createdAt"))
        );
    }

    public List<RailwayLogEntrySummary> fetchDeploymentLogs(String deploymentId,
                                                            Integer limit,
                                                            String filter,
                                                            String startDate,
                                                            String endDate) {
        return fetchLogs("deploymentLogs", "deploymentId", deploymentId, limit, filter, startDate, endDate);
    }

    public List<RailwayLogEntrySummary> fetchBuildLogs(String deploymentId,
                                                       Integer limit,
                                                       String filter,
                                                       String startDate,
                                                       String endDate) {
        return fetchLogs("buildLogs", "deploymentId", deploymentId, limit, filter, startDate, endDate);
    }

    public List<RailwayLogEntrySummary> fetchHttpLogs(String deploymentId,
                                                      Integer limit,
                                                      String filter,
                                                      String startDate,
                                                      String endDate) {
        return fetchLogs("httpLogs", "deploymentId", deploymentId, limit, filter, startDate, endDate);
    }

    public List<RailwayServiceDomainSummary> listServiceDomains(String projectId,
                                                                String environmentId,
                                                                String serviceId) {
        JsonNode data = execute(
            DOMAINS_QUERY,
            Map.of(
                "projectId", projectId,
                "environmentId", environmentId,
                "serviceId", serviceId
            )
        );

        List<RailwayServiceDomainSummary> domains = new ArrayList<>();
        for (JsonNode node : data.path("domains").path("serviceDomains")) {
            domains.add(new RailwayServiceDomainSummary(
                text(node.path("id")),
                text(node.path("domain"))
            ));
        }
        return domains;
    }

    public RailwayServiceDomainSummary createServiceDomain(String serviceId, String environmentId) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("serviceId", serviceId);
        input.put("environmentId", environmentId);

        JsonNode data = execute(SERVICE_DOMAIN_CREATE_MUTATION, Map.of("input", input));
        JsonNode node = data.path("serviceDomainCreate");
        String id = text(node.path("id"));
        String domain = text(node.path("domain"));
        if (id == null || domain == null) {
            throw new RailwayProvisioningException("Railway serviceDomainCreate did not return a domain.");
        }
        log.info("Railway service domain created: serviceId={}, environmentId={}, domain={}", serviceId, environmentId, domain);
        return new RailwayServiceDomainSummary(id, domain);
    }

    public boolean hasStagedChanges(String environmentId) {
        JsonNode data = execute(ENVIRONMENT_STAGED_CHANGES_QUERY, Map.of("environmentId", environmentId));
        return hasMeaningfulStagedChanges(data.path("environmentStagedChanges"));
    }

    static boolean hasMeaningfulStagedChanges(JsonNode staged) {
        if (staged == null || staged.isMissingNode() || staged.isNull()) {
            return false;
        }
        if (!staged.isObject()) {
            return false;
        }

        String id = textValue(staged.path("id"));
        if (id == null || "<empty>".equalsIgnoreCase(id)) {
            return false;
        }

        String status = textValue(staged.path("status"));
        if (status == null) {
            return true;
        }

        return !"EMPTY".equalsIgnoreCase(status)
            && !"NONE".equalsIgnoreCase(status)
            && !"NO_CHANGES".equalsIgnoreCase(status);
    }

    public void commitStagedChanges(String environmentId) {
        execute(ENVIRONMENT_PATCH_COMMIT_STAGED_MUTATION, Map.of("environmentId", environmentId));
        log.info("Railway staged changes committed: environmentId={}", environmentId);
    }

    private List<RailwayLogEntrySummary> fetchLogs(String queryField,
                                                   String idArgName,
                                                   String idValue,
                                                   Integer limit,
                                                   String filter,
                                                   String startDate,
                                                   String endDate) {
        StringBuilder args = new StringBuilder();
        appendArgument(args, idArgName, graphQlStringLiteral(idValue));
        if (limit != null) {
            appendArgument(args, "limit", Integer.toString(limit));
        }
        if (filter != null && !filter.isBlank()) {
            appendArgument(args, "filter", graphQlStringLiteral(filter));
        }
        if (startDate != null && !startDate.isBlank()) {
            appendArgument(args, "startDate", graphQlStringLiteral(startDate));
        }
        if (endDate != null && !endDate.isBlank()) {
            appendArgument(args, "endDate", graphQlStringLiteral(endDate));
        }

        String query = """
            query {
              %s(%s) {
                %s
              }
            }
            """.formatted(queryField, args, LOG_FIELDS);

        JsonNode data = execute(query, Map.of());
        List<RailwayLogEntrySummary> logs = new ArrayList<>();
        for (JsonNode node : data.path(queryField)) {
            logs.add(new RailwayLogEntrySummary(
                text(node.path("timestamp")),
                text(node.path("severity")),
                text(node.path("message")),
                new RailwayLogTagsSummary(
                    text(node.path("tags").path("deploymentId")),
                    text(node.path("tags").path("deploymentInstanceId")),
                    text(node.path("tags").path("environmentId")),
                    text(node.path("tags").path("projectId")),
                    text(node.path("tags").path("serviceId")),
                    text(node.path("tags").path("snapshotId"))
                ),
                attributes(node.path("attributes"))
            ));
        }
        return logs;
    }

    private JsonNode execute(String query, Map<String, Object> variables) {
        if (provisioningProperties.railwayApiToken().isBlank()) {
            throw new RailwayProvisioningConfigurationException(
                "RAILWAY_API mode requires RAILWAY_API_TOKEN to be configured."
            );
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query);
        payload.put("variables", variables);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(provisioningProperties.railwayApiEndpoint()))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + provisioningProperties.railwayApiToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        } catch (IOException ex) {
            throw new RailwayProvisioningException("Failed to serialize Railway GraphQL request.", ex);
        }

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RailwayProvisioningException(
                    "Railway API request failed with HTTP " + response.statusCode() + "."
                );
            }

            JsonNode payloadNode = objectMapper.readTree(response.body());
            JsonNode errors = payloadNode.path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                String message = errors.get(0).path("message").asText("Unknown Railway GraphQL error.");
                throw new RailwayProvisioningException("Railway GraphQL error: " + message);
            }

            JsonNode data = payloadNode.path("data");
            if (data.isMissingNode() || data.isNull()) {
                throw new RailwayProvisioningException("Railway GraphQL response did not contain a data payload.");
            }
            return data;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RailwayProvisioningException("Railway API request was interrupted.", ex);
        } catch (IOException ex) {
            throw new RailwayProvisioningException("Railway API request failed.", ex);
        }
    }

    private String text(JsonNode node) {
        return textValue(node);
    }

    private String graphQlStringLiteral(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new RailwayProvisioningException("Failed to serialize Railway log query value.", ex);
        }
    }

    private void appendArgument(StringBuilder builder, String key, String valueLiteral) {
        if (!builder.isEmpty()) {
            builder.append(", ");
        }
        builder.append(key).append(": ").append(valueLiteral);
    }

    private List<RailwayLogAttributeSummary> attributes(JsonNode attributesNode) {
        List<RailwayLogAttributeSummary> attributes = new ArrayList<>();
        for (JsonNode attributeNode : attributesNode) {
            attributes.add(new RailwayLogAttributeSummary(
                text(attributeNode.path("key")),
                text(attributeNode.path("value"))
            ));
        }
        return attributes;
    }

    private static String textValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    public record RailwayProjectSnapshot(
        String id,
        String name,
        List<RailwayEnvironmentSummary> environments,
        List<RailwayServiceSummary> services
    ) {
        public RailwayEnvironmentSummary environmentNamed(String environmentName) {
            return environments.stream()
                .filter(item -> environmentName.equals(item.name()))
                .findFirst()
                .orElse(null);
        }

        public RailwayServiceSummary serviceNamed(String serviceName) {
            return services.stream()
                .filter(item -> serviceName.equals(item.name()))
                .findFirst()
                .orElse(null);
        }
    }

    public record RailwayEnvironmentSummary(String id, String name) {
    }

    public record RailwayWorkspaceSummary(String id, String name) {
    }

    public record RailwayServiceSummary(String id, String name) {
    }

    public record RailwayServiceInstanceSummary(
        String id,
        String serviceId,
        String serviceName,
        String rootDirectory,
        String dockerfilePath,
        String healthcheckPath,
        String upstreamUrl,
        String sourceRepo,
        String sourceImage
    ) {
    }

    public record RailwayServiceSourceSummary(
        String id,
        String name,
        List<RailwayDeploymentTriggerSummary> repoTriggers
    ) {
    }

    public record RailwayDeploymentTriggerSummary(
        String id,
        String serviceId,
        String repository,
        String branch,
        String provider
    ) {
    }

    public record RailwayEnvVarInput(String name, String value) {
    }

    public record RailwayDeploymentSummary(
        String id,
        String status,
        String url,
        String staticUrl,
        String createdAt
    ) {
    }

    public record RailwayServiceDomainSummary(String id, String domain) {
    }
}
