package com.example.simplechat.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Message {
    private String id;
    private String sender;
    private String receiver;
    private String content; // Text message
    private String fileLink; // File message
    private LocalDateTime timestamp;
    private boolean read;
}