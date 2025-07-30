package com.example.simplechat.repository;

import com.example.simplechat.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class MessageRepository {

    private static final String MESSAGES_DB_PATH = "messages.json";
    private List<Message> messages;
    private final ObjectMapper objectMapper;
    private File messagesFile;

    public MessageRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    private void init() {
        try {
            messagesFile = new ClassPathResource(MESSAGES_DB_PATH).getFile();
            if (messagesFile.exists() && messagesFile.length() > 0) {
                Message[] messageArray = objectMapper.readValue(messagesFile, Message[].class);
                messages = new ArrayList<>(Arrays.asList(messageArray));
            } else {
                messages = new ArrayList<>();
                objectMapper.writeValue(messagesFile, messages);
            }
        } catch (IOException e) {
            System.err.println("Error initializing messages database: " + e.getMessage());
            messages = new ArrayList<>();
        }
    }

    public void save(Message message) {
        messages.add(message);
        try {
            objectMapper.writeValue(messagesFile, messages);
        } catch (IOException e) {
            System.err.println("Error saving message to database: " + e.getMessage());
        }
    }

    public List<Message> findUnreadMessagesForUser(String username) {
        return messages.stream()
                .filter(m -> m.getReceiver().equals(username) && !m.isRead())
                .collect(Collectors.toList());
    }

    public void markMessagesAsRead(List<Message> msgs) {
        msgs.forEach(m -> {
            m.setRead(true);
            // Optionally update the message in the list, though it's already a reference
        });
        try {
            objectMapper.writeValue(messagesFile, messages);
        } catch (IOException e) {
            System.err.println("Error marking messages as read: " + e.getMessage());
        }
    }

    // For simplicity, we just save/load all messages. In a real app, you'd manage them.
    public List<Message> findAll() {
        return new ArrayList<>(messages);
    }
}