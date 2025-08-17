package com.xiaoxj.tools;


import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Streams;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PodTools {

    private final CoreV1Api coreV1Api;
    private final AppsV1Api appsV1Api;

    public PodTools(CoreV1Api coreV1Api, AppsV1Api appsV1Api) {
        this.coreV1Api = coreV1Api;
        this.appsV1Api = appsV1Api;
    }

//    @Tool(name = "list_pods", description = "Lists all Kubernetes pods in the specified namespace")
//    public List<String> listPods(
//            @ToolParam(description = "The Kubernetes namespace to list pods from") String namespace) {
//        return listPods(namespace, "default");
//    }

    @Tool(name = "list_pods", description = "Lists all Kubernetes pods in the specified namespace")
    public List<String> listPods(
            @ToolParam(description = "The Kubernetes namespace to list pods from") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1PodList podList = coreV1Api.listNamespacedPod(ns, null, null, null, null, null, null, null, null, null, null);

            return podList.getItems().stream()
                    .map(pod -> {
                        String name = pod.getMetadata() != null ? pod.getMetadata().getName() : "unknown";
                        String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : "unknown";
                        boolean ready = pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null &&
                                pod.getStatus().getContainerStatuses().stream()
                                        .allMatch(V1ContainerStatus::getReady);
                        String ip = pod.getStatus() != null ? pod.getStatus().getPodIP() : "N/A";

                        return name + " (" + phase + ")" +
                                "\n  - Ready: " + ready +
                                "\n  - IP: " + ip;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

//    @Tool(name = "get_pod_logs", description = "Retrieves logs from a specific Kubernetes pod with error pattern detection")
//    public String getPodLogs(
//            @ToolParam(description = "Name of the pod to get logs from") String podName,
//            @ToolParam(description = "The Kubernetes namespace where the pod is located") String namespace) {
//        return getPodLogs(podName, namespace, "default", 100);
//    }

    @Tool(name = "get_pod_logs", description = "Retrieves logs from a specific Kubernetes pod with error pattern detection")
    public String getPodLogs(
            @ToolParam(description = "Name of the pod to get logs from") String podName,
            @ToolParam(description = "The Kubernetes namespace where the pod is located") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace,
            @ToolParam(description = "Number of lines to retrieve from the end of the logs") int tailLines) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            String logs = coreV1Api.readNamespacedPodLog(podName, ns, null, null, false, null, null, null, null, tailLines, null);

            // Analyze logs for common error patterns
            Map<String, String> errorPatterns = new HashMap<>();
            errorPatterns.put("OutOfMemoryError", "Memory issues detected");
            errorPatterns.put("Exception", "Application exceptions found");
            errorPatterns.put("Error", "General errors detected");
            errorPatterns.put("Failed to pull image", "Image pull issues");
            errorPatterns.put("Connection refused", "Network connectivity issues");
            errorPatterns.put("Permission denied", "Permission/RBAC issues");

            List<String> analysis = errorPatterns.entrySet().stream()
                    .filter(entry -> logs.toLowerCase().contains(entry.getKey().toLowerCase()))
                    .map(Map.Entry::getValue)
                    .distinct()
                    .collect(Collectors.toList());

            if (analysis.isEmpty()) {
                return logs;
            } else {
                return "Log Analysis:\n" +
                        analysis.stream().map(item -> "- " + item).collect(Collectors.joining("\n")) +
                        "\n\nLogs:\n" + logs;
            }
        } catch (Exception e) {
            return "Error retrieving logs: " + e.getMessage();
        }
    }

//    @Tool(name = "describe_pod", description = "Gets detailed information about a specific Kubernetes pod")
//    public String describePod(
//            @ToolParam(description = "Name of the pod to describe") String podName,
//            @ToolParam(description = "The Kubernetes namespace where the pod is located") String namespace) {
//        return describePod(podName, namespace, "default");
//    }

    @Tool(name = "describe_pod", description = "Gets detailed information about a specific Kubernetes pod")
    public String describePod(
            @ToolParam(description = "Name of the pod to describe") String podName,
            @ToolParam(description = "The Kubernetes namespace where the pod is located") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1Pod pod = coreV1Api.readNamespacedPod(podName, ns, null);

            StringBuilder sb = new StringBuilder();
            sb.append("Pod: ").append(pod.getMetadata() != null ? pod.getMetadata().getName() : "unknown").append("\n");
            sb.append("Namespace: ").append(pod.getMetadata() != null ? pod.getMetadata().getNamespace() : "unknown").append("\n");
            sb.append("Status: ").append(pod.getStatus() != null ? pod.getStatus().getPhase() : "unknown").append("\n");
            sb.append("IP: ").append(pod.getStatus() != null ? pod.getStatus().getPodIP() : "N/A").append("\n");
            sb.append("Node: ").append(pod.getSpec() != null ? pod.getSpec().getNodeName() : "N/A").append("\n");
            sb.append("Containers:\n");

            if (pod.getSpec() != null && pod.getSpec().getContainers() != null) {
                for (V1Container container : pod.getSpec().getContainers()) {
                    sb.append("  - ").append(container.getName()).append(":\n");
                    sb.append("    Image: ").append(container.getImage()).append("\n");

                    boolean ready = pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null &&
                            pod.getStatus().getContainerStatuses().stream()
                                    .filter(status -> container.getName().equals(status.getName()))
                                    .findFirst()
                                    .map(V1ContainerStatus::getReady)
                                    .orElse(false);

                    sb.append("    Ready: ").append(ready).append("\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error describing pod: " + e.getMessage();
        }
    }

//    @Tool(name = "diagnose_pods", description = "Analyzes problematic pods and provides troubleshooting recommendations")
//    public String analyzePodIssues(
//            @ToolParam(description = "The Kubernetes namespace to analyze pods from") String namespace) {
//        return analyzePodIssues(namespace, "default");
//    }

    @Tool(name = "diagnose_pods", description = "Analyzes problematic pods and provides troubleshooting recommendations")
    public String analyzePodIssues(
            @ToolParam(description = "The Kubernetes namespace to analyze pods from") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1PodList pods = coreV1Api.listNamespacedPod(ns, null, null, null, null, null, null, null, null, null, null);

            List<String> problematicPods = new ArrayList<>();
            List<String> recommendations = new ArrayList<>();

            for (V1Pod pod : pods.getItems()) {
                analyzePod(pod, problematicPods, recommendations);
            }

            return formatAnalysisResults(ns, problematicPods, recommendations);
        } catch (Exception e) {
            return "Error analyzing pods: " + e.getMessage();
        }
    }

    private void analyzePod(V1Pod pod, List<String> problematicPods, List<String> recommendations) {
        String podName = pod.getMetadata() != null ? pod.getMetadata().getName() : "unknown";
        List<V1ContainerStatus> containerStatuses = pod.getStatus() != null ?
                pod.getStatus().getContainerStatuses() : Collections.emptyList();

        if (pod.getStatus() == null) {
            handleUnknownPod(podName, problematicPods, recommendations);
            return;
        }

        switch (pod.getStatus().getPhase()) {
            case "Pending":
                handlePendingPod(pod, podName, problematicPods, recommendations);
                break;
            case "Failed":
                handleFailedPod(podName, problematicPods, recommendations);
                break;
            default:
                analyzeContainerStatuses(podName, containerStatuses, problematicPods, recommendations);
                checkResourceConstraints(pod, podName, recommendations);
                break;
        }
    }

    private void handlePendingPod(V1Pod pod, String podName, List<String> problematicPods, List<String> recommendations) {
        problematicPods.add(podName + " (Pending)");
        if (pod.getStatus() != null && pod.getStatus().getConditions() != null &&
                pod.getStatus().getConditions().stream()
                        .anyMatch(condition -> "PodScheduled".equals(condition.getType()) && "False".equals(condition.getStatus()))) {
            recommendations.add("Pod " + podName + ": Scheduling issues detected. Check node resources and pod affinity/anti-affinity rules.");
        } else {
            recommendations.add("Pod " + podName + ": Pod is pending. Check events for more details.");
        }
    }

    private void handleFailedPod(String podName, List<String> problematicPods, List<String> recommendations) {
        problematicPods.add(podName + " (Failed)");
        recommendations.add("Pod " + podName + ": Pod failed. Check logs using 'getPodLogs' for more details.");
    }

    private void handleUnknownPod(String podName, List<String> problematicPods, List<String> recommendations) {
        problematicPods.add(podName + " (Unknown Phase)");
        recommendations.add("Pod " + podName + ": Pod phase is unknown. This might indicate a cluster issue.");
    }

    private void analyzeContainerStatuses(String podName, List<V1ContainerStatus> containerStatuses,
                                          List<String> problematicPods, List<String> recommendations) {
        for (V1ContainerStatus status : containerStatuses) {
            String containerName = status.getName();
            checkRestartCount(status, podName, containerName, problematicPods, recommendations);
            checkContainerState(status, podName, containerName, problematicPods, recommendations);
            checkOOMKills(status, podName, containerName, problematicPods, recommendations);
        }
    }

    private void checkRestartCount(V1ContainerStatus status, String podName, String containerName,
                                   List<String> problematicPods, List<String> recommendations) {
        if (status.getRestartCount() > 3) {
            problematicPods.add(podName + " (CrashLooping - Container: " + containerName + ")");
            recommendations.add("Pod " + podName + ", Container " + containerName +
                    ": High restart count (" + status.getRestartCount() + "). Check logs and memory/CPU limits.");
        }
    }

    private void checkContainerState(V1ContainerStatus status, String podName, String containerName,
                                     List<String> problematicPods, List<String> recommendations) {
        if (status.getState() != null && status.getState().getWaiting() != null) {
            V1ContainerStateWaiting waiting = status.getState().getWaiting();
            switch (waiting.getReason()) {
                case "CrashLoopBackOff":
                    problematicPods.add(podName + " (CrashLoopBackOff - Container: " + containerName + ")");
                    recommendations.add("Pod " + podName + ", Container " + containerName +
                            ": Application repeatedly crashing. Check logs and application health.");
                    break;
                case "ImagePullBackOff":
                case "ErrImagePull":
                    problematicPods.add(podName + " (Image Pull Issue - Container: " + containerName + ")");
                    recommendations.add("Pod " + podName + ", Container " + containerName +
                            ": Image pull failed. Check image name, registry credentials, and network connectivity.");
                    break;
                case "CreateContainerError":
                    problematicPods.add(podName + " (Container Creation Failed - Container: " + containerName + ")");
                    recommendations.add("Pod " + podName + ", Container " + containerName +
                            ": Container creation failed. Check container configuration and volume mounts.");
                    break;
                default:
                    if (waiting.getReason() != null) {
                        problematicPods.add(podName + " (" + waiting.getReason() + " - Container: " + containerName + ")");
                        recommendations.add("Pod " + podName + ", Container " + containerName +
                                ": Container in waiting state: " + waiting.getReason() +
                                ". Message: " + (waiting.getMessage() != null ? waiting.getMessage() : "Unknown"));
                    }
                    break;
            }
        }
    }

    private void checkOOMKills(V1ContainerStatus status, String podName, String containerName,
                               List<String> problematicPods, List<String> recommendations) {
        if (status.getLastState() != null && status.getLastState().getTerminated() != null &&
                "OOMKilled".equals(status.getLastState().getTerminated().getReason())) {
            problematicPods.add(podName + " (OOM Killed - Container: " + containerName + ")");
            recommendations.add("Pod " + podName + ", Container " + containerName +
                    ": Out of memory. Consider increasing memory limits or investigating memory leaks.");
        }
    }

    private void checkResourceConstraints(V1Pod pod, String podName, List<String> recommendations) {
        if (pod.getSpec() != null && pod.getSpec().getContainers() != null) {
            for (V1Container container : pod.getSpec().getContainers()) {
                V1ResourceRequirements resources = container.getResources();
                if (resources == null || (resources.getLimits() == null && resources.getRequests() == null)) {
                    recommendations.add("Pod " + podName + ", Container " + container.getName() +
                            ": No resource limits/requests set. Consider adding them for better resource management.");
                }
            }
        }
    }

    private String formatAnalysisResults(String namespace, List<String> problematicPods, List<String> recommendations) {
        if (problematicPods.isEmpty()) {
            return "No problematic pods found in namespace " + namespace;
        } else {
            return "Problematic Pods in namespace " + namespace + ":\n" +
                    problematicPods.stream().map(pod -> "- " + pod).collect(Collectors.joining("\n")) +
                    "\n\nRecommendations:\n" +
                    recommendations.stream().map(rec -> "- " + rec).collect(Collectors.joining("\n"));
        }
    }

//    @Tool(name = "get_pod_metrics", description = "Get resource usage metrics for a specific pod")
//    public String getPodMetrics(
//            @ToolParam(description = "Name of the pod to get metrics for") String podName,
//            @ToolParam(description = "The Kubernetes namespace where the pod is located") String namespace) {
//        return getPodMetrics(podName, namespace, "default");
//    }

    @Tool(name = "get_pod_metrics", description = "Get resource usage metrics for a specific pod")
    public String getPodMetrics(
            @ToolParam(description = "Name of the pod to get metrics for") String podName,
            @ToolParam(description = "The Kubernetes namespace where the pod is located") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1Pod pod = coreV1Api.readNamespacedPod(podName, ns, null);

            List<String> containerMetrics = new ArrayList<>();
            if (pod.getSpec() != null && pod.getSpec().getContainers() != null) {
                for (V1Container container : pod.getSpec().getContainers()) {
                    V1ResourceRequirements resources = container.getResources();
                    Map<String, Quantity> requests = resources != null ? resources.getRequests() : null;
                    Map<String, Quantity> limits = resources != null ? resources.getLimits() : null;

                    V1ContainerStatus containerStatus = pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null ?
                            pod.getStatus().getContainerStatuses().stream()
                                    .filter(status -> container.getName().equals(status.getName()))
                                    .findFirst()
                                    .orElse(null) : null;

                    String state = "Unknown";
                    if (containerStatus != null && containerStatus.getState() != null) {
                        if (containerStatus.getState().getRunning() != null) {
                            state = "Running since " + containerStatus.getState().getRunning().getStartedAt();
                        } else if (containerStatus.getState().getWaiting() != null) {
                            state = "Waiting (" + containerStatus.getState().getWaiting().getReason() + ")";
                        } else if (containerStatus.getState().getTerminated() != null) {
                            state = "Terminated (" + containerStatus.getState().getTerminated().getReason() + ")";
                        }
                    }

                    containerMetrics.add(
                            "Container: " + container.getName() + "\n" +
                                    "Resource Requests:\n" +
                                    "  CPU: " + (requests != null ? requests.get("cpu") : "Not set") + "\n" +
                                    "  Memory: " + (requests != null ? requests.get("memory") : "Not set") + "\n" +
                                    "Resource Limits:\n" +
                                    "  CPU: " + (limits != null ? limits.get("cpu") : "Not set") + "\n" +
                                    "  Memory: " + (limits != null ? limits.get("memory") : "Not set") + "\n" +
                                    "Status:\n" +
                                    "  Ready: " + (containerStatus != null ? containerStatus.getReady() : false) + "\n" +
                                    "  Restarts: " + (containerStatus != null ? containerStatus.getRestartCount() : 0) + "\n" +
                                    "  State: " + state
                    );
                }
            }

            return "Pod Metrics for " + podName + ":\n" +
                    "Node: " + (pod.getSpec() != null ? pod.getSpec().getNodeName() : "Not scheduled") + "\n" +
                    "Phase: " + (pod.getStatus() != null ? pod.getStatus().getPhase() : "unknown") + "\n" +
                    "Start Time: " + (pod.getStatus() != null ? pod.getStatus().getStartTime() : "unknown") + "\n\n" +
                    "Container Metrics:\n" +
                    String.join("\n\n", containerMetrics);
        } catch (Exception e) {
            return "Error getting pod metrics: " + e.getMessage();
        }
    }

//    @Tool(name = "exec_in_pod", description = "Execute a command in a pod container")
//    public String execInPod(
//            @ToolParam(description = "Name of the pod to execute command in") String podName,
//            @ToolParam(description = "The Kubernetes namespace where the pod is located") String namespace,
//            @ToolParam(description = "Command to execute") String command) {
//        return execInPod(podName, namespace, "default", command);
//    }

    @Tool(name = "exec_in_pod", description = "Execute a command in a pod container")
    public String execInPod(
            @ToolParam(description = "Name of the pod to execute command in") String podName,
            @ToolParam(description = "The Kubernetes namespace where the pod is located") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace,
            @ToolParam(description = "Command to execute") String command) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1Pod pod = coreV1Api.readNamespacedPod(podName, ns, null);

            if (pod.getStatus() == null || !"Running".equals(pod.getStatus().getPhase())) {
                return "Cannot execute command: Pod is not running (current phase: " +
                        (pod.getStatus() != null ? pod.getStatus().getPhase() : "unknown") + ")";
            }

            Process process = new ProcessBuilder()
                    .command("kubectl", "exec", "-n", ns, podName, "--", "/bin/sh", "-c", command)
                    .start();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Streams.copy(process.getInputStream(), output);
            ByteArrayOutputStream error = new ByteArrayOutputStream();
            Streams.copy(process.getErrorStream(), error);

            process.waitFor();

            String outputStr = output.toString();
            String errorStr = error.toString();

            if (process.exitValue() != 0) {
                return "Command failed with exit code " + process.exitValue() + "\nError: " + errorStr;
            } else {
                return "Command output:\n" + outputStr;
            }
        } catch (Exception e) {
            return "Error executing command in pod: " + e.getMessage();
        }
    }
}
