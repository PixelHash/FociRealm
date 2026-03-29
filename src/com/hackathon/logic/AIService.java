package com.hackathon.logic;

import dev.langchain4j.model.ollama.OllamaChatModel;
import java.time.Duration;

public class AIService {
    private final OllamaChatModel model;

    public AIService() {
        // Configure the local model connection
        this.model = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3.2")
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    public String ask(String prompt) {
        try {
            return model.generate(prompt);
        } catch (Exception e) {
            return "Error: Could not reach local AI. Is Ollama running?";
        }
    }

    public String summarize(String content) {
        String prompt = "Summarize this in one short sentence for a project dashboard: " + content;
        return ask(prompt);
    }

    public String smartFormat(String content) {
    String prompt = """
                    Act as a professional code and text formatter. Reformat the following content to be clean, well-indented, and readable. Return ONLY the reformatted text without any conversational filler: 
                    """ + content;
    return ask(prompt);
}
}