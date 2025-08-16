package com.xiaoxj.tools;

import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentCondition;
import io.kubernetes.client.openapi.models.V1Pod;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeploymentTools {

    private final AppsV1Api appsV1Api;
    private final CoreV1Api coreV1Api;

    public DeploymentTools(AppsV1Api appsV1Api, CoreV1Api coreV1Api) {
        this.appsV1Api = appsV1Api;
        this.coreV1Api = coreV1Api;
    }

    @Tool(name = "list_deployments", description = "Lists all Kubernetes deployments in the specified namespace")
    public String listDeployments(
            @ToolParam(description = "The Kubernetes namespace to list deployments from") String namespace) {
        return listDeployments(namespace, "default");
    }

    @Tool(name = "list_deployments", description = "Lists all Kubernetes deployments in the specified namespace")
    public String listDeployments(
            @ToolParam(description = "The Kubernetes namespace to list deployments from") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            var deployments = appsV1Api.listNamespacedDeployment(ns, null, null, null, null, null, null, null, null, null, null)
                    .getItems();

            if (deployments.isEmpty()) {
                return "No deployments found in namespace '" + ns + "'";
            }

            List<String> deploymentList = deployments.stream()
                    .map(deployment -> {
                        String name = deployment.getMetadata() != null ? deployment.getMetadata().getName() : null;
                        if (name == null) return null;

                        int availableReplicas = deployment.getStatus() != null && deployment.getStatus().getAvailableReplicas() != null ?
                                deployment.getStatus().getAvailableReplicas() : 0;
                        int desiredReplicas = deployment.getSpec() != null && deployment.getSpec().getReplicas() != null ?
                                deployment.getSpec().getReplicas() : 0;
                        String strategy = deployment.getSpec() != null && deployment.getSpec().getStrategy() != null ?
                                deployment.getSpec().getStrategy().getType() : "Not set";

                        String resourceInfo = "";
                        if (deployment.getSpec() != null && deployment.getSpec().getTemplate() != null &&
                                deployment.getSpec().getTemplate().getSpec() != null &&
                                deployment.getSpec().getTemplate().getSpec().getContainers() != null) {

                            resourceInfo = deployment.getSpec().getTemplate().getSpec().getContainers().stream()
                                    .map(container -> {
                                        String cpuRequest = container.getResources() != null && container.getResources().getRequests() != null ?
                                                String.valueOf(container.getResources().getRequests().get("cpu")) : "Not set";
                                        String memRequest = container.getResources() != null && container.getResources().getRequests() != null ?
                                                String.valueOf(container.getResources().getRequests().get("memory")) : "Not set";
                                        String image = container.getImage() != null ? container.getImage() : "No image";
                                        return "\n    " + container.getName() + ":\n" +
                                                "      Image: " + image + "\n" +
                                                "      CPU Request: " + cpuRequest + "\n" +
                                                "      Memory Request: " + memRequest;
                                    })
                                    .collect(Collectors.joining("\n"));
                        } else {
                            resourceInfo = "No container specs found";
                        }

                        return "Deployment: " + name + "\n" +
                                "  Status:\n" +
                                "    Replicas: " + availableReplicas + "/" + desiredReplicas + "\n" +
                                "    Strategy: " + strategy + "\n" +
                                "  Containers:" + resourceInfo + "\n";
                    })
                    .filter(item -> item != null)
                    .collect(Collectors.toList());

            if (deploymentList.isEmpty()) {
                return "No valid deployments found in namespace '" + ns + "'";
            } else {
                return "Found " + deploymentList.size() + " deployment(s) in namespace '" + ns + "':\n\n" +
                        String.join("\n", deploymentList);
            }
        } catch (Exception e) {
            return "Error listing deployments in namespace '" + (namespace != null ? namespace : defaultNamespace) + "': " + e.getMessage() + "\n" +
                    "Please ensure:\n" +
                    "1. You have a valid kubeconfig file\n" +
                    "2. The cluster is accessible\n" +
                    "3. You have permissions to list deployments in the '" + (namespace != null ? namespace : defaultNamespace) + "' namespace";
        }
    }

    @Tool(name = "describe_deployment", description = "Get detailed information about a specific deployment")
    public String describeDeployment(
            @ToolParam(description = "Name of the deployment to describe") String deploymentName,
            @ToolParam(description = "The Kubernetes namespace where the deployment is located") String namespace) {
        return describeDeployment(deploymentName, namespace, "default");
    }

    @Tool(name = "describe_deployment", description = "Get detailed information about a specific deployment")
    public String describeDeployment(
            @ToolParam(description = "Name of the deployment to describe") String deploymentName,
            @ToolParam(description = "The Kubernetes namespace where the deployment is located") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1Deployment deployment = appsV1Api.readNamespacedDeployment(deploymentName, ns, null);
            String selector = deployment.getSpec() != null && deployment.getSpec().getSelector() != null &&
                    deployment.getSpec().getSelector().getMatchLabels() != null ?
                    deployment.getSpec().getSelector().getMatchLabels().entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.joining(",")) : null;

            List<V1Pod> pods = coreV1Api.listNamespacedPod(ns, null, null, null, null, selector, null, null, null, null, null)
                    .getItems();

            StringBuilder sb = new StringBuilder();
            sb.append("Deployment: ").append(deployment.getMetadata() != null ? deployment.getMetadata().getName() : "").append("\n");
            sb.append("Namespace: ").append(deployment.getMetadata() != null ? deployment.getMetadata().getNamespace() : "").append("\n\n");

            sb.append("Spec:\n");
            sb.append("  Replicas: ").append(deployment.getSpec() != null ? deployment.getSpec().getReplicas() : null).append("\n");
            sb.append("  Strategy: ").append(deployment.getSpec() != null && deployment.getSpec().getStrategy() != null ?
                    deployment.getSpec().getStrategy().getType() : null).append("\n");
            sb.append("  Selector: ").append(deployment.getSpec() != null && deployment.getSpec().getSelector() != null &&
                    deployment.getSpec().getSelector().getMatchLabels() != null ?
                    deployment.getSpec().getSelector().getMatchLabels().entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.joining(", ")) : "").append("\n\n");

            sb.append("Template:\n");
            sb.append("  Labels: ").append(deployment.getSpec() != null && deployment.getSpec().getTemplate() != null &&
                    deployment.getSpec().getTemplate().getMetadata() != null &&
                    deployment.getSpec().getTemplate().getMetadata().getLabels() != null ?
                    deployment.getSpec().getTemplate().getMetadata().getLabels().entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.joining(", ")) : "").append("\n");
            sb.append("  Containers:\n");

            if (deployment.getSpec() != null && deployment.getSpec().getTemplate() != null &&
                    deployment.getSpec().getTemplate().getSpec() != null &&
                    deployment.getSpec().getTemplate().getSpec().getContainers() != null) {

                sb.append(deployment.getSpec().getTemplate().getSpec().getContainers().stream()
                        .map(container -> {
                            String ports = container.getPorts() != null ?
                                    container.getPorts().stream()
                                            .map(p -> p.getContainerPort() + "/" + p.getProtocol())
                                            .collect(Collectors.joining(", ")) : "";

                            String requests = container.getResources() != null && container.getResources().getRequests() != null ?
                                    container.getResources().getRequests().entrySet().stream()
                                            .map(e -> e.getKey() + ": " + e.getValue())
                                            .collect(Collectors.joining(", ")) : "";

                            String limits = container.getResources() != null && container.getResources().getLimits() != null ?
                                    container.getResources().getLimits().entrySet().stream()
                                            .map(e -> e.getKey() + ": " + e.getValue())
                                            .collect(Collectors.joining(", ")) : "";

                            return "    - " + container.getName() + ":\n" +
                                    "      Image: " + container.getImage() + "\n" +
                                    "      Ports: " + ports + "\n" +
                                    "      Resources:\n" +
                                    "        Requests: " + requests + "\n" +
                                    "        Limits: " + limits;
                        })
                        .collect(Collectors.joining("\n")));
            }

            sb.append("\n\nStatus:\n");
            sb.append("  Available Replicas: ").append(deployment.getStatus() != null ? deployment.getStatus().getAvailableReplicas() : null).append("\n");
            sb.append("  Ready Replicas: ").append(deployment.getStatus() != null ? deployment.getStatus().getReadyReplicas() : null).append("\n");
            sb.append("  Updated Replicas: ").append(deployment.getStatus() != null ? deployment.getStatus().getUpdatedReplicas() : null).append("\n");
            sb.append("  Conditions:\n");

            if (deployment.getStatus() != null && deployment.getStatus().getConditions() != null) {
                sb.append(deployment.getStatus().getConditions().stream()
                        .map(condition -> "    - " + condition.getType() + ": " + condition.getStatus() +
                                " (" + condition.getMessage() + ")")
                        .collect(Collectors.joining("\n")));
            }

            sb.append("\n\nPods:\n");
            sb.append(pods.stream()
                    .map(pod -> {
                        boolean ready = pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null ?
                                pod.getStatus().getContainerStatuses().stream().allMatch(V1ContainerStatus::getReady) : false;
                        int restarts = pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null ?
                                pod.getStatus().getContainerStatuses().stream()
                                        .mapToInt(status -> status.getRestartCount() != null ? status.getRestartCount() : 0)
                                        .sum() : 0;

                        return "    - " + pod.getMetadata().getName() + ":\n" +
                                "      Status: " + pod.getStatus().getPhase() + "\n" +
                                "      Ready: " + ready + "\n" +
                                "      Restarts: " + restarts;
                    })
                    .collect(Collectors.joining("\n")));

            return sb.toString();
        } catch (Exception e) {
            return "Error describing deployment '" + deploymentName + "' in namespace '" +
                    (namespace != null ? namespace : defaultNamespace) + "': " + e.getMessage() + "\n" +
                    "Please ensure:\n" +
                    "1. You have a valid kubeconfig file\n" +
                    "2. The cluster is accessible\n" +
                    "3. The deployment '" + deploymentName + "' exists in namespace '" +
                    (namespace != null ? namespace : defaultNamespace) + "'\n" +
                    "4. You have permissions to view deployments in this namespace";
        }
    }

    @Tool(name = "analyze_deployment", description = "Analyzes the health and status of a deployment")
    public String analyzeDeploymentHealth(
            @ToolParam(description = "Name of the deployment to analyze") String deploymentName,
            @ToolParam(description = "The Kubernetes namespace where the deployment is located") String namespace) {
        return analyzeDeploymentHealth(deploymentName, namespace, "default");
    }

    @Tool(name = "analyze_deployment", description = "Analyzes the health and status of a deployment")
    public String analyzeDeploymentHealth(
            @ToolParam(description = "Name of the deployment to analyze") String deploymentName,
            @ToolParam(description = "The Kubernetes namespace where the deployment is located") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1Deployment deployment = appsV1Api.readNamespacedDeployment(deploymentName, ns, null);
            String selector = deployment.getSpec() != null && deployment.getSpec().getSelector() != null &&
                    deployment.getSpec().getSelector().getMatchLabels() != null ?
                    deployment.getSpec().getSelector().getMatchLabels().entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.joining(",")) : null;

            List<V1Pod> pods = coreV1Api.listNamespacedPod(ns, null, null, null, null, selector, null, null, null, null, null)
                    .getItems();

            StringBuilder analysis = new StringBuilder();
            analysis.append("=== Deployment Health Analysis: ").append(deployment.getMetadata() != null ?
                    deployment.getMetadata().getName() : "").append(" ===\n\n");

            // Analyze replica status
            int desiredReplicas = deployment.getSpec() != null && deployment.getSpec().getReplicas() != null ?
                    deployment.getSpec().getReplicas() : 0;
            int availableReplicas = deployment.getStatus() != null && deployment.getStatus().getAvailableReplicas() != null ?
                    deployment.getStatus().getAvailableReplicas() : 0;
            int readyReplicas = deployment.getStatus() != null && deployment.getStatus().getReadyReplicas() != null ?
                    deployment.getStatus().getReadyReplicas() : 0;
            int updatedReplicas = deployment.getStatus() != null && deployment.getStatus().getUpdatedReplicas() != null ?
                    deployment.getStatus().getUpdatedReplicas() : 0;

            analysis.append("Replica Status:\n");
            analysis.append("- Desired: ").append(desiredReplicas).append("\n");
            analysis.append("- Available: ").append(availableReplicas).append("\n");
            analysis.append("- Ready: ").append(readyReplicas).append("\n");
            analysis.append("- Updated: ").append(updatedReplicas).append("\n\n");

            if (availableReplicas < desiredReplicas) {
                analysis.append("⚠️ Warning: Not all desired replicas are available\n");
                analysis.append("Recommendations:\n");
                analysis.append("1. Check pod events for scheduling issues\n");
                analysis.append("2. Verify resource quotas and limits\n");
                analysis.append("3. Check node capacity and availability\n\n");
            }

            // Analyze pod health
            List<V1Pod> unhealthyPods = pods.stream()
                    .filter(pod ->
                            pod.getStatus() == null ||
                                    !"Running".equals(pod.getStatus().getPhase()) ||
                                    (pod.getStatus().getContainerStatuses() != null &&
                                            pod.getStatus().getContainerStatuses().stream()
                                                    .anyMatch(status -> !status.getReady() || (status.getRestartCount() != null && status.getRestartCount() > 0))))
                    .collect(Collectors.toList());

            if (!unhealthyPods.isEmpty()) {
                analysis.append("Pod Health Issues:\n");
                unhealthyPods.forEach(pod -> {
                    analysis.append("Pod: ").append(pod.getMetadata() != null ? pod.getMetadata().getName() : "").append("\n");
                    analysis.append("- Phase: ").append(pod.getStatus() != null ? pod.getStatus().getPhase() : "").append("\n");
                    if (pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null) {
                        pod.getStatus().getContainerStatuses().forEach(status -> {
                            analysis.append("- Container: ").append(status.getName()).append("\n");
                            analysis.append("  Ready: ").append(status.getReady()).append("\n");
                            analysis.append("  Restart Count: ").append(status.getRestartCount()).append("\n");
                            if (status.getState() != null && status.getState().getWaiting() != null) {
                                analysis.append("  Waiting: ").append(status.getState().getWaiting().getReason())
                                        .append(" - ").append(status.getState().getWaiting().getMessage()).append("\n");
                            }
                        });
                    }
                    analysis.append("\n");
                });
            } else {
                analysis.append("✅ All pods are healthy\n\n");
            }

            // Analyze deployment conditions
            analysis.append("Deployment Conditions:\n");
            if (deployment.getStatus() != null && deployment.getStatus().getConditions() != null) {
                for (V1DeploymentCondition condition : deployment.getStatus().getConditions()) {
                    analysis.append("- ").append(condition.getType()).append(": ").append(condition.getStatus()).append("\n");
                    if (!"True".equals(condition.getStatus())) {
                        analysis.append("  Message: ").append(condition.getMessage()).append("\n");
                        analysis.append("  Last Update: ").append(condition.getLastUpdateTime()).append("\n");
                    }
                }
            }

            // Add recommendations based on analysis
            analysis.append("\nRecommendations:\n");
            if (!unhealthyPods.isEmpty()) {
                analysis.append("1. Check pod logs for application errors\n");
                analysis.append("2. Verify container resource limits and requests\n");
                analysis.append("3. Check for image pull issues\n");
                analysis.append("4. Review liveness and readiness probe configurations\n");
            }
            if (deployment.getStatus() != null && deployment.getStatus().getConditions() != null &&
                    deployment.getStatus().getConditions().stream()
                            .anyMatch(condition -> "Progressing".equals(condition.getType()) && !"True".equals(condition.getStatus()))) {
                analysis.append("5. Review deployment strategy and rollout status\n");
                analysis.append("6. Check for configuration or dependency issues\n");
            }

            return analysis.toString();
        } catch (Exception e) {
            return "Error analyzing deployment health: " + e.getMessage();
        }
    }
}