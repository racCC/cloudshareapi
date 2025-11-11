package in.rachitpednekar.cloudshareapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.rachitpednekar.cloudshareapi.dto.ProfileDTO;
import in.rachitpednekar.cloudshareapi.service.ProfileService;
import in.rachitpednekar.cloudshareapi.service.UserCreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.svix.Webhook;
import com.svix.exceptions.WebhookVerificationException;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpHeaders;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor

public class ClerkWebhookController {
    @Value("${clerk.webhook.secret}")
    private String webhookSecret;
    private final ProfileService profileService;
    private final UserCreditService userCreditService;


    @PostMapping("/clerk")
    public ResponseEntity<?> handleClerkWebhook(
            @RequestHeader Map<String, String> headers,
            @RequestBody String payload
    ) {
        try {
            boolean isValid = verifyWebhookSignature(payload, headers);
            if (!isValid) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid webhook signature");
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(payload);
            String eventType = rootNode.path("type").asText();

            switch (eventType) {
                case "user.created" -> handleUserCreated(rootNode.path("data"));
                case "user.updated" -> handleUserUpdated(rootNode.path("data"));
                case "user.deleted" -> handleUserDeleted(rootNode.path("data"));
                default -> System.out.println("⚠️ Unhandled event type: " + eventType);
            }

            return ResponseEntity.ok("Webhook processed");
        } catch (WebhookVerificationException e) {
            System.err.println("❌ Signature verification failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Signature verification failed");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Server error: " + e.getMessage());
        }
    }


    private void handleUserDeleted(JsonNode data) {
        String clerkId=data.path("id").asText();
        profileService.deleteProfile(clerkId);

    }

    private void handleUserUpdated(JsonNode data) {
        String clerkId=data.path("id").asText();
        String email="";
        JsonNode emailAddresses=data.path("email_addresses");
        if(emailAddresses.isArray() && emailAddresses.size()>0){
            email=emailAddresses.get(0).path("email_address").asText();

        }
        String firstName=data.path("first_name").asText("");
        String lastName=data.path("last_name").asText("");
        String photoUrl=data.path("image_url").asText("");

        ProfileDTO updatedProfile=ProfileDTO.builder()
                .clerkId(clerkId)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .photoUrl(photoUrl)
                .build();


        updatedProfile=profileService.updateProfile(updatedProfile);
        if(updatedProfile==null){
            handleUserCreated(data);
        }



    }

    private void handleUserCreated(JsonNode data) {
        String clerkId=data.path("id").asText();
        String email="";
        JsonNode emailAddresses=data.path("email_addresses");
        if(emailAddresses.isArray() && emailAddresses.size()>0){
            email=emailAddresses.get(0).path("email_address").asText();

        }
        String firstName=data.path("first_name").asText("");
        String lastName=data.path("last_name").asText("");
        String photoUrl=data.path("image_url").asText("");
        ProfileDTO newProfile=ProfileDTO.builder()
                .clerkId(clerkId)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .photoUrl(photoUrl)
                .build();
        profileService.createProfile(newProfile);
        userCreditService.createInitialCredits(clerkId);




    }

    private boolean verifyWebhookSignature(String payload, Map<String, String> headers)
            throws WebhookVerificationException {
        Webhook webhook = new Webhook(webhookSecret);

        // Convert Map<String, String> → Map<String, List<String>>
        Map<String, List<String>> formattedHeaders = headers.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> List.of(e.getValue())
                ));

        // Build HttpHeaders
        HttpHeaders httpHeaders = HttpHeaders.of(formattedHeaders, (k, v) -> true);

        // ✅ New API: verify using full headers object
        webhook.verify(payload, httpHeaders);
        return true;
    }



}
