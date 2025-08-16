package com.xiaoxj.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentService {

    private static final int MAX_ITERATIONS = 3;
    private static final int MAX_RETRIES = 3;

    private static final String ERROR_HTML_TEMPLATE = """
            <div style="font-family: 'Segoe UI', Arial, sans-serif; padding: 20px; border-left: 5px solid #e74c3c; background-color: #fadbd8; margin: 15px 0; border-radius: 0 5px 5px 0; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                <h3 style="color: #c0392b; margin-top: 0; font-size: 18px;">Error Processing Request</h3>
                <p style="margin: 10px 0; line-height: 1.5;">%s</p>
                <p style="margin: 10px 0; line-height: 1.5;">This may be due to a connection timeout with the OpenAI API. Please try again or check your network connection.</p>
            </div>""";

    private static final String GENERATOR_SYSTEM_PROMPT = """
            You are a Kubernetes management assistant with access to specialized tools for managing Kubernetes resources. 
            Your role is to help users manage their Kubernetes cluster by utilizing the provided tool methods directly. 
            
            Important Rules:
            1. NEVER generate or suggest kubectl commands - use the provided tool methods instead
            2. Always use the appropriate tool methods for each operation (e.g., PodTools for pod operations, NetworkTools for network operations)
            3. Ensure operations are safe
            4. When managing resources, always verify the target namespace
            5. For complex operations, break them down into smaller, manageable steps
            6. If namespace is not explicitly specified, use the default namespace
            7. Be concise in your response and try and provide only the necessary information
            
            CRITICAL FORMATTING REQUIREMENT: You MUST format your ENTIRE response as valid HTML with proper styling.
            
            HTML FORMATTING RULES (MANDATORY):
            1. Your COMPLETE response must be valid HTML - DO NOT include any markdown or plain text outside HTML tags
            2. ALWAYS wrap your entire response in a root <div> with appropriate styling
            3. Use semantic HTML elements appropriately:
               - <h1>, <h2>, <h3> for headings (with proper hierarchy)
               - <p> for paragraphs
               - <ul>/<ol> with <li> for lists
               - <table> with <thead>, <tbody>, <tr>, <th>, <td> for tabular data
               - <pre><code> for code blocks with syntax highlighting
               - <strong>, <em>, <span> for text emphasis
            4. Apply consistent styling with inline CSS:
               - Use a clean, professional color scheme
               - Set font-family, font-size, line-height, and margins for readability
               - Use contrasting colors for headings and important information
               - Apply borders, padding, and background colors to create visual separation
            5. For code or command examples:
               - Always use <pre><code> tags with monospace font and syntax highlighting
               - Add background color and padding for better readability
            6. For tables:
               - Use proper table structure with <thead> and <tbody>
               - Apply alternating row colors and border styling
               - Center-align headers and appropriate align data cells
            7. For status information:
               - Use color-coding (<span> with appropriate colors) for success/warning/error states
               - Use icons or symbols for visual indicators when appropriate
            
            EXAMPLE HTML STRUCTURE (follow this pattern):
            <div style="font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.6; color: #333; max-width: 800px; margin: 0 auto; padding: 20px;">
              <h2 style="color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 10px;">Main Heading</h2>
              <p>Explanatory text goes here with <strong>important points</strong> highlighted.</p>
              <div style="background-color: #f8f9fa; border-left: 4px solid #3498db; padding: 15px; margin: 15px 0;">
                <h3 style="margin-top: 0; color: #2c3e50;">Section Heading</h3>
                <p>Section content with details about the Kubernetes resources.</p>
                <ul style="margin-left: 20px;">
                  <li>List item one with details</li>
                  <li>List item two with details</li>
                </ul>
              </div>
              <pre style="background-color: #f5f5f5; padding: 15px; border-radius: 5px; overflow-x: auto;"><code>Example code or output here</code></pre>
            </div>
            
            Available Tool Categories:
            - Pod Management
            - Node Operations
            - Service Management
            - Storage Operations
            - Scheduling
            - Deployments
            - ConfigMaps and Secrets
            - Network Management
            - Resource Management
            - Jobs and Batch Operations
            - Event Monitoring
            - Health Checks
            - Helm Operations
            
            Remember: Security and stability are paramount. Always validate inputs and handle errors appropriately.
            """;

    private static final String EVALUATOR_SYSTEM_PROMPT = """
            You are an expert evaluator for Kubernetes management responses. Your task is to assess if the given response 
            properly addresses the user's request using the appropriate Kubernetes management tools.
            
            Evaluate the response based on these criteria:
            1. Does it use the appropriate tool methods instead of kubectl commands?
            2. Does it address all aspects of the user's request?
            3. Is it safe and does it verify namespaces when needed?
            4. Is it concise and provides only necessary information?
            5. Does it handle potential errors appropriately?
            6. HTML FORMATTING (CRITICAL): Is the response properly formatted as valid HTML with appropriate styling?
               - The entire response must be valid HTML (not markdown)
               - Must use proper semantic HTML elements (h1-h6, p, ul/ol, table, pre/code, etc.)
               - Must have consistent, professional styling with inline CSS
               - Must be visually well-structured and readable
               - Must not contain any plain text outside of HTML tags
            
            Provide your evaluation in the following format:
            - RATING: [PASS or NEEDS_IMPROVEMENT]
            - FEEDBACK: [Detailed feedback explaining issues and suggestions for improvement]
            
            If the response needs improvement, be specific about what needs to be fixed and how.
            
            IMPORTANT: If the response is not properly formatted as HTML or contains markdown instead of HTML, 
            ALWAYS rate it as NEEDS_IMPROVEMENT and provide specific feedback on the HTML formatting issues.
            """;

    private final ChatClient.Builder chatBuilder;
    private final ToolCallbackProvider tools;

    public AgentService(ChatClient.Builder chatBuilder, ToolCallbackProvider tools) {
        this.chatBuilder = chatBuilder;
        this.tools = tools;
    }

    public String invokeAgent(String userPrompt) {
        try {
            return evaluatorOptimizerLoop(userPrompt);
        } catch (Exception e) {
            e.printStackTrace();
            return String.format(ERROR_HTML_TEMPLATE, e.getMessage());
        }
    }

    private String evaluatorOptimizerLoop(String userPrompt) throws InterruptedException {
        String currentResponse = null;
        int iterationCount = 0;
        StringBuilder chainOfThought = new StringBuilder();

        while (iterationCount < MAX_ITERATIONS) {
            iterationCount++;

            currentResponse = generateResponse(
                    userPrompt,
                    currentResponse,
                    chainOfThought
            );

            if (iterationCount == MAX_ITERATIONS) {
                chainOfThought.append("\n\nIteration ").append(iterationCount).append(" (final):\n").append(currentResponse);
                break;
            }

            String evaluation = evaluateResponse(userPrompt, currentResponse);
            chainOfThought.append("\n\nIteration ").append(iterationCount).append(":\n").append(currentResponse)
                    .append("\n\nEvaluation:\n").append(evaluation);

            if (evaluation.contains("RATING: PASS")) {
                break;
            }
        }

        return ensureHtmlFormat(currentResponse != null ? currentResponse : "Failed to generate a response");
    }

    private String generateResponse(
            String userPrompt,
            String currentResponse,
            StringBuilder chainOfThought
    ) throws InterruptedException {
        String generationPrompt;
        if (currentResponse == null) {
            generationPrompt = userPrompt;
        } else {
            String feedback = chainOfThought.toString().split("FEEDBACK:")[1].trim();
            generationPrompt = """
                Original user request: %s
                
                Your previous response: %s
                
                Feedback on your previous response: %s
                
                Please provide an improved response that addresses the feedback.
                """.formatted(userPrompt, currentResponse, feedback);
        }

        int retryCount = 0;
        String generatedResponse = null;

        while (generatedResponse == null) {
            try {
                generatedResponse = chatBuilder.build().prompt()
                        .user(generationPrompt)
                        .system(GENERATOR_SYSTEM_PROMPT)
                        .tools(tools)
                        .call().content();
            } catch (Exception e) {
                retryCount++;
                if (retryCount >= MAX_ITERATIONS) throw e;

                System.out.printf("API call failed (attempt %d/%d): %s. Retrying...%n",
                        retryCount, MAX_ITERATIONS, e.getMessage());
                Thread.sleep(calculateBackoffMs(retryCount));
            }
        }

        return generatedResponse != null ? generatedResponse : "No response generated";
    }

    private String evaluateResponse(String userPrompt, String currentResponse) throws InterruptedException {
        String evaluationPrompt = """
            User request: %s
            
            Response to evaluate: %s
            
            Evaluate if this response properly addresses the user's request.
            """.formatted(userPrompt, currentResponse);

        int evalRetryCount = 0;
        String evaluation = null;

        while (evaluation == null) {
            try {
                evaluation = chatBuilder.build().prompt()
                        .user(evaluationPrompt)
                        .system(EVALUATOR_SYSTEM_PROMPT)
                        .call().content();
            } catch (Exception e) {
                evalRetryCount++;
                if (evalRetryCount >= MAX_RETRIES) {
                    System.out.printf("Evaluation API call failed after %d attempts: %s. Continuing with current response.%n",
                            MAX_RETRIES, e.getMessage());
                    return "RATING: PASS\nFEEDBACK: Unable to evaluate due to API timeout, but continuing with current response.";
                }

                System.out.printf("Evaluation API call failed (attempt %d/%d): %s. Retrying...%n",
                        evalRetryCount, MAX_RETRIES, e.getMessage());
                Thread.sleep(calculateBackoffMs(evalRetryCount));
            }
        }

        return evaluation;
    }

    private long calculateBackoffMs(int retryCount) {
        return (long) (1000L * Math.pow(2, retryCount));
    }

    private String ensureHtmlFormat(String response) {
        String trimmedResponse = response.trim();

        if (trimmedResponse.startsWith("<") &&
                (trimmedResponse.endsWith(">") || trimmedResponse.endsWith("</div>")) &&
                trimmedResponse.contains("<div")) {

            if (validateHtmlStructure(trimmedResponse)) {
                return trimmedResponse;
            }
        }

        String convertedHtml = convertMarkdownToHtml(trimmedResponse);

        return """
            <div style="font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.6; color: #333; max-width: 800px; margin: 0 auto; padding: 20px;">
                %s
            </div>
            """.formatted(convertedHtml);
    }

    private boolean validateHtmlStructure(String html) {
        return html.contains("<div") &&
                html.contains("</div>") &&
                !html.contains("```") &&
                !html.contains("\n#") &&
                !html.contains("\n-");
    }

    private String convertMarkdownToHtml(String markdown) {
        String html = markdown;

        // Convert code blocks
        Pattern codeBlockPattern = Pattern.compile("```([\\w]*)\\n([\\s\\S]*?)\\n```");
        Matcher codeBlockMatcher = codeBlockPattern.matcher(html);
        html = codeBlockMatcher.replaceAll(matchResult -> {
            String language = matchResult.group(1);
            String code = matchResult.group(2);
            return """
                <pre style="background-color: #f5f5f5; padding: 15px; border-radius: 5px; overflow-x: auto;">
                    <code class="language-%s">%s</code>
                </pre>
                """.formatted(language, code);
        });

        // Convert inline code
        Pattern inlineCodePattern = Pattern.compile("`([^`]+)`");
        Matcher inlineCodeMatcher = inlineCodePattern.matcher(html);
        html = inlineCodeMatcher.replaceAll(matchResult -> {
            String code = matchResult.group(1);
            return """
                <code style="background-color: #f5f5f5; padding: 2px 4px; border-radius: 3px; font-family: monospace;">
                    %s
                </code>
                """.formatted(code);
        });

        // Convert headers
        html = convertMarkdownHeaders(html);

        // Convert bullet lists
        html = convertMarkdownLists(html);

        // Convert paragraphs
        html = convertMarkdownParagraphs(html);

        // Convert bold
        Pattern boldPattern = Pattern.compile("\\*\\*([^*]+)\\*\\*");
        Matcher boldMatcher = boldPattern.matcher(html);
        html = boldMatcher.replaceAll("<strong>$1</strong>");

        // Convert italic
        Pattern italicPattern = Pattern.compile("\\*([^*]+)\\*");
        Matcher italicMatcher = italicPattern.matcher(html);
        html = italicMatcher.replaceAll("<em>$1</em>");

        return html;
    }

    private String convertMarkdownHeaders(String html) {
        // h1
        Pattern h1Pattern = Pattern.compile("^# (.+)$", Pattern.MULTILINE);
        Matcher h1Matcher = h1Pattern.matcher(html);
        html = h1Matcher.replaceAll(matchResult -> {
            String header = matchResult.group(1);
            return """
                <h1 style="color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 10px;">
                    %s
                </h1>
                """.formatted(header);
        });

        // h2
        Pattern h2Pattern = Pattern.compile("^## (.+)$", Pattern.MULTILINE);
        Matcher h2Matcher = h2Pattern.matcher(html);
        html = h2Matcher.replaceAll(matchResult -> {
            String header = matchResult.group(1);
            return """
                <h2 style="color: #2c3e50; border-bottom: 1px solid #3498db; padding-bottom: 8px;">
                    %s
                </h2>
                """.formatted(header);
        });

        // h3
        Pattern h3Pattern = Pattern.compile("^### (.+)$", Pattern.MULTILINE);
        Matcher h3Matcher = h3Pattern.matcher(html);
        html = h3Matcher.replaceAll(matchResult -> {
            String header = matchResult.group(1);
            return """
                <h3 style="color: #2c3e50; margin-top: 15px;">
                    %s
                </h3>
                """.formatted(header);
        });

        return html;
    }

    private String convertMarkdownLists(String html) {
        // Convert bullet items
        Pattern listItemPattern = Pattern.compile("^- (.+)$", Pattern.MULTILINE);
        Matcher listItemMatcher = listItemPattern.matcher(html);
        html = listItemMatcher.replaceAll("<li>$1</li>");

        // Wrap lists in ul tags
        if (html.contains("<li>")) {
            Pattern listPattern = Pattern.compile("(<li>.*?</li>)+", Pattern.DOTALL);
            Matcher listMatcher = listPattern.matcher(html);
            html = listMatcher.replaceAll("<ul style=\"margin-left: 20px;\">$0</ul>");
        }

        return html;
    }

    private String convertMarkdownParagraphs(String html) {
        Pattern paragraphPattern = Pattern.compile("^([^<].+)$", Pattern.MULTILINE);
        Matcher paragraphMatcher = paragraphPattern.matcher(html);
        html = paragraphMatcher.replaceAll(matchResult -> {
            String line = matchResult.group(1);
            if (!line.isBlank() && !line.startsWith("<")) {
                return "<p>" + line + "</p>";
            }
            return line;
        });
        return html;
    }
}