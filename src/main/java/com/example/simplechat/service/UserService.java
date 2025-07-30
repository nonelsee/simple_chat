package com.example.simplechat.service;

import com.example.simplechat.model.User;
import com.example.simplechat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    // Simulate online status for long polling
    // Key: username, Value: a dummy object for synchronization or a DeferredResult/CompletableFuture
    private final ConcurrentHashMap<String, Object> onlineUsers = new ConcurrentHashMap<>();

    public String login(String username, String password) {
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            String hashedPassword = hashPasswordMD5(password);
            if (user.getPasswordHash().equals(hashedPassword)) {
                String accessToken = UUID.randomUUID().toString();
                LocalDateTime expiry = LocalDateTime.now().plusHours(1); // Token expires in 1 hour
                user.setAccessToken(accessToken);
                user.setAccessTokenExpiry(expiry);
                userRepository.save(user); // Update user in DB
                onlineUsers.put(username, new Object()); // Mark as online
                return accessToken;
            }
        }
        return null; // Login failed
    }

    public Optional<User> validateAccessToken(String accessToken) {
        Optional<User> userOptional = userRepository.findByAccessToken(accessToken);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user.getAccessTokenExpiry() != null && user.getAccessTokenExpiry().isAfter(LocalDateTime.now())) {
                return Optional.of(user);
            }
        }
        return Optional.empty(); // Token invalid or expired
    }

    public List<String> getFriends(String username) {
        return userRepository.findByUsername(username)
                .map(User::getFriends)
                .orElse(List.of()); // Return empty list if user not found
    }

    public boolean isFriend(String senderUsername, String receiverUsername) {
        return userRepository.findByUsername(receiverUsername)
                .map(user -> user.getFriends().contains(senderUsername))
                .orElse(false);
    }

    public String hashPasswordMD5(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashInBytes = md.digest(password.getBytes());

            StringBuilder sb = new StringBuilder();
            for (byte b : hashInBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found.", e);
        }
    }

    public void setOnlineStatus(String username, boolean isOnline) {
        if (isOnline) {
            onlineUsers.put(username, new Object()); // Using dummy object for presence
        } else {
            onlineUsers.remove(username);
        }
    }

    public boolean isUserOnline(String username) {
        return onlineUsers.containsKey(username);
    }

    public ConcurrentHashMap<String, Object> getOnlineUsers() {
        return onlineUsers;
    }
}