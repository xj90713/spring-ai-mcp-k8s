package com.xiaoxj.tools;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.SchedulingV1Api;
import io.kubernetes.client.openapi.models.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SchedulingTools {

    private final CoreV1Api coreV1Api;
    private final SchedulingV1Api schedulingV1Api;

    public SchedulingTools(CoreV1Api coreV1Api, SchedulingV1Api schedulingV1Api) {
        this.coreV1Api = coreV1Api;
        this.schedulingV1Api = schedulingV1Api;
    }

    @Tool(name = "list_priority_classes", description = "Lists all priority classes in the cluster")
    public List<String> listPriorityClasses() {
        try {
            V1PriorityClassList priorityClassList = schedulingV1Api.listPriorityClass(
                    null, null, null, null, null, null, null, null, null, null);

            return priorityClassList.getItems().stream()
                    .map(pc -> {
                        String name = pc.getMetadata() != null ? pc.getMetadata().getName() : "unknown";
                        int value = pc.getValue();
                        boolean globalDefault = pc.getGlobalDefault() != null && pc.getGlobalDefault();
                        String description = pc.getDescription() != null ? pc.getDescription() : "N/A";

                        return name + "\n  - Value: " + value +
                                "\n  - Global Default: " + globalDefault +
                                "\n  - Description: " + description;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Tool(name = "describe_priority_class", description = "Get detailed information about a specific priority class")
    public String describePriorityClass(
            @ToolParam(description = "Name of the priority class to describe") String priorityClassName) {
        try {
            V1PriorityClass pc = schedulingV1Api.readPriorityClass(priorityClassName, null);

            StringBuilder sb = new StringBuilder();
            sb.append("Priority Class: ").append(pc.getMetadata() != null ? pc.getMetadata().getName() : "unknown").append("\n");
            sb.append("Value: ").append(pc.getValue()).append("\n");
            sb.append("Global Default: ").append(pc.getGlobalDefault() != null && pc.getGlobalDefault()).append("\n");
            sb.append("Description: ").append(pc.getDescription() != null ? pc.getDescription() : "N/A").append("\n\n");

            sb.append("Metadata:\n");
            sb.append("  Created: ").append(pc.getMetadata() != null ? pc.getMetadata().getCreationTimestamp() : "unknown").append("\n");
            sb.append("  Labels: ");
            if (pc.getMetadata() != null && pc.getMetadata().getLabels() != null) {
                sb.append(pc.getMetadata().getLabels().entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining(", ")));
            } else {
                sb.append("none");
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error describing priority class: " + e.getMessage();
        }
    }

    @Tool(name = "list_node_taints", description = "Lists all taints on the specified node")
    public String listNodeTaints(
            @ToolParam(description = "Name of the node to get taints from") String nodeName) {
        try {
            V1Node node = coreV1Api.readNode(nodeName, null);
            List<V1Taint> taints = node.getSpec() != null ? node.getSpec().getTaints() : Collections.emptyList();

            if (taints.isEmpty()) {
                return "No taints found on node " + nodeName;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Node: ").append(nodeName).append("\n");
            sb.append("Taints:\n");
            sb.append(taints.stream()
                    .map(taint -> "  - " + taint.getKey() + "=" + taint.getValue() + ":" + taint.getEffect())
                    .collect(Collectors.joining("\n")));

            return sb.toString();
        } catch (Exception e) {
            return "Error listing node taints: " + e.getMessage();
        }
    }

    @Tool(name = "list_pod_tolerations", description = "Lists all tolerations on pods in a namespace")
    public List<String> listPodTolerations(
            @ToolParam(description = "The Kubernetes namespace to list pod tolerations from") String namespace) {
        return listPodTolerations(namespace, "default");
    }

    @Tool(name = "list_pod_tolerations", description = "Lists all tolerations on pods in a namespace")
    public List<String> listPodTolerations(
            @ToolParam(description = "The Kubernetes namespace to list pod tolerations from") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1PodList podList = coreV1Api.listNamespacedPod(ns, null, null, null, null, null, null, null, null, null, null);

            return podList.getItems().stream()
                    .filter(pod -> pod.getSpec() != null && pod.getSpec().getTolerations() != null && !pod.getSpec().getTolerations().isEmpty())
                    .map(pod -> {
                        String name = pod.getMetadata() != null ? pod.getMetadata().getName() : "unknown";
                        String tolerations = pod.getSpec().getTolerations().stream()
                                .map(tol -> {
                                    String key = tol.getKey() != null ? tol.getKey() : "<all>";
                                    String value = tol.getValue() != null ? "=" + tol.getValue() : "";
                                    String seconds = tol.getTolerationSeconds() != null ? " for " + tol.getTolerationSeconds() + "s" : "";
                                    return "    - " + key + value + ":" + tol.getEffect() + seconds;
                                })
                                .collect(Collectors.joining("\n"));

                        return name + ":\n  Tolerations:\n" + tolerations;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Tool(name = "list_pod_node_affinity", description = "Lists node affinity rules for pods in a namespace")
    public List<String> listPodNodeAffinity(
            @ToolParam(description = "The Kubernetes namespace to list pod node affinity rules from") String namespace) {
        return listPodNodeAffinity(namespace, "default");
    }

    @Tool(name = "list_pod_node_affinity", description = "Lists node affinity rules for pods in a namespace")
    public List<String> listPodNodeAffinity(
            @ToolParam(description = "The Kubernetes namespace to list pod node affinity rules from") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1PodList podList = coreV1Api.listNamespacedPod(ns, null, null, null, null, null, null, null, null, null, null);

            return podList.getItems().stream()
                    .filter(pod -> pod.getSpec() != null &&
                            pod.getSpec().getAffinity() != null &&
                            pod.getSpec().getAffinity().getNodeAffinity() != null)
                    .map(pod -> {
                        String name = pod.getMetadata() != null ? pod.getMetadata().getName() : "unknown";
                        V1NodeAffinity nodeAffinity = pod.getSpec().getAffinity().getNodeAffinity();

                        // Required rules
                        String requiredRules = "None";
                        if (nodeAffinity.getRequiredDuringSchedulingIgnoredDuringExecution() != null &&
                                nodeAffinity.getRequiredDuringSchedulingIgnoredDuringExecution().getNodeSelectorTerms() != null) {

                            requiredRules = nodeAffinity.getRequiredDuringSchedulingIgnoredDuringExecution()
                                    .getNodeSelectorTerms().stream()
                                    .map(term -> {
                                        if (term.getMatchExpressions() == null) {
                                            return "";
                                        }
                                        return term.getMatchExpressions().stream()
                                                .map(expr -> {
                                                    String values = expr.getValues() != null ?
                                                            String.join(", ", expr.getValues()) : "";
                                                    return "      - " + expr.getKey() + " " + expr.getOperator() + " [" + values + "]";
                                                })
                                                .collect(Collectors.joining("\n"));
                                    })
                                    .collect(Collectors.joining("\n"));
                        }

                        // Preferred rules
                        String preferredRules = "None";
                        if (nodeAffinity.getPreferredDuringSchedulingIgnoredDuringExecution() != null) {
                            preferredRules = nodeAffinity.getPreferredDuringSchedulingIgnoredDuringExecution().stream()
                                    .map(pref -> {
                                        String expressions = "";
                                        if (pref.getPreference().getMatchExpressions() != null) {
                                            expressions = pref.getPreference().getMatchExpressions().stream()
                                                    .map(expr -> {
                                                        String values = expr.getValues() != null ?
                                                                String.join(", ", expr.getValues()) : "";
                                                        return "        - " + expr.getKey() + " " + expr.getOperator() + " [" + values + "]";
                                                    })
                                                    .collect(Collectors.joining("\n"));
                                        }
                                        return "    - Weight: " + pref.getWeight() + "\n      Match Expressions:\n" + expressions;
                                    })
                                    .collect(Collectors.joining("\n"));
                        }

                        return name + ":\n" +
                                "  Required Rules:\n" + requiredRules + "\n" +
                                "  Preferred Rules:\n" + preferredRules;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}