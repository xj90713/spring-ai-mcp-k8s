package com.xiaoxj.tools;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ConfigMapAndSecretTools {

    private final CoreV1Api coreV1Api;

    public ConfigMapAndSecretTools(CoreV1Api coreV1Api) {
        this.coreV1Api = coreV1Api;
    }

    @Tool(name = "list_config_maps", description = "Lists all ConfigMaps in the specified namespace")
    public List<String> listConfigMaps(
            @ToolParam(description = "The Kubernetes namespace to list ConfigMaps from") String namespace) {
        return listConfigMaps(namespace, "default");
    }

    @Tool(name = "list_config_maps", description = "Lists all ConfigMaps in the specified namespace")
    public List<String> listConfigMaps(
            @ToolParam(description = "The Kubernetes namespace to list ConfigMaps from") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            return coreV1Api.listNamespacedConfigMap(namespace != null ? namespace : defaultNamespace,
                            null, null, null, null, null, null, null, null, null, null)
                    .getItems()
                    .stream()
                    .map(configMap ->
                            configMap.getMetadata().getName() +
                                    "\n  - Data Keys: " + (configMap.getData() != null ?
                                    String.join(", ", configMap.getData().keySet()) : "No data") +
                                    "\n  - Created: " + configMap.getMetadata().getCreationTimestamp() +
                                    "\n  - Labels: " + (configMap.getMetadata().getLabels() != null ?
                                    configMap.getMetadata().getLabels().entrySet().stream()
                                            .map(e -> e.getKey() + "=" + e.getValue())
                                            .collect(Collectors.joining(", ")) : "No labels"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    @Tool(name = "describe_config_map", description = "Get detailed information about a specific ConfigMap")
    public String describeConfigMap(
            @ToolParam(description = "Name of the ConfigMap to describe") String configMapName,
            @ToolParam(description = "The Kubernetes namespace where the ConfigMap is located") String namespace) {
        return describeConfigMap(configMapName, namespace, "default");
    }

    @Tool(name = "describe_config_map", description = "Get detailed information about a specific ConfigMap")
    public String describeConfigMap(
            @ToolParam(description = "Name of the ConfigMap to describe") String configMapName,
            @ToolParam(description = "The Kubernetes namespace where the ConfigMap is located") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            var configMap = coreV1Api.readNamespacedConfigMap(configMapName,
                    namespace != null ? namespace : defaultNamespace, null);
            return String.format(
                    "ConfigMap: %s\n" +
                            "Namespace: %s\n" +
                            "Created: %s\n\n" +
                            "Labels:\n%s\n\n" +
                            "Annotations:\n%s\n\n" +
                            "Data:\n%s",
                    configMap.getMetadata().getName(),
                    namespace != null ? namespace : defaultNamespace,
                    configMap.getMetadata().getCreationTimestamp(),
                    configMap.getMetadata().getLabels() != null ?
                            configMap.getMetadata().getLabels().entrySet().stream()
                                    .map(e -> "  " + e.getKey() + ": " + e.getValue())
                                    .collect(Collectors.joining("\n")) : "  No labels",
                    configMap.getMetadata().getAnnotations() != null ?
                            configMap.getMetadata().getAnnotations().entrySet().stream()
                                    .map(e -> "  " + e.getKey() + ": " + e.getValue())
                                    .collect(Collectors.joining("\n")) : "  No annotations",
                    configMap.getData() != null ?
                            configMap.getData().entrySet().stream()
                                    .map(e -> "  " + e.getKey() + ": " +
                                            (e.getValue().length() > 100 ?
                                                    e.getValue().substring(0, 100) + "..." : e.getValue()))
                                    .collect(Collectors.joining("\n")) : "  No data"
            );
        } catch (Exception e) {
            return "Error describing ConfigMap: " + e.getMessage();
        }
    }

    @Tool(name = "list_secrets", description = "Lists all Secrets in the specified namespace (names only for security)")
    public List<String> listSecrets(
            @ToolParam(description = "The Kubernetes namespace to list Secrets from") String namespace) {
        return listSecrets(namespace, "default");
    }

    @Tool(name = "list_secrets", description = "Lists all Secrets in the specified namespace (names only for security)")
    public List<String> listSecrets(
            @ToolParam(description = "The Kubernetes namespace to list Secrets from") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            return coreV1Api.listNamespacedSecret(namespace != null ? namespace : defaultNamespace,
                            null, null, null, null, null, null, null, null, null, null)
                    .getItems()
                    .stream()
                    .map(secret ->
                            secret.getMetadata().getName() +
                                    "\n  - Type: " + secret.getType() +
                                    "\n  - Created: " + secret.getMetadata().getCreationTimestamp() +
                                    "\n  - Labels: " + (secret.getMetadata().getLabels() != null ?
                                    secret.getMetadata().getLabels().entrySet().stream()
                                            .map(e -> e.getKey() + "=" + e.getValue())
                                            .collect(Collectors.joining(", ")) : "No labels"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    @Tool(name = "describe_secret", description = "Get metadata about a specific Secret (no secret values shown)")
    public String describeSecret(
            @ToolParam(description = "Name of the Secret to describe") String secretName,
            @ToolParam(description = "The Kubernetes namespace where the Secret is located") String namespace) {
        return describeSecret(secretName, namespace, "default");
    }

    @Tool(name = "describe_secret", description = "Get metadata about a specific Secret (no secret values shown)")
    public String describeSecret(
            @ToolParam(description = "Name of the Secret to describe") String secretName,
            @ToolParam(description = "The Kubernetes namespace where the Secret is located") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            var secret = coreV1Api.readNamespacedSecret(secretName,
                    namespace != null ? namespace : defaultNamespace, null);
            return String.format(
                    "Secret: %s\n" +
                            "Namespace: %s\n" +
                            "Type: %s\n" +
                            "Created: %s\n\n" +
                            "Labels:\n%s\n\n" +
                            "Annotations:\n%s\n\n" +
                            "Data Keys:\n%s",
                    secret.getMetadata().getName(),
                    namespace != null ? namespace : defaultNamespace,
                    secret.getType(),
                    secret.getMetadata().getCreationTimestamp(),
                    secret.getMetadata().getLabels() != null ?
                            secret.getMetadata().getLabels().entrySet().stream()
                                    .map(e -> "  " + e.getKey() + ": " + e.getValue())
                                    .collect(Collectors.joining("\n")) : "  No labels",
                    secret.getMetadata().getAnnotations() != null ?
                            secret.getMetadata().getAnnotations().entrySet().stream()
                                    .map(e -> "  " + e.getKey() + ": " + e.getValue())
                                    .collect(Collectors.joining("\n")) : "  No annotations",
                    secret.getData() != null ?
                            secret.getData().keySet().stream()
                                    .map(k -> "  - " + k)
                                    .collect(Collectors.joining("\n")) : "  No data"
            );
        } catch (Exception e) {
            return "Error describing Secret: " + e.getMessage();
        }
    }
}