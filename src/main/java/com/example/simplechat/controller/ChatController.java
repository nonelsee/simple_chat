package com.example.simplechat.controller;

import com.example.simplechat.model.Message;
import com.example.simplechat.model.User;
import com.example.simplechat.service.MessageService;
import com.example.simplechat.service.UserService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class ChatController {

    @Autowired
    private UserService userService;

    @Autowired
    private MessageService messageService;

    // Map to hold long-polling requests
    // Key: username, Value: DeferredResult for their pending messages
    private final ConcurrentHashMap<String, DeferredResult<List<Message>>> longPollingRequests = new ConcurrentHashMap<>();

    // A scheduler to check for messages periodically and resolve DeferredResult
    private final java.util.concurrent.ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Initialize scheduler to check for new messages every 100ms
    // In a real system, you would use a messaging queue or more direct notification.
    @PostConstruct
    private void setupScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            longPollingRequests.forEach((username, deferredResult) -> {
                // Check if the user is still online (has an active long polling request)
                if (!deferredResult.isSetOrExpired()) {
                    List<Message> newMessages = messageService.getNewMessages(username);
                    if (!newMessages.isEmpty()) {
                        deferredResult.setResult(newMessages);
                        longPollingRequests.remove(username); // Request resolved
                    }
                } else {
                    // Remove expired requests
                    longPollingRequests.remove(username);
                    userService.setOnlineStatus(username, false); // Mark user as offline if request expires
                }
            });
        }, 0, 100, TimeUnit.MILLISECONDS); // Check every 100ms
    }

    @PreDestroy
    private void cleanupScheduler() {
        scheduler.shutdown();
    }


    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body("Username and password are required.");
        }

        String accessToken = userService.login(username, password);
        if (accessToken != null) {
            return ResponseEntity.ok().body(Map.of("accessToken", accessToken));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials.");
    }

    @GetMapping("/friends")
    public ResponseEntity<?> getFriends(@RequestHeader("Access-Token") String accessToken) {
        Optional<User> userOptional = userService.validateAccessToken(accessToken);
        if (userOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired access token.");
        }
        User user = userOptional.get();
        List<String> friends = userService.getFriends(user.getUsername());
        return ResponseEntity.ok().body(Map.of("friends", friends));
    }

    @PostMapping(value = "/send-message", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<?> sendMessage(
            @RequestHeader("Access-Token") String accessToken,
            @RequestParam("receiver") String receiver,
            @RequestParam(value = "message", required = false) String messageContent,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        Optional<User> userOptional = userService.validateAccessToken(accessToken);
        if (userOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired access token.");
        }
        User sender = userOptional.get();

        if (messageContent == null && (file == null || file.isEmpty())) {
            return ResponseEntity.badRequest().body("Message content or file is required.");
        }

        int status = messageService.sendMessage(sender.getUsername(), receiver, messageContent, file);

        switch (status) {
            case 1: return ResponseEntity.ok().body(Map.of("status", 1, "message", "Message sent, receiver online."));
            case 2: return ResponseEntity.ok().body(Map.of("status", 2, "message", "Message queued, receiver offline."));
            case 3: return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", 3, "message", "Sender not in receiver's friend list."));
            default: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("status", -1, "message", "Error sending message."));
        }
    }

    @GetMapping("/get-new-messages")
    public DeferredResult<List<Message>> getNewMessages(@RequestHeader("Access-Token") String accessToken) {
        DeferredResult<List<Message>> deferredResult = new DeferredResult<>(10000L, Collections.emptyList()); // 10 seconds timeout, empty list on timeout

        Optional<User> userOptional = userService.validateAccessToken(accessToken);
        if (userOptional.isEmpty()) {
            deferredResult.setResult(Collections.emptyList()); // Or throw an exception for UNAUTHORIZED
            return deferredResult;
        }
        User currentUser = userOptional.get();
        String username = currentUser.getUsername();

        userService.setOnlineStatus(username, true); // Mark user as online for long polling

        // First, check for any immediate messages
        List<Message> immediateMessages = messageService.getNewMessages(username);
        if (!immediateMessages.isEmpty()) {
            deferredResult.setResult(immediateMessages);
            userService.setOnlineStatus(username, false); // Mark offline if all messages delivered immediately
        } else {
            // No immediate messages, put the request into the map for long polling
            longPollingRequests.put(username, deferredResult);

            // Set a completion handler to remove the request when it's done (either by timeout or by result)
            deferredResult.onCompletion(() -> {
                longPollingRequests.remove(username);
                userService.setOnlineStatus(username, false); // Mark offline after completion
            });

            // Set a timeout handler
            deferredResult.onTimeout(() -> {
                longPollingRequests.remove(username);
                userService.setOnlineStatus(username, false); // Mark offline on timeout
                deferredResult.setResult(Collections.emptyList());
            });
        }
        return deferredResult;
    }

    @GetMapping("/files/{filename}")
    public ResponseEntity<Resource> downloadFile(
            @RequestHeader("Access-Token") String accessToken,
            @PathVariable String filename) {

        Optional<User> userOptional = userService.validateAccessToken(accessToken);
        if (userOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Path filePath = messageService.getFilePath(filename, userOptional.get().getUsername());
            if (filePath == null || !java.nio.file.Files.exists(filePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            String contentType = Files.probeContentType(filePath);
            if(contentType == null) {
                contentType = "application/octet-stream"; // Default if type cannot be determined
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            System.err.println("Error downloading file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}