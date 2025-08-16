package com.xiaoxj.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class HelmTools {

    @Tool(name = "list_releases", description = "List all Helm releases in a namespace")
    public String listReleases(
            @ToolParam(description = "The Kubernetes namespace to list releases from") String namespace) throws IOException {
        return executeHelmCommand("list", "--namespace", namespace);
    }

    @Tool(name = "install_chart", description = "Install a Helm chart with optional values")
    public String installChart(
            @ToolParam(description = "Name for the release") String releaseName,
            @ToolParam(description = "Name of the chart to install") String chartName,
            @ToolParam(description = "The Kubernetes namespace to install into") String namespace,
            @ToolParam(description = "Optional version of the chart") String version,
            @ToolParam(description = "Optional key-value pairs for chart values") Map<String, String> values) throws IOException {
        return installChart(releaseName, chartName, namespace, version, values, null);
    }

    @Tool(name = "install_chart", description = "Install a Helm chart with optional values")
    public String installChart(
            @ToolParam(description = "Name for the release") String releaseName,
            @ToolParam(description = "Name of the chart to install") String chartName,
            @ToolParam(description = "The Kubernetes namespace to install into") String namespace,
            @ToolParam(description = "Optional version of the chart") String version,
            @ToolParam(description = "Optional key-value pairs for chart values") Map<String, String> values,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("install");
        command.add(releaseName);
        command.add(chartName);
        command.add("--namespace");
        command.add(namespace != null ? namespace : defaultNamespace);

        if (version != null && !version.isEmpty()) {
            command.add("--version");
            command.add(version);
        }

        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                command.add("--set");
                command.add(entry.getKey() + "=" + entry.getValue());
            }
        }

        return executeHelmCommand(command.toArray(new String[0]));
    }

    @Tool(name = "uninstall_release", description = "Uninstall a Helm release")
    public String uninstallRelease(
            @ToolParam(description = "Name of the release to uninstall") String releaseName,
            @ToolParam(description = "The Kubernetes namespace of the release") String namespace) throws IOException {
        return uninstallRelease(releaseName, namespace, null);
    }

    @Tool(name = "uninstall_release", description = "Uninstall a Helm release")
    public String uninstallRelease(
            @ToolParam(description = "Name of the release to uninstall") String releaseName,
            @ToolParam(description = "The Kubernetes namespace of the release") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) throws IOException {
        return executeHelmCommand("uninstall", releaseName, "--namespace",
                namespace != null ? namespace : defaultNamespace);
    }

    @Tool(name = "upgrade_release", description = "Upgrade an existing Helm release")
    public String upgradeRelease(
            @ToolParam(description = "Name of the release to upgrade") String releaseName,
            @ToolParam(description = "Name of the chart to upgrade to") String chartName,
            @ToolParam(description = "The Kubernetes namespace of the release") String namespace,
            @ToolParam(description = "Optional version to upgrade to") String version,
            @ToolParam(description = "Optional key-value pairs for chart values") Map<String, String> values) throws IOException {
        return upgradeRelease(releaseName, chartName, namespace, version, values, null);
    }

    @Tool(name = "upgrade_release", description = "Upgrade an existing Helm release")
    public String upgradeRelease(
            @ToolParam(description = "Name of the release to upgrade") String releaseName,
            @ToolParam(description = "Name of the chart to upgrade to") String chartName,
            @ToolParam(description = "The Kubernetes namespace of the release") String namespace,
            @ToolParam(description = "Optional version to upgrade to") String version,
            @ToolParam(description = "Optional key-value pairs for chart values") Map<String, String> values,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("upgrade");
        command.add(releaseName);
        command.add(chartName);
        command.add("--namespace");
        command.add(namespace != null ? namespace : defaultNamespace);

        if (version != null && !version.isEmpty()) {
            command.add("--version");
            command.add(version);
        }

        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                command.add("--set");
                command.add(entry.getKey() + "=" + entry.getValue());
            }
        }

        return executeHelmCommand(command.toArray(new String[0]));
    }

    @Tool(name = "get_release_status", description = "Get the status of a Helm release")
    public String getReleaseStatus(
            @ToolParam(description = "Name of the release to check") String releaseName,
            @ToolParam(description = "The Kubernetes namespace of the release") String namespace) throws IOException {
        return getReleaseStatus(releaseName, namespace, null);
    }

    @Tool(name = "get_release_status", description = "Get the status of a Helm release")
    public String getReleaseStatus(
            @ToolParam(description = "Name of the release to check") String releaseName,
            @ToolParam(description = "The Kubernetes namespace of the release") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) throws IOException {
        return executeHelmCommand("status", releaseName, "--namespace",
                namespace != null ? namespace : defaultNamespace);
    }

    @Tool(name = "add_repository", description = "Add a Helm repository")
    public String addRepository(
            @ToolParam(description = "Name for the repository") String name,
            @ToolParam(description = "URL of the repository") String url) throws IOException {
        return executeHelmCommand("repo", "add", name, url);
    }

    @Tool(name = "update_repositories", description = "Update all Helm repositories")
    public String updateRepositories() throws IOException {
        return executeHelmCommand("repo", "update");
    }

    @Tool(name = "show_values", description = "Show the values for a release")
    public String showValues(
            @ToolParam(description = "Name of the release") String releaseName,
            @ToolParam(description = "The Kubernetes namespace of the release") String namespace) throws IOException {
        return showValues(releaseName, namespace, null);
    }

    @Tool(name = "show_values", description = "Show the values for a release")
    public String showValues(
            @ToolParam(description = "Name of the release") String releaseName,
            @ToolParam(description = "The Kubernetes namespace of the release") String namespace,
            @ToolParam(description = "Default namespace if not specified") String defaultNamespace) throws IOException {
        return executeHelmCommand("get", "values", releaseName, "--namespace",
                namespace != null ? namespace : defaultNamespace);
    }

    private String executeHelmCommand(String... args) throws IOException {
        try {
            List<String> command = new ArrayList<>();
            command.add("helm");
            for (String arg : args) {
                command.add(arg);
            }

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroy();
                throw new RuntimeException("Helm command timed out");
            }

            if (process.exitValue() != 0) {
                throw new RuntimeException("Helm command failed: " + output.toString());
            }

            return output.toString();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}