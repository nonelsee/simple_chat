package com.example.simplechat.service;

import com.example.simplechat.model.Message;
import com.example.simplechat.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserService userService; // To check online status and notify

    // Queue for messages awaiting long polling
    // Key: receiver username, Value: Queue of messages
    private final ConcurrentHashMap<String, LinkedBlockingQueue<Message>> messageQueues = new ConcurrentHashMap<>();

    private String STORAGE_ROOT = "src/main/resources/storage/";

    public MessageService() {
        // Ensure storage directory exists
        try {
            Files.createDirectories(Paths.get(STORAGE_ROOT));
        } catch (IOException e) {
            System.err.println("Failed to create storage directory: " + e.getMessage());
        }
    }

    public int sendMessage(String sender, String receiver, String content, MultipartFile file) {
        // Check if sender is friend of receiver
        if (!userService.isFriend(sender, receiver)) {
            return 3; // Not friends
        }

        Message message = new Message();
        message.setId(UUID.randomUUID().toString());
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setTimestamp(LocalDateTime.now());
        message.setRead(false);

        if (file != null && !file.isEmpty()) {
            try {
                String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
                Path filePath = Paths.get(STORAGE_ROOT, fileName);
                Files.copy(file.getInputStream(), filePath);
                message.setFileLink("/files/" + fileName); // Link for download
                message.setContent("[FILE]"); // Indicate it's a file
            } catch (IOException e) {
                System.err.println("Failed to save file: " + e.getMessage());
                return -1; // Indicate error
            }
        } else {
            message.setContent(content);
        }

        messageRepository.save(message); // Save message to JSON DB

        // If receiver is online (long polling active), deliver immediately
        if (userService.isUserOnline(receiver)) {
            // Add message to receiver's queue
            messageQueues.computeIfAbsent(receiver, k -> new LinkedBlockingQueue<>()).offer(message);
            // In a real scenario, you'd trigger the long-polling response here.
            // For now, it will be picked up by the next long poll.
            return 1; // Receiver online
        } else {
            // Add message to receiver's queue for later retrieval
            messageQueues.computeIfAbsent(receiver, k -> new LinkedBlockingQueue<>()).offer(message);
            return 2; // Receiver offline, message queued
        }
    }

    public List<Message> getNewMessages(String username) {
        // Get all unread messages from the main repository for this user
        List<Message> unreadFromDb = messageRepository.findUnreadMessagesForUser(username);

        // Also take messages from the real-time queue
        LinkedBlockingQueue<Message> userQueue = messageQueues.get(username);
        List<Message> newMessages = new java.util.ArrayList<>();
        if (userQueue != null) {
            userQueue.drainTo(newMessages); // Move all messages from queue to list
        }

        // Combine and return. Mark messages as read in the DB
        if (!unreadFromDb.isEmpty()) {
            newMessages.addAll(unreadFromDb);
        }

        if (!newMessages.isEmpty()) {
            // Mark these messages as read in the "database"
            messageRepository.markMessagesAsRead(newMessages);
        }

        return newMessages;
    }

    public Path getFilePath(String filename, String requestingUser) throws IOException {
        Path filePath = Paths.get(STORAGE_ROOT, filename).normalize();

        // Security check: Ensure file is within the storage directory
        if (!filePath.startsWith(Paths.get(STORAGE_ROOT).normalize())) {
            throw new IOException("Attempted directory traversal: " + filename);
        }

        // In a real app, you'd also check if requestingUser has permission to this file.
        // For simplicity, we assume if you know the file name, you can access it,
        // but in a real chat, files are tied to messages and specific chats.
        // A more robust check would involve checking if 'requestingUser' was a participant
        // in the message that generated this file.
        // For this simple example, we are not storing message-to-file ownership granularly.

        if (!Files.exists(filePath)) {
            return null;
        }
        return filePath;
    }
}