package com.example.simplechat.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class User {
    private String username;
    private String passwordHash;
    private List<String> friends;
    private String accessToken;
    private LocalDateTime accessTokenExpiry;
    private transient boolean online; // transient để không lưu vào JSON
}