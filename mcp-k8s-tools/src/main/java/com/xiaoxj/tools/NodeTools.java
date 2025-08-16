package com.xiaoxj.tools;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NodeTools {

    private final CoreV1Api coreV1Api;

    public NodeTools(CoreV1Api coreV1Api) {
        this.coreV1Api = coreV1Api;
    }

    @Tool(name = "list_nodes", description = "Lists all Kubernetes nodes in the cluster")
    public List<String> listNodes() {
        try {
            V1NodeList nodeList = coreV1Api.listNode(null, null, null, null, null, null, null, null, null, null);
            return nodeList.getItems().stream()
                    .map(node -> {
                        String name = node.getMetadata() != null ? node.getMetadata().getName() : "unknown";
                        boolean ready = node.getStatus() != null && node.getStatus().getConditions() != null &&
                                node.getStatus().getConditions().stream()
                                        .anyMatch(condition -> "Ready".equals(condition.getType()) && "True".equals(condition.getStatus()));

                        List<String> roles = node.getMetadata() != null && node.getMetadata().getLabels() != null ?
                                node.getMetadata().getLabels().entrySet().stream()
                                        .filter(entry -> entry.getKey().startsWith("node-role.kubernetes.io/"))
                                        .map(entry -> entry.getKey().substring("node-role.kubernetes.io/".length()))
                                        .collect(Collectors.toList()) : Collections.singletonList("none");

                        String internalIp = node.getStatus() != null && node.getStatus().getAddresses() != null ?
                                node.getStatus().getAddresses().stream()
                                        .filter(address -> "InternalIP".equals(address.getType()))
                                        .map(V1NodeAddress::getAddress)
                                        .findFirst().orElse("N/A") : "N/A";

                        String osImage = node.getStatus() != null && node.getStatus().getNodeInfo() != null ?
                                node.getStatus().getNodeInfo().getOsImage() : "N/A";

                        String kubeVersion = node.getStatus() != null && node.getStatus().getNodeInfo() != null ?
                                node.getStatus().getNodeInfo().getKubeletVersion() : "N/A";

                        return name + "\n  - Ready: " + ready +
                                "\n  - Roles: " + String.join(", ", roles) +
                                "\n  - Internal IP: " + internalIp +
                                "\n  - OS Image: " + osImage +
                                "\n  - Kubernetes Version: " + kubeVersion;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Tool(name = "describe_node", description = "Get detailed information about a specific node")
    public String describeNode(
            @ToolParam(description = "Name of the node to describe") String nodeName) {
        try {
            V1Node node = coreV1Api.readNode(nodeName, null);

            StringBuilder sb = new StringBuilder();
            sb.append("Node: ").append(node.getMetadata() != null ? node.getMetadata().getName() : "unknown").append("\n");

            sb.append("Labels: ");
            if (node.getMetadata() != null && node.getMetadata().getLabels() != null) {
                sb.append(node.getMetadata().getLabels().entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining(", ")));
            }
            sb.append("\n\n");

            sb.append("Status:\n");
            sb.append("  Conditions:\n");
            if (node.getStatus() != null && node.getStatus().getConditions() != null) {
                for (V1NodeCondition condition : node.getStatus().getConditions()) {
                    sb.append("    - ").append(condition.getType()).append(": ").append(condition.getStatus())
                            .append(" (").append(condition.getMessage()).append(")\n");
                }
            }

            sb.append("\n  Capacity:\n");
            if (node.getStatus() != null && node.getStatus().getCapacity() != null) {
                for (Map.Entry<String, Quantity> entry : node.getStatus().getCapacity().entrySet()) {
                    sb.append("    - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            }

            sb.append("\n  Allocatable:\n");
            if (node.getStatus() != null && node.getStatus().getAllocatable() != null) {
                for (Map.Entry<String, Quantity> entry : node.getStatus().getAllocatable().entrySet()) {
                    sb.append("    - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            }

            sb.append("\n  System Info:\n");
            if (node.getStatus() != null && node.getStatus().getNodeInfo() != null) {
                V1NodeSystemInfo info = node.getStatus().getNodeInfo();
                sb.append("    OS Image: ").append(info.getOsImage()).append("\n");
                sb.append("    Container Runtime: ").append(info.getContainerRuntimeVersion()).append("\n");
                sb.append("    Kubelet Version: ").append(info.getKubeletVersion()).append("\n");
                sb.append("    Kube-Proxy Version: ").append(info.getKubeProxyVersion()).append("\n");
                sb.append("    Operating System: ").append(info.getOperatingSystem()).append("\n");
                sb.append("    Architecture: ").append(info.getArchitecture()).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error describing node: " + e.getMessage();
        }
    }

    @Tool(name = "get_node_metrics", description = "Get resource usage metrics for a specific node")
    public String getNodeMetrics(
            @ToolParam(description = "Name of the node to get metrics for") String nodeName) {
        try {
            V1Node node = coreV1Api.readNode(nodeName, null);
            Map<String, Quantity> allocatable = node.getStatus() != null ? node.getStatus().getAllocatable() : null;
            Map<String, Quantity> capacity = node.getStatus() != null ? node.getStatus().getCapacity() : null;

            V1PodList podList = coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null);
            List<V1Pod> nodePods = podList.getItems().stream()
                    .filter(pod -> pod.getSpec() != null && nodeName.equals(pod.getSpec().getNodeName()))
                    .collect(Collectors.toList());

            double usedCPU = nodePods.stream()
                    .mapToDouble(pod -> {
                        if (pod.getSpec() == null || pod.getSpec().getContainers() == null) return 0.0;
                        return pod.getSpec().getContainers().stream()
                                .mapToDouble(container -> {
                                    if (container.getResources() == null || container.getResources().getRequests() == null) return 0.0;
                                    String cpu = String.valueOf(container.getResources().getRequests().get("cpu"));
                                    return cpu != null ? parseResourceQuantity(cpu) : 0.0;
                                })
                                .sum();
                    })
                    .sum();

            double usedMemory = nodePods.stream()
                    .mapToDouble(pod -> {
                        if (pod.getSpec() == null || pod.getSpec().getContainers() == null) return 0.0;
                        return pod.getSpec().getContainers().stream()
                                .mapToDouble(container -> {
                                    if (container.getResources() == null || container.getResources().getRequests() == null) return 0.0;
                                    String memory = String.valueOf(container.getResources().getRequests().get("memory"));
                                    return memory != null ? parseResourceQuantity(memory) / 1024 / 1024 / 1024 : 0.0; // Convert to GB
                                })
                                .sum();
                    })
                    .sum();

            long runningPods = nodePods.stream()
                    .filter(pod -> pod.getStatus() != null && "Running".equals(pod.getStatus().getPhase()))
                    .count();

            StringBuilder sb = new StringBuilder();
            sb.append("Node: ").append(node.getMetadata() != null ? node.getMetadata().getName() : "unknown").append("\n\n");

            sb.append("Capacity:\n");
            sb.append("  CPU: ").append(capacity != null ? capacity.get("cpu") : "N/A").append("\n");
            sb.append("  Memory: ").append(capacity != null ? capacity.get("memory") : "N/A").append("\n");
            sb.append("  Pods: ").append(capacity != null ? capacity.get("pods") : "N/A").append("\n");
            sb.append("  Ephemeral Storage: ").append(capacity != null ? capacity.get("ephemeral-storage") : "N/A").append("\n\n");

            sb.append("Allocatable:\n");
            sb.append("  CPU: ").append(allocatable != null ? allocatable.get("cpu") : "N/A").append("\n");
            sb.append("  Memory: ").append(allocatable != null ? allocatable.get("memory") : "N/A").append("\n");
            sb.append("  Pods: ").append(allocatable != null ? allocatable.get("pods") : "N/A").append("\n");
            sb.append("  Ephemeral Storage: ").append(allocatable != null ? allocatable.get("ephemeral-storage") : "N/A").append("\n\n");

            sb.append("Current Usage:\n");
            sb.append("  CPU: ").append(usedCPU).append("\n");
            sb.append("  Memory: ").append(usedMemory).append("GB\n");
            sb.append("  Running Pods: ").append(runningPods).append("\n\n");

            sb.append("Conditions:\n");
            if (node.getStatus() != null && node.getStatus().getConditions() != null) {
                for (V1NodeCondition condition : node.getStatus().getConditions()) {
                    sb.append("  - ").append(condition.getType()).append(": ").append(condition.getStatus())
                            .append(" (Last update: ").append(condition.getLastTransitionTime()).append(")\n");
                }
            } else {
                sb.append("  No conditions available\n");
            }

            sb.append("\nSystem Info:\n");
            if (node.getStatus() != null && node.getStatus().getNodeInfo() != null) {
                V1NodeSystemInfo info = node.getStatus().getNodeInfo();
                sb.append("  OS Image: ").append(info.getOsImage() != null ? info.getOsImage() : "N/A").append("\n");
                sb.append("  Container Runtime: ").append(info.getContainerRuntimeVersion() != null ? info.getContainerRuntimeVersion() : "N/A").append("\n");
                sb.append("  Kubelet Version: ").append(info.getKubeletVersion() != null ? info.getKubeletVersion() : "N/A").append("\n");
                sb.append("  Kernel Version: ").append(info.getKernelVersion() != null ? info.getKernelVersion() : "N/A").append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error getting node metrics: " + e.getMessage();
        }
    }

    @Tool(name = "get_node_events", description = "Get recent events for a specific node")
    public String getNodeEvents(
            @ToolParam(description = "Name of the node to get events for") String nodeName) {
        try {
            CoreV1EventList eventList = coreV1Api.listEventForAllNamespaces(null, null, null, null, null, null, null, null, null, null);
            OffsetDateTime now = OffsetDateTime.now();

            List<CoreV1Event> nodeEvents = eventList.getItems().stream()
                    .filter(event -> event.getInvolvedObject() != null &&
                            "Node".equals(event.getInvolvedObject().getKind()) &&
                            nodeName.equals(event.getInvolvedObject().getName()) &&
                            (event.getLastTimestamp() == null ||
                                    ChronoUnit.HOURS.between(event.getLastTimestamp(), now) <= 24))
                    .sorted((e1, e2) -> {
                        if (e1.getLastTimestamp() == null && e2.getLastTimestamp() == null) return 0;
                        if (e1.getLastTimestamp() == null) return 1;
                        if (e2.getLastTimestamp() == null) return -1;
                        return e2.getLastTimestamp().compareTo(e1.getLastTimestamp());
                    })
                    .collect(Collectors.toList());

            if (nodeEvents.isEmpty()) {
                return "No events found for node " + nodeName + " in the last 24 hours";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Recent events for node ").append(nodeName).append(":\n\n");

            for (CoreV1Event event : nodeEvents) {
                sb.append("Time: ").append(event.getLastTimestamp()).append("\n");
                sb.append("Type: ").append(event.getType()).append("\n");
                sb.append("Reason: ").append(event.getReason()).append("\n");
                sb.append("Message: ").append(event.getMessage()).append("\n");
                sb.append("Count: ").append(event.getCount() != null ? event.getCount() : 1).append("\n");
                sb.append("Component: ").append(event.getSource() != null ? event.getSource().getComponent() : "N/A").append("\n\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error getting node events: " + e.getMessage();
        }
    }

    @Tool(name = "drain_node", description = "Mark a node as unschedulable and evict pods for maintenance")
    public String drainNode(
            @ToolParam(description = "Name of the node to drain") String nodeName) {
        try {
            // Mark node as unschedulable
            V1Node node = coreV1Api.readNode(nodeName, null);
            node.getSpec().setUnschedulable(true);
            coreV1Api.replaceNode(nodeName, node, null, null, null, null);

            // Get pods on the node
            V1PodList podList = coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null);
            List<V1Pod> nodePods = podList.getItems().stream()
                    .filter(pod -> pod.getSpec() != null && nodeName.equals(pod.getSpec().getNodeName()))
                    .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append("Node ").append(nodeName).append(" marked as unschedulable.\n");
            sb.append("Found ").append(nodePods.size()).append(" pods to evict.\n\n");
            sb.append("Note: Manual pod eviction required. Please use kubectl to evict pods:\n");

            for (V1Pod pod : nodePods) {
                sb.append("kubectl delete pod ")
                        .append(pod.getMetadata() != null ? pod.getMetadata().getName() : "unknown")
                        .append(" -n ")
                        .append(pod.getMetadata() != null ? pod.getMetadata().getNamespace() : "default")
                        .append("\n");
            }

            sb.append("\nTo make the node schedulable again, run:\n");
            sb.append("kubectl uncordon ").append(nodeName);

            return sb.toString();
        } catch (Exception e) {
            return "Error draining node: " + e.getMessage();
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