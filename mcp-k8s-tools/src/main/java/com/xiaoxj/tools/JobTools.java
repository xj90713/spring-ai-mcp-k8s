package com.xiaoxj.tools;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1JobStatus;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JobTools {

    private final BatchV1Api batchV1Api;

    public JobTools(BatchV1Api batchV1Api) {
        this.batchV1Api = batchV1Api;
    }

    @Tool(name = "list_jobs", description = "List all jobs in a namespace")
    public String listJobs(
            @ToolParam(description = "The Kubernetes namespace to list jobs from") String namespace) {
        try {
            V1JobList jobs = batchV1Api.listNamespacedJob(
                    namespace, null, null, null, null, null, null, null, null, null, null);
            return formatJobList(jobs.getItems());
        } catch (ApiException e) {
            return "Error listing jobs: " + e.getMessage();
        }
    }

    @Tool(name = "get_job_status", description = "Get detailed status of a specific job")
    public String getJobStatus(
            @ToolParam(description = "Name of the job") String jobName,
            @ToolParam(description = "The Kubernetes namespace of the job") String namespace) {
        try {
            V1Job job = batchV1Api.readNamespacedJob(jobName, namespace, null);
            return formatJobStatus(job);
        } catch (ApiException e) {
            return "Error getting job status: " + e.getMessage();
        }
    }

    @Tool(name = "delete_job", description = "Delete a job from the cluster")
    public String deleteJob(
            @ToolParam(description = "Name of the job to delete") String jobName,
            @ToolParam(description = "The Kubernetes namespace of the job") String namespace) {
        try {
            batchV1Api.deleteNamespacedJob(
                    jobName, namespace, null, null, null, null, null, null);
            return "Successfully deleted job " + jobName + " in namespace " + namespace;
        } catch (ApiException e) {
            return "Error deleting job: " + e.getMessage();
        }
    }

    private String formatJobList(List<V1Job> jobs) {
        if (jobs.isEmpty()) {
            return "No jobs found in the namespace";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(jobs.size()).append(" jobs:\n");

        for (V1Job job : jobs) {
            String name = job.getMetadata() != null ? job.getMetadata().getName() : "unknown";
            V1JobStatus status = job.getStatus();

            int completions = status != null && status.getSucceeded() != null ? status.getSucceeded() : 0;
            int failed = status != null && status.getFailed() != null ? status.getFailed() : 0;
            int active = status != null && status.getActive() != null ? status.getActive() : 0;
            String startTime = status != null && status.getStartTime() != null ?
                    status.getStartTime().toString() : "N/A";
            String completionTime = status != null && status.getCompletionTime() != null ?
                    status.getCompletionTime().toString() : "N/A";

            sb.append("Job: ").append(name).append("\n")
                    .append("  - Status: Active: ").append(active)
                    .append(", Succeeded: ").append(completions)
                    .append(", Failed: ").append(failed).append("\n")
                    .append("  - Start Time: ").append(startTime).append("\n")
                    .append("  - Completion Time: ").append(completionTime).append("\n\n");
        }

        return sb.toString();
    }

    private String formatJobStatus(V1Job job) {
        String name = job.getMetadata() != null ? job.getMetadata().getName() : "unknown";
        String namespace = job.getMetadata() != null ? job.getMetadata().getNamespace() : "unknown";

        Integer completions = job.getSpec() != null ? job.getSpec().getCompletions() : 0;
        Integer parallelism = job.getSpec() != null ? job.getSpec().getParallelism() : 0;

        V1JobStatus status = job.getStatus();
        int succeeded = status != null && status.getSucceeded() != null ? status.getSucceeded() : 0;
        int failed = status != null && status.getFailed() != null ? status.getFailed() : 0;
        int active = status != null && status.getActive() != null ? status.getActive() : 0;
        String startTime = status != null && status.getStartTime() != null ?
                status.getStartTime().toString() : "N/A";
        String completionTime = status != null && status.getCompletionTime() != null ?
                status.getCompletionTime().toString() : "N/A";

        StringBuilder sb = new StringBuilder();
        sb.append("Job Details for ").append(name).append(" in namespace ").append(namespace).append(":\n")
                .append("Specifications:\n")
                .append("  - Desired Completions: ").append(completions).append("\n")
                .append("  - Parallelism: ").append(parallelism).append("\n\n")
                .append("Status:\n")
                .append("  - Active: ").append(active).append("\n")
                .append("  - Succeeded: ").append(succeeded).append("\n")
                .append("  - Failed: ").append(failed).append("\n")
                .append("  - Start Time: ").append(startTime).append("\n")
                .append("  - Completion Time: ").append(completionTime).append("\n");

        if (status != null && status.getConditions() != null) {
            for (var condition : status.getConditions()) {
                sb.append("\nCondition: ").append(condition.getType()).append("\n")
                        .append("  - Status: ").append(condition.getStatus()).append("\n")
                        .append("  - Last Transition: ").append(condition.getLastTransitionTime()).append("\n");
                if (condition.getMessage() != null) {
                    sb.append("  - Message: ").append(condition.getMessage()).append("\n");
                }
            }
        }

        return sb.toString();
    }
}