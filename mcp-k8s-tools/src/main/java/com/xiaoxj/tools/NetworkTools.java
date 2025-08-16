package com.xiaoxj.tools;

import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1IngressList;
import io.kubernetes.client.openapi.models.V1NetworkPolicy;
import io.kubernetes.client.openapi.models.V1NetworkPolicyList;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NetworkTools {

    private final NetworkingV1Api networkingV1Api;

    public NetworkTools(NetworkingV1Api networkingV1Api) {
        this.networkingV1Api = networkingV1Api;
    }

    @Tool(name = "listIngresses", description = "Lists all Ingresses in the specified namespace")
    public List<String> listIngresses(
            @ToolParam(description = "The Kubernetes namespace to list Ingresses from") String namespace) {
        return listIngresses(namespace, "default");
    }

    @Tool(name = "listIngresses", description = "Lists all Ingresses in the specified namespace")
    public List<String> listIngresses(
            @ToolParam(description = "The Kubernetes namespace to list Ingresses from") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1IngressList ingressList = networkingV1Api.listNamespacedIngress(
                    ns, null, null, null, null, null, null, null, null, null, null);

            return ingressList.getItems().stream()
                    .map(ingress -> {
                        String name = ingress.getMetadata() != null ? ingress.getMetadata().getName() : "unknown";
                        String hosts = ingress.getSpec() != null && ingress.getSpec().getRules() != null ?
                                ingress.getSpec().getRules().stream()
                                        .filter(rule -> rule.getHost() != null)
                                        .map(rule -> rule.getHost())
                                        .collect(Collectors.joining(", ")) : "No hosts";
                        String tls = ingress.getSpec() != null && ingress.getSpec().getTls() != null ?
                                ingress.getSpec().getTls().stream()
                                        .filter(tlsEntry -> tlsEntry.getHosts() != null)
                                        .map(tlsEntry -> String.join(", ", tlsEntry.getHosts()))
                                        .collect(Collectors.joining("; ")) : "No TLS";
                        String className = ingress.getSpec() != null ?
                                ingress.getSpec().getIngressClassName() : "default";

                        return name + "\n  - Hosts: " + hosts +
                                "\n  - TLS: " + tls +
                                "\n  - Class: " + className;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Tool(name = "describeIngress", description = "Get detailed information about a specific Ingress")
    public String describeIngress(
            @ToolParam(description = "Name of the Ingress to describe") String ingressName,
            @ToolParam(description = "The Kubernetes namespace where the Ingress is located") String namespace) {
        return describeIngress(ingressName, namespace, "default");
    }

    @Tool(name = "describeIngress", description = "Get detailed information about a specific Ingress")
    public String describeIngress(
            @ToolParam(description = "Name of the Ingress to describe") String ingressName,
            @ToolParam(description = "The Kubernetes namespace where the Ingress is located") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1Ingress ingress = networkingV1Api.readNamespacedIngress(ingressName, ns, null);

            StringBuilder sb = new StringBuilder();
            sb.append("Ingress: ").append(ingress.getMetadata() != null ? ingress.getMetadata().getName() : "unknown").append("\n");
            sb.append("Namespace: ").append(ns).append("\n");
            sb.append("Class: ").append(ingress.getSpec() != null ?
                    ingress.getSpec().getIngressClassName() : "default").append("\n");
            sb.append("Created: ").append(ingress.getMetadata() != null ?
                    ingress.getMetadata().getCreationTimestamp() : "unknown").append("\n\n");

            sb.append("Rules:\n");
            if (ingress.getSpec() != null && ingress.getSpec().getRules() != null) {
                for (var rule : ingress.getSpec().getRules()) {
                    sb.append("  Host: ").append(rule.getHost() != null ? rule.getHost() : "*").append("\n");
                    sb.append("     Paths:\n");
                    if (rule.getHttp() != null && rule.getHttp().getPaths() != null) {
                        for (var path : rule.getHttp().getPaths()) {
                            sb.append("    - Path: ").append(path.getPath() != null ? path.getPath() : "/").append("\n");
                            sb.append("       PathType: ").append(path.getPathType()).append("\n");
                            sb.append("       Backend:\n");
                            sb.append("         Service: ").append(path.getBackend() != null && path.getBackend().getService() != null ?
                                    path.getBackend().getService().getName() : "unknown").append("\n");
                            sb.append("         Port: ").append(path.getBackend() != null && path.getBackend().getService() != null &&
                                    path.getBackend().getService().getPort() != null ?
                                    path.getBackend().getService().getPort().getNumber() : "unknown").append("\n");
                        }
                    } else {
                        sb.append("    No paths defined\n");
                    }
                }
            } else {
                sb.append("  No rules defined\n");
            }

            sb.append("\nTLS:\n");
            if (ingress.getSpec() != null && ingress.getSpec().getTls() != null) {
                for (var tls : ingress.getSpec().getTls()) {
                    sb.append("  - Hosts: ").append(tls.getHosts() != null ?
                            String.join(", ", tls.getHosts()) : "none").append("\n");
                    sb.append("     SecretName: ").append(tls.getSecretName()).append("\n");
                }
            } else {
                sb.append("  No TLS configured\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error describing Ingress: " + e.getMessage();
        }
    }

    @Tool(name = "listNetworkPolicies", description = "Lists all NetworkPolicies in the specified namespace")
    public List<String> listNetworkPolicies(
            @ToolParam(description = "The Kubernetes namespace to list NetworkPolicies from") String namespace) {
        return listNetworkPolicies(namespace, "default");
    }

    @Tool(name = "listNetworkPolicies", description = "Lists all NetworkPolicies in the specified namespace")
    public List<String> listNetworkPolicies(
            @ToolParam(description = "The Kubernetes namespace to list NetworkPolicies from") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1NetworkPolicyList policyList = networkingV1Api.listNamespacedNetworkPolicy(
                    ns, null, null, null, null, null, null, null, null, null, null);

            return policyList.getItems().stream()
                    .map(policy -> {
                        String name = policy.getMetadata() != null ? policy.getMetadata().getName() : "unknown";
                        String podSelector = policy.getSpec() != null && policy.getSpec().getPodSelector() != null &&
                                policy.getSpec().getPodSelector().getMatchLabels() != null ?
                                policy.getSpec().getPodSelector().getMatchLabels().entrySet().stream()
                                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                                        .collect(Collectors.joining(", ")) : "All pods";
                        String policyTypes = policy.getSpec() != null && policy.getSpec().getPolicyTypes() != null ?
                                String.join(", ", policy.getSpec().getPolicyTypes()) : "None";

                        return name + "\n  - Pod Selector: " + podSelector +
                                "\n  - Policy Types: " + policyTypes;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Tool(name = "describeNetworkPolicy", description = "Get detailed information about a specific NetworkPolicy")
    public String describeNetworkPolicy(
            @ToolParam(description = "Name of the NetworkPolicy to describe") String policyName,
            @ToolParam(description = "The Kubernetes namespace where the NetworkPolicy is located") String namespace) {
        return describeNetworkPolicy(policyName, namespace, "default");
    }

    @Tool(name = "describeNetworkPolicy", description = "Get detailed information about a specific NetworkPolicy")
    public String describeNetworkPolicy(
            @ToolParam(description = "Name of the NetworkPolicy to describe") String policyName,
            @ToolParam(description = "The Kubernetes namespace where the NetworkPolicy is located") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1NetworkPolicy policy = networkingV1Api.readNamespacedNetworkPolicy(policyName, ns, null);

            StringBuilder sb = new StringBuilder();
            sb.append("NetworkPolicy: ").append(policy.getMetadata() != null ?
                    policy.getMetadata().getName() : "unknown").append("\n");
            sb.append("Namespace: ").append(ns).append("\n");
            sb.append("Created: ").append(policy.getMetadata() != null ?
                    policy.getMetadata().getCreationTimestamp() : "unknown").append("\n\n");

            sb.append("Pod Selector:\n");
            sb.append("  Labels: ").append(policy.getSpec() != null && policy.getSpec().getPodSelector() != null &&
                    policy.getSpec().getPodSelector().getMatchLabels() != null ?
                    policy.getSpec().getPodSelector().getMatchLabels().entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .collect(Collectors.joining(", ")) : "All pods").append("\n\n");

            sb.append("Policy Types: ").append(policy.getSpec() != null && policy.getSpec().getPolicyTypes() != null ?
                    String.join(", ", policy.getSpec().getPolicyTypes()) : "None").append("\n\n");

            sb.append("Ingress Rules:\n");
            if (policy.getSpec() != null && policy.getSpec().getIngress() != null) {
                for (var ingress : policy.getSpec().getIngress()) {
                    sb.append("  From:\n");
                    if (ingress.getFrom() != null) {
                        for (var from : ingress.getFrom()) {
                            sb.append("    - Pod Selector: ").append(from.getPodSelector() != null &&
                                    from.getPodSelector().getMatchLabels() != null ?
                                    from.getPodSelector().getMatchLabels().entrySet().stream()
                                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                                            .collect(Collectors.joining(", ")) : "Any").append("\n");
                            sb.append("       Namespace Selector: ").append(from.getNamespaceSelector() != null &&
                                    from.getNamespaceSelector().getMatchLabels() != null ?
                                    from.getNamespaceSelector().getMatchLabels().entrySet().stream()
                                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                                            .collect(Collectors.joining(", ")) : "Any").append("\n");
                            sb.append("       IP Block: ").append(from.getIpBlock() != null ?
                                            from.getIpBlock().getCidr() : "None").append(" (Except: ")
                                    .append(from.getIpBlock() != null && from.getIpBlock().getExcept() != null ?
                                            String.join(", ", from.getIpBlock().getExcept()) : "None").append(")\n");
                        }
                    } else {
                        sb.append("    No from rules\n");
                    }

                    sb.append("     Ports:\n");
                    if (ingress.getPorts() != null) {
                        for (var port : ingress.getPorts()) {
                            sb.append("    - Protocol: ").append(port.getProtocol()).append("\n");
                            sb.append("       Port: ").append(port.getPort()).append("\n");
                        }
                    } else {
                        sb.append("    No port rules\n");
                    }
                }
            } else {
                sb.append("  No ingress rules\n");
            }

            sb.append("\nEgress Rules:\n");
            if (policy.getSpec() != null && policy.getSpec().getEgress() != null) {
                for (var egress : policy.getSpec().getEgress()) {
                    sb.append("  To:\n");
                    if (egress.getTo() != null) {
                        for (var to : egress.getTo()) {
                            sb.append("    - Pod Selector: ").append(to.getPodSelector() != null &&
                                    to.getPodSelector().getMatchLabels() != null ?
                                    to.getPodSelector().getMatchLabels().entrySet().stream()
                                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                                            .collect(Collectors.joining(", ")) : "Any").append("\n");
                            sb.append("       Namespace Selector: ").append(to.getNamespaceSelector() != null &&
                                    to.getNamespaceSelector().getMatchLabels() != null ?
                                    to.getNamespaceSelector().getMatchLabels().entrySet().stream()
                                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                                            .collect(Collectors.joining(", ")) : "Any").append("\n");
                            sb.append("       IP Block: ").append(to.getIpBlock() != null ?
                                            to.getIpBlock().getCidr() : "None").append(" (Except: ")
                                    .append(to.getIpBlock() != null && to.getIpBlock().getExcept() != null ?
                                            String.join(", ", to.getIpBlock().getExcept()) : "None").append(")\n");
                        }
                    } else {
                        sb.append("    No to rules\n");
                    }

                    sb.append("     Ports:\n");
                    if (egress.getPorts() != null) {
                        for (var port : egress.getPorts()) {
                            sb.append("    - Protocol: ").append(port.getProtocol()).append("\n");
                            sb.append("       Port: ").append(port.getPort()).append("\n");
                        }
                    } else {
                        sb.append("    No port rules\n");
                    }
                }
            } else {
                sb.append("  No egress rules\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error describing NetworkPolicy: " + e.getMessage();
        }
    }
}