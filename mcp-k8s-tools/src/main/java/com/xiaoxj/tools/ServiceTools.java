package com.xiaoxj.tools;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ServiceTools {

    private final CoreV1Api coreV1Api;

    public ServiceTools(CoreV1Api coreV1Api) {
        this.coreV1Api = coreV1Api;
    }

//    @Tool(name = "list_services", description = "Lists all Kubernetes services in the specified namespace")
//    public List<String> listServices(
//            @ToolParam(description = "The Kubernetes namespace to list services from") String namespace) {
//        return listServices(namespace, "default");
//    }

    @Tool(name = "list_services", description = "Lists all Kubernetes services in the specified namespace")
    public List<String> listServices(
            @ToolParam(description = "The Kubernetes namespace to list services from") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1ServiceList serviceList = coreV1Api.listNamespacedService(ns, null, null, null, null, null, null, null, null, null, null);

            return serviceList.getItems().stream()
                    .map(service -> {
                        String name = service.getMetadata() != null ? service.getMetadata().getName() : "unknown";
                        String type = service.getSpec() != null ? service.getSpec().getType() : "N/A";
                        String clusterIP = service.getSpec() != null ? service.getSpec().getClusterIP() : "N/A";

                        String externalIP = "N/A";
                        if (service.getStatus() != null && service.getStatus().getLoadBalancer() != null &&
                                service.getStatus().getLoadBalancer().getIngress() != null &&
                                !service.getStatus().getLoadBalancer().getIngress().isEmpty()) {
                            V1LoadBalancerIngress ingress = service.getStatus().getLoadBalancer().getIngress().get(0);
                            externalIP = ingress.getIp() != null ? ingress.getIp() :
                                    (ingress.getHostname() != null ? ingress.getHostname() : "N/A");
                        }

                        String ports = "N/A";
                        if (service.getSpec() != null && service.getSpec().getPorts() != null) {
                            ports = service.getSpec().getPorts().stream()
                                    .map(port -> port.getPort() + ":" + port.getTargetPort() + "/" + port.getProtocol())
                                    .collect(Collectors.joining(", "));
                        }

                        return name + "\n  - Type: " + type +
                                "\n  - Cluster IP: " + clusterIP +
                                "\n  - External IP: " + externalIP +
                                "\n  - Ports: " + ports;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

//    @Tool(name = "describe_service", description = "Get detailed information about a specific service")
//    public String describeService(
//            @ToolParam(description = "Name of the service to describe") String serviceName,
//            @ToolParam(description = "The Kubernetes namespace where the service is located") String namespace) {
//        return describeService(serviceName, namespace, "default");
//    }

    @Tool(name = "describe_service", description = "Get detailed information about a specific service")
    public String describeService(
            @ToolParam(description = "Name of the service to describe") String serviceName,
            @ToolParam(description = "The Kubernetes namespace where the service is located") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1Service service = coreV1Api.readNamespacedService(serviceName, ns, null);

            StringBuilder sb = new StringBuilder();
            sb.append("Service: ").append(service.getMetadata() != null ? service.getMetadata().getName() : "unknown").append("\n");
            sb.append("Namespace: ").append(ns).append("\n");

            sb.append("Labels: ");
            if (service.getMetadata() != null && service.getMetadata().getLabels() != null) {
                sb.append(service.getMetadata().getLabels().entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining(", ")));
            } else {
                sb.append("none");
            }
            sb.append("\n\n");

            sb.append("Spec:\n");
            sb.append("  Type: ").append(service.getSpec() != null ? service.getSpec().getType() : "N/A").append("\n");
            sb.append("  Cluster IP: ").append(service.getSpec() != null ? service.getSpec().getClusterIP() : "N/A").append("\n");

            sb.append("  External IPs: ");
            if (service.getSpec() != null && service.getSpec().getExternalIPs() != null) {
                sb.append(String.join(", ", service.getSpec().getExternalIPs()));
            } else {
                sb.append("none");
            }
            sb.append("\n");

            sb.append("  Ports:\n");
            if (service.getSpec() != null && service.getSpec().getPorts() != null) {
                for (V1ServicePort port : service.getSpec().getPorts()) {
                    sb.append("    - ").append(port.getName() != null ? port.getName() : "unnamed")
                            .append(": ").append(port.getPort())
                            .append(":").append(port.getTargetPort())
                            .append("/").append(port.getProtocol()).append("\n");
                }
            } else {
                sb.append("    No ports defined\n");
            }

            sb.append("\n  Selector:\n");
            if (service.getSpec() != null && service.getSpec().getSelector() != null) {
                for (Map.Entry<String, String> entry : service.getSpec().getSelector().entrySet()) {
                    sb.append("    ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            } else {
                sb.append("    No selector defined\n");
            }

            sb.append("\nStatus:\n");
            sb.append("  Load Balancer:\n");
            sb.append("    Ingress:\n");
            if (service.getStatus() != null && service.getStatus().getLoadBalancer() != null &&
                    service.getStatus().getLoadBalancer().getIngress() != null) {
                for (V1LoadBalancerIngress ingress : service.getStatus().getLoadBalancer().getIngress()) {
                    sb.append("    - IP: ").append(ingress.getIp() != null ? ingress.getIp() : "N/A")
                            .append(", Hostname: ").append(ingress.getHostname() != null ? ingress.getHostname() : "N/A").append("\n");
                }
            } else {
                sb.append("    None\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error describing service: " + e.getMessage();
        }
    }

//    @Tool(name = "get_service_endpoints", description = "Get endpoints (pod IPs) for a specific service")
//    public String getServiceEndpoints(
//            @ToolParam(description = "Name of the service to get endpoints for") String serviceName,
//            @ToolParam(description = "The Kubernetes namespace where the service is located") String namespace) {
//        return getServiceEndpoints(serviceName, namespace, "default");
//    }

    @Tool(name = "get_service_endpoints", description = "Get endpoints (pod IPs) for a specific service")
    public String getServiceEndpoints(
            @ToolParam(description = "Name of the service to get endpoints for") String serviceName,
            @ToolParam(description = "The Kubernetes namespace where the service is located") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1Endpoints endpoints = coreV1Api.readNamespacedEndpoints(serviceName, ns, null);

            StringBuilder sb = new StringBuilder();
            sb.append("Service: ").append(serviceName).append("\n");
            sb.append("Endpoints:\n");

            if (endpoints.getSubsets() != null) {
                String endpointList = endpoints.getSubsets().stream()
                        .flatMap(subset -> {
                            if (subset.getAddresses() != null) {
                                return subset.getAddresses().stream()
                                        .map(address -> "  - " + address.getIp() + " (" +
                                                (address.getTargetRef() != null ? address.getTargetRef().getName() : "unknown") + ")");
                            }
                            return null;
                        })
                        .filter(str -> str != null)
                        .collect(Collectors.joining("\n"));

                if (!endpointList.isEmpty()) {
                    sb.append(endpointList);
                } else {
                    sb.append("No endpoints found");
                }
            } else {
                sb.append("No endpoints found");
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error getting service endpoints: " + e.getMessage();
        }
    }
}