package com.xiaoxj.agent;


import com.xiaoxj.agent.service.AgentService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(scanBasePackages = "com")
@RestController
@RequestMapping("/api/v1")
public class K8sAgentApplication {

    private final AgentService agentService;

    public K8sAgentApplication(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping(value = "/chat", consumes = org.springframework.http.MediaType.TEXT_PLAIN_VALUE)
    public String invokeChat(@RequestBody String userPrompt) {
        return agentService.invokeAgent(userPrompt);
    }

    public static void main(String[] args) {
        SpringApplication.run(K8sAgentApplication.class, args);
    }
}