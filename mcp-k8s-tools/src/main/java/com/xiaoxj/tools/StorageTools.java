package com.xiaoxj.tools;


import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.StorageV1Api;
import io.kubernetes.client.openapi.models.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StorageTools {

    private final CoreV1Api coreV1Api;
    private final StorageV1Api storageV1Api;

    public StorageTools(CoreV1Api coreV1Api, StorageV1Api storageV1Api) {
        this.coreV1Api = coreV1Api;
        this.storageV1Api = storageV1Api;
    }

    @Tool(name = "list_persistent_volumes", description = "Lists all persistent volumes in the cluster")
    public List<String> listPersistentVolumes() {
        try {
            V1PersistentVolumeList pvList = coreV1Api.listPersistentVolume(null, null, null, null, null, null, null, null, null, null);
            return pvList.getItems().stream()
                    .map(pv -> {
                        String name = pv.getMetadata() != null ? pv.getMetadata().getName() : "unknown";
                        String status = pv.getStatus() != null ? pv.getStatus().getPhase() : "unknown";
                        String capacity = pv.getSpec() != null && pv.getSpec().getCapacity() != null ?
                                String.valueOf(pv.getSpec().getCapacity().get("storage")) : "unknown";
                        String accessModes = pv.getSpec() != null && pv.getSpec().getAccessModes() != null ?
                                String.join(", ", pv.getSpec().getAccessModes()) : "none";
                        String storageClass = pv.getSpec() != null ? pv.getSpec().getStorageClassName() : "none";
                        String reclaimPolicy = pv.getSpec() != null ? pv.getSpec().getPersistentVolumeReclaimPolicy() : "unknown";

                        return name + "\n  - Status: " + status +
                                "\n  - Capacity: " + capacity +
                                "\n  - Access Modes: " + accessModes +
                                "\n  - Storage Class: " + storageClass +
                                "\n  - Reclaim Policy: " + reclaimPolicy;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

//    @Tool(name = "list_persistent_volume_claims", description = "Lists all persistent volume claims in the specified namespace")
//    public List<String> listPersistentVolumeClaims(
//            @ToolParam(description = "The Kubernetes namespace to list PVCs from") String namespace) {
//        return listPersistentVolumeClaims(namespace, "default");
//    }

    @Tool(name = "list_persistent_volume_claims", description = "Lists all persistent volume claims in the specified namespace")
    public List<String> listPersistentVolumeClaims(
            @ToolParam(description = "The Kubernetes namespace to list PVCs from") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1PersistentVolumeClaimList pvcList = coreV1Api.listNamespacedPersistentVolumeClaim(
                    ns, null, null, null, null, null, null, null, null, null, null);

            return pvcList.getItems().stream()
                    .map(pvc -> {
                        String name = pvc.getMetadata() != null ? pvc.getMetadata().getName() : "unknown";
                        String status = pvc.getStatus() != null ? pvc.getStatus().getPhase() : "unknown";
                        String volume = pvc.getSpec() != null ? pvc.getSpec().getVolumeName() : "none";
                        String capacity = pvc.getStatus() != null && pvc.getStatus().getCapacity() != null ?
                                String.valueOf(pvc.getStatus().getCapacity().get("storage")) : "unknown";
                        String accessModes = pvc.getSpec() != null && pvc.getSpec().getAccessModes() != null ?
                                String.join(", ", pvc.getSpec().getAccessModes()) : "none";
                        String storageClass = pvc.getSpec() != null ? pvc.getSpec().getStorageClassName() : "none";

                        return name + "\n  - Status: " + status +
                                "\n  - Volume: " + volume +
                                "\n  - Capacity: " + capacity +
                                "\n  - Access Modes: " + accessModes +
                                "\n  - Storage Class: " + storageClass;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Tool(name = "list_storage_classes", description = "Lists all storage classes in the cluster")
    public List<String> listStorageClasses() {
        try {
            V1StorageClassList scList = storageV1Api.listStorageClass(null, null, null, null, null, null, null, null, null, null);
            return scList.getItems().stream()
                    .map(sc -> {
                        String name = sc.getMetadata() != null ? sc.getMetadata().getName() : "unknown";
                        String provisioner = sc.getProvisioner();
                        String reclaimPolicy = sc.getReclaimPolicy();
                        String volumeBindingMode = sc.getVolumeBindingMode();
                        Boolean allowVolumeExpansion = sc.getAllowVolumeExpansion();
                        boolean isDefault = sc.getMetadata() != null && sc.getMetadata().getAnnotations() != null &&
                                "true".equals(sc.getMetadata().getAnnotations().get("storageclass.kubernetes.io/is-default-class"));

                        return name + "\n  - Provisioner: " + provisioner +
                                "\n  - Reclaim Policy: " + reclaimPolicy +
                                "\n  - Volume Binding Mode: " + volumeBindingMode +
                                "\n  - Allow Volume Expansion: " + allowVolumeExpansion +
                                "\n  - Default: " + isDefault;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Tool(name = "describe_persistent_volume", description = "Get detailed information about a specific persistent volume")
    public String describePersistentVolume(
            @ToolParam(description = "Name of the persistent volume to describe") String pvName) {
        try {
            V1PersistentVolume pv = coreV1Api.readPersistentVolume(pvName, null);

            StringBuilder sb = new StringBuilder();
            sb.append("Persistent Volume: ").append(pv.getMetadata() != null ? pv.getMetadata().getName() : "unknown").append("\n");
            sb.append("Status: ").append(pv.getStatus() != null ? pv.getStatus().getPhase() : "unknown").append("\n\n");

            sb.append("Spec:\n");
            sb.append("  Capacity: ").append(pv.getSpec() != null && pv.getSpec().getCapacity() != null ?
                    pv.getSpec().getCapacity().get("storage") : "unknown").append("\n");
            sb.append("  Access Modes: ").append(pv.getSpec() != null && pv.getSpec().getAccessModes() != null ?
                    String.join(", ", pv.getSpec().getAccessModes()) : "none").append("\n");
            sb.append("  Storage Class: ").append(pv.getSpec() != null ? pv.getSpec().getStorageClassName() : "none").append("\n");
            sb.append("  Reclaim Policy: ").append(pv.getSpec() != null ? pv.getSpec().getPersistentVolumeReclaimPolicy() : "unknown").append("\n\n");

            sb.append("  Source:\n");
            if (pv.getSpec() != null) {
                if (pv.getSpec().getHostPath() != null) {
                    sb.append("    HostPath:\n      Path: ").append(pv.getSpec().getHostPath().getPath()).append("\n");
                } else if (pv.getSpec().getNfs() != null) {
                    sb.append("    NFS:\n      Server: ").append(pv.getSpec().getNfs().getServer())
                            .append("\n      Path: ").append(pv.getSpec().getNfs().getPath()).append("\n");
                } else if (pv.getSpec().getAwsElasticBlockStore() != null) {
                    sb.append("    AWS EBS:\n      Volume ID: ").append(pv.getSpec().getAwsElasticBlockStore().getVolumeID()).append("\n");
                } else {
                    sb.append("    Other storage type\n");
                }
            }

            sb.append("\nClaim:\n");
            sb.append("  Name: ").append(pv.getSpec() != null && pv.getSpec().getClaimRef() != null ?
                    pv.getSpec().getClaimRef().getName() : "none").append("\n");
            sb.append("  Namespace: ").append(pv.getSpec() != null && pv.getSpec().getClaimRef() != null ?
                    pv.getSpec().getClaimRef().getNamespace() : "none").append("\n");

            return sb.toString();
        } catch (Exception e) {
            return "Error describing persistent volume: " + e.getMessage();
        }
    }

//    @Tool(name = "describe_persistent_volume_claim", description = "Get detailed information about a specific persistent volume claim")
//    public String describePersistentVolumeClaim(
//            @ToolParam(description = "Name of the persistent volume claim to describe") String pvcName,
//            @ToolParam(description = "The Kubernetes namespace where the PVC is located") String namespace) {
//        return describePersistentVolumeClaim(pvcName, namespace, "default");
//    }

    @Tool(name = "describe_persistent_volume_claim", description = "Get detailed information about a specific persistent volume claim")
    public String describePersistentVolumeClaim(
            @ToolParam(description = "Name of the persistent volume claim to describe") String pvcName,
            @ToolParam(description = "The Kubernetes namespace where the PVC is located") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) {
        try {
            String ns = namespace != null ? namespace : defaultNamespace;
            V1PersistentVolumeClaim pvc = coreV1Api.readNamespacedPersistentVolumeClaim(pvcName, ns, null);

            StringBuilder sb = new StringBuilder();
            sb.append("Persistent Volume Claim: ").append(pvc.getMetadata() != null ? pvc.getMetadata().getName() : "unknown").append("\n");
            sb.append("Namespace: ").append(ns).append("\n");
            sb.append("Status: ").append(pvc.getStatus() != null ? pvc.getStatus().getPhase() : "unknown").append("\n\n");

            sb.append("Spec:\n");
            sb.append("  Access Modes: ").append(pvc.getSpec() != null && pvc.getSpec().getAccessModes() != null ?
                    String.join(", ", pvc.getSpec().getAccessModes()) : "none").append("\n");
            sb.append("  Resources:\n");
            sb.append("    Requests:\n");
            sb.append("      Storage: ").append(pvc.getSpec() != null && pvc.getSpec().getResources() != null &&
                    pvc.getSpec().getResources().getRequests() != null ?
                    pvc.getSpec().getResources().getRequests().get("storage") : "none").append("\n");
            sb.append("  Storage Class: ").append(pvc.getSpec() != null ? pvc.getSpec().getStorageClassName() : "none").append("\n");
            sb.append("  Volume Name: ").append(pvc.getSpec() != null ? pvc.getSpec().getVolumeName() : "none").append("\n\n");

            sb.append("Status:\n");
            sb.append("  Capacity: ").append(pvc.getStatus() != null && pvc.getStatus().getCapacity() != null ?
                    pvc.getStatus().getCapacity().get("storage") : "none").append("\n");
            sb.append("  Access Modes: ").append(pvc.getStatus() != null && pvc.getStatus().getAccessModes() != null ?
                    String.join(", ", pvc.getStatus().getAccessModes()) : "none").append("\n");

            return sb.toString();
        } catch (Exception e) {
            return "Error describing persistent volume claim: " + e.getMessage();
        }
    }
}