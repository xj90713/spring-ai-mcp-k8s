package com.xiaoxj.tools;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ResourceManagementTools {

    private final CoreV1Api coreV1Api;

    public ResourceManagementTools(CoreV1Api coreV1Api) {
        this.coreV1Api = coreV1Api;
    }

//    @Tool(name = "get_namespace_resource_quotas", description = "Get resource quotas for a namespace")
//    public String getNamespaceResourceQuotas(
//            @ToolParam(description = "The Kubernetes namespace to get resource quotas from") String namespace) {
//        return getNamespaceResourceQuotas(namespace, "default");
//    }

    @Tool(name = "get_namespace_resource_quotas", description = "Get resource quotas for a namespace")
    public String getNamespaceResourceQuotas(
            @ToolParam(description = "The Kubernetes namespace to get resource quotas from") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1ResourceQuotaList quotas = coreV1Api.listNamespacedResourceQuota(ns, null, null, null, null, null, null, null, null, null, null);

            if (quotas.getItems().isEmpty()) {
                return "No resource quotas found in namespace " + ns;
            }

            return quotas.getItems().stream()
                    .map(quota -> {
                        String name = quota.getMetadata() != null ? quota.getMetadata().getName() : "unknown";

                        String hardLimits = "  No hard limits defined";
                        if (quota.getStatus() != null && quota.getStatus().getHard() != null) {
                            hardLimits = quota.getStatus().getHard().entrySet().stream()
                                    .map(entry -> "  " + entry.getKey() + ": " + entry.getValue())
                                    .collect(Collectors.joining("\n"));
                        }

                        String usage = "  No usage data";
                        if (quota.getStatus() != null && quota.getStatus().getUsed() != null) {
                            usage = quota.getStatus().getUsed().entrySet().stream()
                                    .map(entry -> "  " + entry.getKey() + ": " + entry.getValue())
                                    .collect(Collectors.joining("\n"));
                        }

                        return "ResourceQuota: " + name + "\n" +
                                "Status:\n" + hardLimits + "\n\n" +
                                "Usage:\n" + usage;
                    })
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            return "Error getting resource quotas: " + e.getMessage();
        }
    }

//    @Tool(name = "describe_limit_range", description = "Get limit range details for a namespace")
//    public String describeLimitRange(
//            @ToolParam(description = "Name of the LimitRange to describe") String name,
//            @ToolParam(description = "The Kubernetes namespace where the LimitRange is located") String namespace) {
//        return describeLimitRange(name, namespace, "default");
//    }

    @Tool(name = "describe_limit_range", description = "Get limit range details for a namespace")
    public String describeLimitRange(
            @ToolParam(description = "Name of the LimitRange to describe") String name,
            @ToolParam(description = "The Kubernetes namespace where the LimitRange is located") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1LimitRange limitRange = coreV1Api.readNamespacedLimitRange(name, ns, null);

            StringBuilder sb = new StringBuilder();
            sb.append("LimitRange: ").append(limitRange.getMetadata() != null ? limitRange.getMetadata().getName() : "unknown").append("\n");
            sb.append("Namespace: ").append(ns).append("\n\n");
            sb.append("Limits:\n");

            if (limitRange.getSpec() != null && limitRange.getSpec().getLimits() != null) {
                for (V1LimitRangeItem limit : limitRange.getSpec().getLimits()) {
                    sb.append("Type: ").append(limit.getType()).append("\n");

                    sb.append("Default:\n");
                    if (limit.getDefault() != null) {
                        sb.append(limit.getDefault().entrySet().stream()
                                .map(entry -> "  " + entry.getKey() + ": " + entry.getValue())
                                .collect(Collectors.joining("\n")));
                    } else {
                        sb.append("  No defaults defined");
                    }
                    sb.append("\n\n");

                    sb.append("DefaultRequest:\n");
                    if (limit.getDefaultRequest() != null) {
                        sb.append(limit.getDefaultRequest().entrySet().stream()
                                .map(entry -> "  " + entry.getKey() + ": " + entry.getValue())
                                .collect(Collectors.joining("\n")));
                    } else {
                        sb.append("  No default requests defined");
                    }
                    sb.append("\n\n");

                    sb.append("Max:\n");
                    if (limit.getMax() != null) {
                        sb.append(limit.getMax().entrySet().stream()
                                .map(entry -> "  " + entry.getKey() + ": " + entry.getValue())
                                .collect(Collectors.joining("\n")));
                    } else {
                        sb.append("  No max limits defined");
                    }
                    sb.append("\n\n");

                    sb.append("Min:\n");
                    if (limit.getMin() != null) {
                        sb.append(limit.getMin().entrySet().stream()
                                .map(entry -> "  " + entry.getKey() + ": " + entry.getValue())
                                .collect(Collectors.joining("\n")));
                    } else {
                        sb.append("  No min limits defined");
                    }
                    sb.append("\n\n");
                }
            } else {
                sb.append("No limits defined");
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error describing LimitRange: " + e.getMessage();
        }
    }

    @Tool(name = "get_cluster_resource_usage", description = "Get overall cluster resource utilization")
    public String getClusterResourceUsage() {
        try {
            V1NodeList nodes = coreV1Api.listNode(null, null, null, null, null, null, null, null, null, null);
            V1PodList pods = coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null);

            double totalCPU = 0.0;
            double totalMemory = 0.0;
            double usedCPU = 0.0;
            double usedMemory = 0.0;

            for (V1Node node : nodes.getItems()) {
                if (node.getStatus() != null && node.getStatus().getCapacity() != null) {
                    Map<String, Quantity> capacity = node.getStatus().getCapacity();
                    if (capacity.get("cpu") != null) {
                        totalCPU += parseResourceQuantity(String.valueOf(capacity.get("cpu")));
                    }
                    if (capacity.get("memory") != null) {
                        totalMemory += parseResourceQuantity(String.valueOf(capacity.get("memory")));
                    }
                }
            }

            for (V1Pod pod : pods.getItems()) {
                if (pod.getSpec() != null && pod.getSpec().getContainers() != null) {
                    for (V1Container container : pod.getSpec().getContainers()) {
                        if (container.getResources() != null && container.getResources().getRequests() != null) {
                            Map<String, Quantity> requests = container.getResources().getRequests();
                            if (requests.get("cpu") != null) {
                                usedCPU += parseResourceQuantity(String.valueOf(requests.get("cpu")));
                            }
                            if (requests.get("memory") != null) {
                                usedMemory += parseResourceQuantity(String.valueOf(requests.get("memory")));
                            }
                        }
                    }
                }
            }

            double cpuUsagePercent = (totalCPU > 0) ? (usedCPU / totalCPU) * 100 : 0;
            double memoryUsagePercent = (totalMemory > 0) ? (usedMemory / totalMemory) * 100 : 0;

            return "Cluster Resource Usage:\n\n" +
                    "CPU:\n" +
                    "  Total: " + totalCPU + "\n" +
                    "  Used: " + usedCPU + "\n" +
                    "  Usage: " + cpuUsagePercent + "%\n\n" +
                    "Memory:\n" +
                    "  Total: " + totalMemory + "\n" +
                    "  Used: " + usedMemory + "\n" +
                    "  Usage: " + memoryUsagePercent + "%\n\n" +
                    "Nodes: " + nodes.getItems().size() + "\n" +
                    "Pods: " + pods.getItems().size();
        } catch (Exception e) {
            return "Error getting cluster resource usage: " + e.getMessage();
        }
    }

//    @Tool(name = "get_namespace_resource_usage", description = "Get resource usage for a specific namespace")
//    public String getNamespaceResourceUsage(
//            @ToolParam(description = "The Kubernetes namespace to get resource usage from") String namespace) {
//        return getNamespaceResourceUsage(namespace, "default");
//    }

    @Tool(name = "get_namespace_resource_usage", description = "Get resource usage for a specific namespace")
    public String getNamespaceResourceUsage(
            @ToolParam(description = "The Kubernetes namespace to get resource usage from") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1PodList pods = coreV1Api.listNamespacedPod(ns, null, null, null, null, null, null, null, null, null, null);

            double cpuRequests = 0.0;
            double memoryRequests = 0.0;
            double cpuLimits = 0.0;
            double memoryLimits = 0.0;

            for (V1Pod pod : pods.getItems()) {
                if (pod.getSpec() != null && pod.getSpec().getContainers() != null) {
                    for (V1Container container : pod.getSpec().getContainers()) {
                        if (container.getResources() != null) {
                            if (container.getResources().getRequests() != null) {
                                Map<String, Quantity> requests = container.getResources().getRequests();
                                if (requests.get("cpu") != null) {
                                    cpuRequests += parseResourceQuantity(String.valueOf(requests.get("cpu")));
                                }
                                if (requests.get("memory") != null) {
                                    memoryRequests += parseResourceQuantity(String.valueOf(requests.get("memory")));
                                }
                            }
                            if (container.getResources().getLimits() != null) {
                                Map<String, Quantity> limits = container.getResources().getLimits();
                                if (limits.get("cpu") != null) {
                                    cpuLimits += parseResourceQuantity(String.valueOf(limits.get("cpu")));
                                }
                                if (limits.get("memory") != null) {
                                    memoryLimits += parseResourceQuantity(String.valueOf(limits.get("memory")));
                                }
                            }
                        }
                    }
                }
            }

            long runningPods = pods.getItems().stream()
                    .filter(pod -> pod.getStatus() != null && "Running".equals(pod.getStatus().getPhase()))
                    .count();

            long pendingPods = pods.getItems().stream()
                    .filter(pod -> pod.getStatus() != null && "Pending".equals(pod.getStatus().getPhase()))
                    .count();

            long failedPods = pods.getItems().stream()
                    .filter(pod -> pod.getStatus() != null && "Failed".equals(pod.getStatus().getPhase()))
                    .count();

            return "Namespace Resource Usage: " + ns + "\n\n" +
                    "CPU:\n" +
                    "  Requests: " + cpuRequests + "\n" +
                    "  Limits: " + cpuLimits + "\n\n" +
                    "Memory:\n" +
                    "  Requests: " + memoryRequests + "\n" +
                    "  Limits: " + memoryLimits + "\n\n" +
                    "Total Pods: " + pods.getItems().size() + "\n" +
                    "Running Pods: " + runningPods + "\n" +
                    "Pending Pods: " + pendingPods + "\n" +
                    "Failed Pods: " + failedPods;
        } catch (Exception e) {
            return "Error getting namespace resource usage: " + e.getMessage();
        }
    }

    private double parseResourceQuantity(String quantity) {
        // Simplified parsing - in a real implementation, you'd need to handle all Kubernetes quantity formats
        if (quantity.endsWith("m")) {
            return Double.parseDouble(quantity.substring(0, quantity.length() - 1)) / 1000;
        }
        return Double.parseDouble(quantity.replaceAll("[^0-9.]", ""));
    }
}