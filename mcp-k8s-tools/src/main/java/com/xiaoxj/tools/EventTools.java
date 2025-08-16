package com.xiaoxj.tools;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EventTools {

    private final CoreV1Api coreV1Api;

    public EventTools(CoreV1Api coreV1Api) {
        this.coreV1Api = coreV1Api;
    }

    @Tool(name = "get_recent_events", description = "Get recent events from a namespace for troubleshooting with severity analysis")
    public String getRecentEvents(
            @ToolParam(description = "The Kubernetes namespace to get events from") String namespace) {
        if (namespace == null) {
            namespace = "default";
        }

        try {
            CoreV1EventList  events = coreV1Api.listNamespacedEvent(namespace, null, null, null, null, null, null, null, null, null, null);
            OffsetDateTime now = OffsetDateTime.now();

            List<CoreV1Event> recentEvents = events.getItems().stream()
                    .filter(event -> {
                        if (event.getLastTimestamp() != null) {
                            return ChronoUnit.HOURS.between(event.getLastTimestamp(), now) <= 1;
                        }
                        return true;
                    })
                    .sorted((e1, e2) -> {
                        if (e1.getLastTimestamp() == null && e2.getLastTimestamp() == null) {
                            return 0;
                        }
                        if (e1.getLastTimestamp() == null) {
                            return 1;
                        }
                        if (e2.getLastTimestamp() == null) {
                            return -1;
                        }
                        return e2.getLastTimestamp().compareTo(e1.getLastTimestamp());
                    })
                    .collect(Collectors.toList());

            if (recentEvents.isEmpty()) {
                return "No events found in namespace " + namespace + " in the last hour";
            }

            List<String> criticalEvents = new ArrayList<>();
            List<String> warningEvents = new ArrayList<>();
            List<String> normalEvents = new ArrayList<>();

            for (CoreV1Event event : recentEvents) {
                String eventSummary = String.format(
                        "Time: %s\nType: %s\nReason: %s\nObject: %s/%s\nMessage: %s\nCount: %d\nComponent: %s",
                        event.getLastTimestamp(),
                        event.getType(),
                        event.getReason(),
                        event.getInvolvedObject().getKind(),
                        event.getInvolvedObject().getName(),
                        event.getMessage(),
                        event.getCount() != null ? event.getCount() : 1,
                        event.getSource() != null && event.getSource().getComponent() != null
                                ? event.getSource().getComponent() : "N/A"
                );

                if ("Warning".equals(event.getType()) && isCriticalReason(event.getReason())) {
                    criticalEvents.add(eventSummary);
                } else if ("Warning".equals(event.getType())) {
                    warningEvents.add(eventSummary);
                } else {
                    normalEvents.add(eventSummary);
                }
            }

            String analysis = analyzeEvents(criticalEvents.size(), warningEvents.size(), normalEvents.size());

            StringBuilder result = new StringBuilder();
            result.append(String.format("Event Analysis for namespace %s:\n%s\n", namespace, analysis));

            if (!criticalEvents.isEmpty()) {
                result.append("\nCritical Events (Require Immediate Attention):\n")
                        .append(String.join("\n\n", criticalEvents))
                        .append("\n");
            }

            if (!warningEvents.isEmpty()) {
                result.append("\nWarning Events:\n")
                        .append(String.join("\n\n", warningEvents))
                        .append("\n");
            }

            if (!normalEvents.isEmpty()) {
                result.append("\nNormal Events:\n")
                        .append(String.join("\n\n", normalEvents))
                        .append("\n");
            }

            return result.toString().trim();
        } catch (Exception e) {
            return "Error getting events: " + e.getMessage();
        }
    }

    private boolean isCriticalReason(String reason) {
        if (reason == null) {
            return false;
        }
        Set<String> criticalReasons = Set.of(
                "Failed",
                "FailedCreate",
                "FailedScheduling",
                "BackOff",
                "Error",
                "NodeNotReady",
                "KillContainer"
        );
        return criticalReasons.contains(reason);
    }

    private String analyzeEvents(int criticalCount, int warningCount, int normalCount) {
        int total = criticalCount + warningCount + normalCount;
        List<String> recommendations = new ArrayList<>();

        if (criticalCount > 0) {
            recommendations.add("Critical events detected! Immediate attention required.");
        }

        if (warningCount > normalCount && warningCount > 2) {
            recommendations.add("High number of warning events. System might be unstable.");
        }

        if (criticalCount + warningCount == 0 && normalCount > 0) {
            recommendations.add("System appears to be healthy.");
        }

        return String.format(
                "Summary:\n" +
                        "- Total Events: %d\n" +
                        "- Critical Events: %d\n" +
                        "- Warning Events: %d\n" +
                        "- Normal Events: %d\n\n" +
                        "Recommendations:\n%s",
                total,
                criticalCount,
                warningCount,
                normalCount,
                recommendations.stream()
                        .map(rec -> "- " + rec)
                        .collect(Collectors.joining("\n"))
        );
    }

    @Tool(name = "get_resource_events", description = "Get events for a specific resource")
    public String getResourceEvents(
            @ToolParam(description = "Type of resource (Pod, Deployment, etc)") String resourceType,
            @ToolParam(description = "Name of the resource") String resourceName,
            @ToolParam(description = "The Kubernetes namespace where the resource is located") String namespace) {
        if (namespace == null) {
            namespace = "default";
        }

        try {
            CoreV1EventList events = coreV1Api.listNamespacedEvent(namespace, null, null, null, null, null, null, null, null, null, null);

            List<CoreV1Event> resourceEvents = events.getItems().stream()
                    .filter(event -> {
                        V1ObjectReference involvedObject = event.getInvolvedObject();
                        return involvedObject.getKind().equalsIgnoreCase(resourceType) &&
                                resourceName.equals(involvedObject.getName());
                    })
                    .sorted((e1, e2) -> {
                        if (e1.getLastTimestamp() == null && e2.getLastTimestamp() == null) {
                            return 0;
                        }
                        if (e1.getLastTimestamp() == null) {
                            return 1;
                        }
                        if (e2.getLastTimestamp() == null) {
                            return -1;
                        }
                        return e2.getLastTimestamp().compareTo(e1.getLastTimestamp());
                    })
                    .collect(Collectors.toList());

            if (resourceEvents.isEmpty()) {
                return "No events found for " + resourceType + "/" + resourceName + " in namespace " + namespace;
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("Events for %s/%s in namespace %s:\n\n", resourceType, resourceName, namespace));

            for (CoreV1Event event : resourceEvents) {
                result.append(String.format(
                        "Time: %s\n" +
                                "Type: %s\n" +
                                "Reason: %s\n" +
                                "Message: %s\n" +
                                "Count: %d\n" +
                                "Component: %s\n" +
                                "Host: %s\n\n",
                        event.getLastTimestamp(),
                        event.getType(),
                        event.getReason(),
                        event.getMessage(),
                        event.getCount() != null ? event.getCount() : 1,
                        event.getSource() != null && event.getSource().getComponent() != null
                                ? event.getSource().getComponent() : "N/A",
                        event.getSource() != null && event.getSource().getHost() != null
                                ? event.getSource().getHost() : "N/A"
                ));
            }

            return result.toString().trim();
        } catch (Exception e) {
            return "Error getting resource events: " + e.getMessage();
        }
    }
}