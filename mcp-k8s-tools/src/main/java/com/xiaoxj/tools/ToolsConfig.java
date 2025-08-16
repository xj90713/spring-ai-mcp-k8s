package com.xiaoxj.tools;


import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.*;
import io.kubernetes.client.util.Config;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class ToolsConfig {

    @Bean
    public ApiClient kubernetesClient() {
        String kubeConfigPath = System.getProperty("user.home") + "/.kube/config";
        try {
            return Config.fromConfig(kubeConfigPath);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize Kubernetes client from " + kubeConfigPath, e);
        }
    }

    @Bean
    public CoreV1Api coreV1Api(ApiClient client) {
        return new CoreV1Api(client);
    }

    @Bean
    public AppsV1Api appsV1Api(ApiClient client) {
        return new AppsV1Api(client);
    }

    @Bean
    public StorageV1Api storageV1Api(ApiClient client) {
        return new StorageV1Api(client);
    }

    @Bean
    public SchedulingV1Api schedulingV1Api(ApiClient client) {
        return new SchedulingV1Api(client);
    }

    @Bean
    public NetworkingV1Api networkingV1Api(ApiClient client) {
        return new NetworkingV1Api(client);
    }

    @Bean
    public EventsV1Api eventsV1Api(ApiClient client) {
        return new EventsV1Api(client);
    }

    @Bean
    public BatchV1Api batchV1Api(ApiClient client) {
        return new BatchV1Api(client);
    }

    @Bean
    public ToolCallbackProvider k8sTools(
            PodTools podTools,
            NodeTools nodeTools,
            ServiceTools serviceTools,
            EventTools eventTools,
            StorageTools storageTools,
            SchedulingTools schedulingTools,
            DeploymentTools deploymentTools,
            ConfigMapAndSecretTools configMapAndSecretTools,
            NetworkTools networkTools,
            ResourceManagementTools resourceManagementTools,
            HealthTools healthTools,
            HelmTools helmTools,
            JobTools jobTools) {

        return MethodToolCallbackProvider.builder()
                .toolObjects(
                        podTools,
                        nodeTools,
                        serviceTools,
                        storageTools,
                        schedulingTools,
                        deploymentTools,
                        configMapAndSecretTools,
                        networkTools,
                        resourceManagementTools,
                        jobTools,
                        eventTools,
                        healthTools,
                        helmTools)
                .build();
    }
}