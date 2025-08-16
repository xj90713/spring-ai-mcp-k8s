package com.xiaoxj.tools;

import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HealthTools {

    private final CoreV1Api coreV1Api;
    private final AppsV1Api appsV1Api;

    public HealthTools(CoreV1Api coreV1Api, AppsV1Api appsV1Api) {
        this.coreV1Api = coreV1Api;
        this.appsV1Api = appsV1Api;
    }

    @Tool(name = "check_cluster_health", description = "Check overall cluster health")
    public String checkClusterHealth() {
        try {
            V1NodeList nodes = coreV1Api.listNode(null, null, null, null, null, null, null, null, null, null);
            V1PodList pods = coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null);
            V1DeploymentList deployments = appsV1Api.listDeploymentForAllNamespaces(null, null, null, null, null, null, null, null, null, null);

            // Node health analysis
            List<String> nodeHealth = nodes.getItems().stream()
                    .map(node -> {
                        List<V1NodeCondition> conditions = node.getStatus() != null ? node.getStatus().getConditions() : null;
                        boolean ready = conditions != null && conditions.stream()
                                .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));

                        List<String> problems = conditions != null ? conditions.stream()
                                .filter(c -> !"Ready".equals(c.getType()) && "True".equals(c.getStatus()))
                                .map(V1NodeCondition::getType)
                                .collect(Collectors.toList()) : new ArrayList<>();

                        String nodeName = node.getMetadata() != null ? node.getMetadata().getName() : "unknown";
                        return nodeName + ": " + (ready ? "Ready" : "Not Ready") +
                                (!problems.isEmpty() ? " (Issues: " + String.join(", ", problems) + ")" : "");
                    })
                    .collect(Collectors.toList());

            // Pod issues analysis
            List<String> podIssues = pods.getItems().stream()
                    .filter(pod -> pod.getStatus() != null &&
                            !"Running".equals(pod.getStatus().getPhase()) &&
                            !"Succeeded".equals(pod.getStatus().getPhase()))
                    .map(pod -> {
                        String namespace = pod.getMetadata() != null ? pod.getMetadata().getNamespace() : "unknown";
                        String name = pod.getMetadata() != null ? pod.getMetadata().getName() : "unknown";
                        String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : "unknown";
                        String reason = pod.getStatus() != null ? pod.getStatus().getReason() : null;
                        return namespace + "/" + name + ": " + phase + (reason != null ? " (" + reason + ")" : "");
                    })
                    .collect(Collectors.toList());

            // Deployment issues analysis
            List<String> deploymentIssues = deployments.getItems().stream()
                    .filter(deployment ->
                            (deployment.getStatus() != null && deployment.getStatus().getReadyReplicas() != null ?
                                    deployment.getStatus().getReadyReplicas() : 0) <
                                    (deployment.getStatus() != null && deployment.getStatus().getReplicas() != null ?
                                            deployment.getStatus().getReplicas() : 0))
                    .map(deployment -> {
                        String namespace = deployment.getMetadata() != null ? deployment.getMetadata().getNamespace() : "unknown";
                        String name = deployment.getMetadata() != null ? deployment.getMetadata().getName() : "unknown";
                        int readyReplicas = deployment.getStatus() != null && deployment.getStatus().getReadyReplicas() != null ?
                                deployment.getStatus().getReadyReplicas() : 0;
                        int replicas = deployment.getStatus() != null && deployment.getStatus().getReplicas() != null ?
                                deployment.getStatus().getReplicas() : 0;
                        return namespace + "/" + name + ": Ready: " + readyReplicas + "/" + replicas;
                    })
                    .collect(Collectors.toList());

            // Calculate summary metrics
            long readyNodes = nodes.getItems().stream()
                    .filter(node -> node.getStatus() != null &&
                            node.getStatus().getConditions() != null &&
                            node.getStatus().getConditions().stream()
                                    .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus())))
                    .count();

            long healthyPods = pods.getItems().stream()
                    .filter(pod -> pod.getStatus() != null &&
                            ("Running".equals(pod.getStatus().getPhase()) ||
                                    "Succeeded".equals(pod.getStatus().getPhase())))
                    .count();

            long healthyDeployments = deployments.getItems().stream()
                    .filter(deployment ->
                            (deployment.getStatus() != null && deployment.getStatus().getReadyReplicas() != null ?
                                    deployment.getStatus().getReadyReplicas() : 0) >=
                                    (deployment.getStatus() != null && deployment.getStatus().getReplicas() != null ?
                                            deployment.getStatus().getReplicas() : 0))
                    .count();

            // Build result string
            StringBuilder result = new StringBuilder();
            result.append("Cluster Health Check:\n\n");

            result.append("Nodes (").append(nodes.getItems().size()).append("):\n");
            nodeHealth.forEach(item -> result.append("  - ").append(item).append("\n"));

            result.append("\nPod Issues (").append(podIssues.size()).append("):\n");
            if (podIssues.isEmpty()) {
                result.append("  None\n");
            } else {
                podIssues.forEach(item -> result.append("  - ").append(item).append("\n"));
            }

            result.append("\nDeployment Issues (").append(deploymentIssues.size()).append("):\n");
            if (deploymentIssues.isEmpty()) {
                result.append("  None\n");
            } else {
                deploymentIssues.forEach(item -> result.append("  - ").append(item).append("\n"));
            }

            result.append("\nSummary:\n");
            result.append("  - Nodes: ").append(readyNodes).append("/").append(nodes.getItems().size()).append(" ready\n");
            result.append("  - Pods: ").append(healthyPods).append("/").append(pods.getItems().size()).append(" healthy\n");
            result.append("  - Deployments: ").append(healthyDeployments).append("/").append(deployments.getItems().size()).append(" healthy");

            return result.toString();
        } catch (Exception e) {
            return "Error checking cluster health: " + e.getMessage();
        }
    }

    @Tool(name = "get_failed_workloads", description = "List all failed pods/jobs in a namespace")
    public String getFailedWorkloads(
            @ToolParam(description = "The Kubernetes namespace to check for failed workloads") String namespace) {
        return getFailedWorkloads(namespace, "default");
    }

    @Tool(name = "get_failed_workloads", description = "List all failed pods/jobs in a namespace")
    public String getFailedWorkloads(
            @ToolParam(description = "The Kubernetes namespace to check for failed workloads") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1PodList pods = coreV1Api.listNamespacedPod(ns, null, null, null, null, null, null, null, null, null, null);

            List<V1Pod> failedPods = pods.getItems().stream()
                    .filter(pod -> {
                        if (pod.getStatus() == null) return false;

                        // Check if pod phase is Failed
                        if ("Failed".equals(pod.getStatus().getPhase())) {
                            return true;
                        }

                        // Check container statuses for issues
                        if (pod.getStatus().getContainerStatuses() != null) {
                            return pod.getStatus().getContainerStatuses().stream()
                                    .anyMatch(status -> {
                                        if (status.getState() == null) return false;

                                        // Check waiting state for known error reasons
                                        if (status.getState().getWaiting() != null) {
                                            String reason = status.getState().getWaiting().getReason();
                                            return reason != null && Arrays.asList("CrashLoopBackOff", "Error", "ImagePullBackOff").contains(reason);
                                        }

                                        // Check terminated state for non-zero exit code
                                        if (status.getState().getTerminated() != null) {
                                            return status.getState().getTerminated().getExitCode() != null &&
                                                    status.getState().getTerminated().getExitCode() != 0;
                                        }

                                        return false;
                                    });
                        }

                        return false;
                    })
                    .collect(Collectors.toList());

            if (failedPods.isEmpty()) {
                return "No failed workloads found in namespace " + ns;
            }

            StringBuilder result = new StringBuilder();
            result.append("Failed Workloads in namespace ").append(ns).append(":\n\n");

            for (V1Pod pod : failedPods) {
                String podName = pod.getMetadata() != null ? pod.getMetadata().getName() : "unknown";
                String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : "unknown";
                String reason = pod.getStatus() != null ? pod.getStatus().getReason() : "N/A";
                String message = pod.getStatus() != null ? pod.getStatus().getMessage() : "N/A";

                result.append("Pod: ").append(podName).append("\n");
                result.append("Phase: ").append(phase).append("\n");
                result.append("Reason: ").append(reason).append("\n");
                result.append("Message: ").append(message).append("\n\n");
                result.append("Container Status:\n");

                if (pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null) {
                    for (V1ContainerStatus container : pod.getStatus().getContainerStatuses()) {
                        result.append("- ").append(container.getName()).append(":\n");
                        result.append("  Ready: ").append(container.getReady()).append("\n");
                        result.append("  RestartCount: ").append(container.getRestartCount()).append("\n");

                        if (container.getState() != null) {
                            if (container.getState().getWaiting() != null) {
                                result.append("  Waiting: ").append(container.getState().getWaiting().getReason())
                                        .append(" (").append(container.getState().getWaiting().getMessage() != null ?
                                                container.getState().getWaiting().getMessage() : "N/A").append(")\n");
                            } else if (container.getState().getTerminated() != null) {
                                result.append("  Terminated: Exit ").append(container.getState().getTerminated().getExitCode())
                                        .append(" (").append(container.getState().getTerminated().getReason() != null ?
                                                container.getState().getTerminated().getReason() : "N/A").append(")\n");
                            } else {
                                result.append("  Running\n");
                            }
                        }
                        result.append("\n");
                    }
                } else {
                    result.append("No container status available\n");
                }
                result.append("\n");
            }

            return result.toString();
        } catch (Exception e) {
            return "Error getting failed workloads: " + e.getMessage();
        }
    }

    @Tool(name = "analyze_resource_bottlenecks", description = "Identify resource constraints in a namespace")
    public String analyzeResourceBottlenecks(
            @ToolParam(description = "The Kubernetes namespace to analyze for resource bottlenecks") String namespace) {
        return analyzeResourceBottlenecks(namespace, "default");
    }

    @Tool(name = "analyze_resource_bottlenecks", description = "Identify resource constraints in a namespace")
    public String analyzeResourceBottlenecks(
            @ToolParam(description = "The Kubernetes namespace to analyze for resource bottlenecks") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1PodList pods = coreV1Api.listNamespacedPod(ns, null, null, null, null, null, null, null, null, null, null);
            CoreV1EventList events = coreV1Api.listNamespacedEvent(ns, null, null, null, null, null, null, null, null, null, null);

            List<String> resourceIssues = new ArrayList<>();
            List<CoreV1Event> resourceEvents = new ArrayList<>();

            // Check for resource-related pod issues
            for (V1Pod pod : pods.getItems()) {
                String podName = pod.getMetadata() != null ? pod.getMetadata().getName() : "unknown";

                // Check container resource usage and limits
                if (pod.getSpec() != null && pod.getSpec().getContainers() != null) {
                    for (V1Container container : pod.getSpec().getContainers()) {
                        String containerName = container.getName();
                        V1ResourceRequirements resources = container.getResources();

                        if (resources == null || resources.getRequests() == null || resources.getLimits() == null) {
                            resourceIssues.add(podName + "/" + containerName + ": No resource requests/limits defined");
                        }
                    }
                }

                // Check for resource-related status conditions
                if (pod.getStatus() != null && pod.getStatus().getConditions() != null) {
                    for (V1PodCondition condition : pod.getStatus().getConditions()) {
                        if ("PodScheduled".equals(condition.getType()) &&
                                "False".equals(condition.getStatus()) &&
                                "Unschedulable".equals(condition.getReason()) &&
                                condition.getMessage() != null &&
                                condition.getMessage().contains("Insufficient")) {
                            resourceIssues.add(podName + ": " + condition.getMessage());
                        }
                    }
                }
            }

            // Check for resource-related events
            for (CoreV1Event event : events.getItems()) {
                if (event.getReason() != null &&
                        Arrays.asList("FailedScheduling", "OutOfmemory", "OutOfcpu", "BackOff").contains(event.getReason()) &&
                        event.getMessage() != null &&
                        event.getMessage().contains("Insufficient")) {
                    resourceEvents.add(event);
                }
            }

            // Build result string
            StringBuilder result = new StringBuilder();
            result.append("Resource Bottleneck Analysis for namespace ").append(ns).append(":\n\n");

            result.append("Resource Configuration Issues:\n");
            if (resourceIssues.isEmpty()) {
                result.append("None found\n");
            } else {
                resourceIssues.forEach(issue -> result.append("- ").append(issue).append("\n"));
            }

            result.append("\nRecent Resource-Related Events:\n");
            if (resourceEvents.isEmpty()) {
                result.append("None found\n");
            } else {
                for (CoreV1Event event : resourceEvents) {
                    result.append("- Time: ").append(event.getLastTimestamp()).append("\n");
                    result.append("  Resource: ").append(event.getInvolvedObject() != null ?
                                    event.getInvolvedObject().getKind() : "unknown").append("/")
                            .append(event.getInvolvedObject() != null ?
                                    event.getInvolvedObject().getName() : "unknown").append("\n");
                    result.append("  Issue: ").append(event.getReason()).append("\n");
                    result.append("  Message: ").append(event.getMessage()).append("\n\n");
                }
            }

            result.append("\nRecommendations:\n");
            if (resourceIssues.isEmpty() && resourceEvents.isEmpty()) {
                result.append("No immediate resource bottlenecks detected");
            } else {
                result.append("1. Review and adjust resource requests/limits for pods with issues\n");
                result.append("2. Consider increasing cluster capacity if resource constraints are frequent\n");
                result.append("3. Implement horizontal pod autoscaling for workloads with variable resource needs\n");
                result.append("4. Review pod scheduling and node affinity rules");
            }

            return result.toString();
        } catch (Exception e) {
            return "Error analyzing resource bottlenecks: " + e.getMessage();
        }
    }
}