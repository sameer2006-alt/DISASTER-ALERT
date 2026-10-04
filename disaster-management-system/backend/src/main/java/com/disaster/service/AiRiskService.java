package com.disaster.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class AiRiskService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String GROK_API_KEY = "";

    public AiThreatAnalysis analyzeThreat(double lat, double lng) {
        if (GROK_API_KEY == null || GROK_API_KEY.isBlank()) {
            return getMockData(lat, lng);
        }
        try {
            String prompt = String.format(
                "You are a GIS and natural disaster analysis agent. Given a geographic coordinate in India (latitude: %f, longitude: %f), analyze the potential disaster threats (monsoon floods, landslides, cyclones, seismic risk, etc.) for this area. " +
                "Return ONLY a valid JSON object matching the following structure (do not include markdown wrapping, backticks, or extra text):\n" +
                "{\n" +
                "  \"confidenceScore\": 85,\n" +
                "  \"floodProbability\": 90,\n" +
                "  \"affectedPopulation\": 1200000,\n" +
                "  \"evacuationUrgency\": \"CRITICAL\",\n" +
                "  \"expansionRadius\": 20\n" +
                "}",
                lat, lng
            );

            Map<String, Object> requestBody = Map.of(
                "model", "llama-3.3-70b-versatile",
                "messages", new Object[]{
                    Map.of("role", "user", "content", prompt)
                },
                "temperature", 0.2
            );

            String requestJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + GROK_API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String responseBody = response.body();
                Map<?, ?> responseMap = objectMapper.readValue(responseBody, Map.class);
                java.util.List<?> choices = (java.util.List<?>) responseMap.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                    Map<?, ?> message = (Map<?, ?>) choice.get("message");
                    if (message != null) {
                        String content = (String) message.get("content");
                        if (content != null) {
                            content = content.trim();
                            // Strip markdown wrapper if the model includes it
                            if (content.startsWith("```")) {
                                int firstLineBreak = content.indexOf('\n');
                                int lastBackticks = content.lastIndexOf("```");
                                if (firstLineBreak != -1 && lastBackticks != -1 && lastBackticks > firstLineBreak) {
                                    content = content.substring(firstLineBreak, lastBackticks).trim();
                                }
                            }
                            
                            Map<?, ?> parsed = objectMapper.readValue(content, Map.class);
                            int confidenceScore = parsed.containsKey("confidenceScore") ? ((Number) parsed.get("confidenceScore")).intValue() : 80;
                            int floodProbability = parsed.containsKey("floodProbability") ? ((Number) parsed.get("floodProbability")).intValue() : 70;
                            int affectedPopulation = parsed.containsKey("affectedPopulation") ? ((Number) parsed.get("affectedPopulation")).intValue() : 500000;
                            String evacuationUrgency = parsed.containsKey("evacuationUrgency") ? (String) parsed.get("evacuationUrgency") : "MODERATE";
                            int expansionRadius = parsed.containsKey("expansionRadius") ? ((Number) parsed.get("expansionRadius")).intValue() : 15;
                            
                            return new AiThreatAnalysis(confidenceScore, floodProbability, affectedPopulation, evacuationUrgency, expansionRadius);
                        }
                    }
                }
            } else {
                System.err.println("Grok API error status: " + response.statusCode() + " body: " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Grok API call failed, falling back to simulation. Error: " + e.getMessage());
        }

        return getMockData(lat, lng);
    }

    private AiThreatAnalysis getMockData(double lat, double lng) {
        double confidence = 82 + (Math.random() * 15);
        double floodProb = 65 + (Math.random() * 30);
        int population = (int) (100_000 + Math.random() * (2_000 * 1_000));
        String urgency = floodProb > 85 ? "CRITICAL" : (floodProb > 70 ? "HIGH" : "MODERATE");
        double expansion = 10 + (Math.random() * 40);

        return new AiThreatAnalysis(
            (int) confidence,
            (int) floodProb,
            population,
            urgency,
            (int) expansion
        );
    }

    public record AiThreatAnalysis(int confidenceScore, int floodProbability, int affectedPopulation, String evacuationUrgency, int expansionRadius) {}
}
