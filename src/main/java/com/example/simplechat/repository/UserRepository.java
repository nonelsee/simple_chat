package com.example.simplechat.repository;

import com.example.simplechat.model.User;
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
import java.util.Optional;

@Repository
public class UserRepository {

    private static final String USERS_DB_PATH = "users.json";
    private List<User> users;
    private final ObjectMapper objectMapper;
    private File usersFile;

    public UserRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    private void init() {
        try {
            // Get the actual file path from classpath resource
            usersFile = new ClassPathResource(USERS_DB_PATH).getFile();
            if (usersFile.exists() && usersFile.length() > 0) {
                User[] userArray = objectMapper.readValue(usersFile, User[].class);
                users = new ArrayList<>(Arrays.asList(userArray));
            } else {
                users = new ArrayList<>();
                // Create an empty JSON array if file is new
                objectMapper.writeValue(usersFile, users);
            }
        } catch (IOException e) {
            System.err.println("Error initializing users database: " + e.getMessage());
            users = new ArrayList<>(); // Initialize as empty list on error
        }
    }

    public Optional<User> findByUsername(String username) {
        return users.stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
    }

    public Optional<User> findByAccessToken(String accessToken) {
        return users.stream()
                .filter(u -> accessToken.equals(u.getAccessToken()))
                .findFirst();
    }

    public void save(User user) {
        // Remove existing user if present and add the updated one
        users.removeIf(u -> u.getUsername().equals(user.getUsername()));
        users.add(user);
        try {
            objectMapper.writeValue(usersFile, users);
        } catch (IOException e) {
            System.err.println("Error saving user to database: " + e.getMessage());
        }
    }

    public List<User> findAll() {
        return new ArrayList<>(users);
    }
}