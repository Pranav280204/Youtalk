package com.example.demo1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.cognitiveservices.speech.*;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import org.springframework.web.bind.annotation.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "chrome-extension://cpngpddlmhemmecdofeahdibhbdpdleh")
public class ApiController {

    private static final String ytDlpPath = "C:\\Users\\Pranav\\AppData\\Roaming\\Python\\Python312\\Scripts\\yt-dlp.exe";
    private static final String ffmpegLocation = "C:\\ffmpeg";
    private static final String speechSubscriptionKey = "6Ki4n1cYiPl3UhtYaFqx4yLKHwuqSVCzjfxPEN945IYbB77hITBlJQQJ99AKACGhslBXJ3w3AAAYACOGUEQy";
    private static final String speechServiceRegion = "centralindia";

    @PostMapping("/process")
    public String processVideo(@RequestParam("url") String youtubeUrl, @RequestParam("question") String question) {
        String audioFilePath = "downloaded_audio.wav";

        // Step 1: Download and convert video
        String downloadResult = downloadAndConvertVideo(youtubeUrl, audioFilePath);
        System.out.println("Download Result : " + downloadResult);
        if (!"Success".equals(downloadResult)) {
            return "Error: " + downloadResult;
        }

        // Step 2: Transcribe the audio
        String transcription = transcribeAudioWithAzure(audioFilePath);
        System.out.println("trans : " + transcription);
        if (transcription.isEmpty()) {
            return "Error: Failed to transcribe the audio.";
        }

        // Step 3: Get the answer from OpenAI
        return getAnswer(transcription, question);
    }
    @PostMapping("/getSummary")
    public String getSummary(@RequestParam("url") String youtubeUrl) {
        String audioFilePath = "downloaded_audio.wav";

        // Step 1: Download and convert video
        String downloadResult = downloadAndConvertVideo(youtubeUrl, audioFilePath);
        if (!"Success".equals(downloadResult)) {
            return "Error: " + downloadResult;
        }

        // Step 2: Transcribe the audio
        String transcription = transcribeAudioWithAzure(audioFilePath);
        if (transcription.isEmpty()) {
            return "Error: Failed to transcribe the audio.";
        }

        // Step 3: Get the summary from Gemini
        return getSummaryFromGemini(transcription);
    }

    private String downloadAndConvertVideo(String youtubeUrl, String audioFilePath) {
        try {
            Process process = new ProcessBuilder(ytDlpPath, "-x", "--audio-format", "wav", youtubeUrl, "-o", audioFilePath, "--ffmpeg-location", ffmpegLocation).start();
            if (process.waitFor() == 0) {
                File audioFile = new File(audioFilePath);
                return audioFile.exists() ? "Success" : "Audio file not found.";
            } else {
                return "yt-dlp process failed.";
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "Exception occurred during video download or conversion: " + e.getMessage();
        }
    }

    private String transcribeAudioWithAzure(String audioFilePath) {
        StringBuilder finalText = new StringBuilder();
        SpeechConfig speechConfig = SpeechConfig.fromSubscription(speechSubscriptionKey, speechServiceRegion);
        AudioConfig audioConfig = AudioConfig.fromWavFileInput(audioFilePath);
        SpeechRecognizer recognizer = new SpeechRecognizer(speechConfig, audioConfig);

        try {
            // Event listener to capture recognized text
            recognizer.recognized.addEventListener((s, e) -> {
                if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                    finalText.append(e.getResult().getText()).append(" ");
                }
            });

            recognizer.canceled.addEventListener((s, e) -> {
                System.err.println("Recognition canceled: " + e.getErrorDetails());
            });

            recognizer.sessionStopped.addEventListener((s, e) -> {
                System.out.println("Session stopped.");
            });

            // Start recognition
            recognizer.startContinuousRecognitionAsync().get();

            // Wait for recognition to complete
            Thread.sleep(30000); // Adjust this as per the length of your audio

            // Stop recognition after timeout
            recognizer.stopContinuousRecognitionAsync().get();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            recognizer.close();
            speechConfig.close();
            audioConfig.close();
        }

        return finalText.toString().trim();
    }

    private String getAnswer(String transcription, String question) {
        try {
            HttpClient client = HttpClient.newHttpClient();

            // Refined question with clear instruction for Gemini
            String refinedQuestion = String.format(
                    "The following transcription is of a YouTube video.Also consider this text as video only don't write text. The transcription is: '%s'. Now, answer the following question based on the transcription: '%s'. Please provide only the answer in one sentence without extra explanation.",
                    transcription,
                    question
            );
            // Format the prompt for Gemini API
            String jsonRequest = String.format("{\"contents\":[{\"parts\":[{\"text\":\"%s\"}]}]}", refinedQuestion);

            // Send POST request to Gemini API
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=AIzaSyBBivzUC_FbwzUJPsNFHzR3X8i_NLHhwYg"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            // Log the response for debugging
            System.out.println("Response from Gemini: " + responseBody);

            // Parse the response from Gemini
            JsonNode jsonResponse = new ObjectMapper().readTree(responseBody);

            // Check for errors in the response
            JsonNode errorNode = jsonResponse.get("error");
            if (errorNode != null) {
                return "Error: " + errorNode.get("message").asText();
            }

            // Check for the "candidates" field in the response
            JsonNode candidates = jsonResponse.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return "Error: No candidates found in the response.";
            }

            // Extract the content from the first candidate
            JsonNode content = candidates.get(0).get("content");
            if (content == null) {
                return "Error: No content found in the first candidate.";
            }

            // Extract the text from the content
            JsonNode parts = content.get("parts");
            if (parts == null || parts.isEmpty()) {
                return "Error: No parts found in the content.";
            }

            String answer = parts.get(0).get("text").asText();
            return answer;
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: Unable to fetch answer from Gemini.";
        }
    }
    private String getSummaryFromGemini(String transcription) {
        try {
            HttpClient client = HttpClient.newHttpClient();

            // Refined prompt for summarization
            String refinedQuestion = String.format(
                    "The following is a transcription from a YouTube video: '%s'. Now, summarize the content of this video in a few sentences.",
                    transcription
            );

            // Format the prompt for Gemini API
            String jsonRequest = String.format("{\"contents\":[{\"parts\":[{\"text\":\"%s\"}]}]}", refinedQuestion);

            // Send POST request to Gemini API
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=AIzaSyBBivzUC_FbwzUJPsNFHzR3X8i_NLHhwYg"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            // Parse the response from Gemini
            JsonNode jsonResponse = new ObjectMapper().readTree(responseBody);

            JsonNode errorNode = jsonResponse.get("error");
            if (errorNode != null) {
                return "Error: " + errorNode.get("message").asText();
            }

            JsonNode candidates = jsonResponse.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return "Error: No candidates found in the response.";
            }

            JsonNode content = candidates.get(0).get("content");
            if (content == null) {
                return "Error: No content found in the first candidate.";
            }

            JsonNode parts = content.get("parts");
            if (parts == null || parts.isEmpty()) {
                return "Error: No parts found in the content.";
            }

            return parts.get(0).get("text").asText();

        } catch (Exception e) {
            e.printStackTrace();
            return "Error: Unable to fetch summary from Gemini.";
        }
    }

}
