package com.example.simplechat.controller;

import com.example.simplechat.model.Message;
import com.example.simplechat.model.User;
import com.example.simplechat.service.MessageService;
import com.example.simplechat.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.async.DeferredResult;

import java.io.IOException;
import java.nio.file.Files; // Thêm import này
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private ChatController chatController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private User testUser;
    private String validAccessToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(chatController).build();
        objectMapper = new ObjectMapper();

        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setPasswordHash("5d41402abc4b2a76b9719d911017c592"); // MD5 of "hello"
        testUser.setFriends(Arrays.asList("friend1", "friend2"));
        testUser.setAccessToken("valid-token-123");
        testUser.setAccessTokenExpiry(LocalDateTime.now().plusHours(1));

        validAccessToken = "valid-token-123";
    }

    @Test
    void login_ValidCredentials_ReturnsAccessToken() throws Exception {
        // Arrange
        Map<String, String> loginRequest = Map.of("username", "testuser", "password", "hello");
        when(userService.login("testuser", "hello")).thenReturn("access-token-123");

        // Act
        String jsonRequest = objectMapper.writeValueAsString(loginRequest);

        // Assert
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-123"));

        verify(userService).login("testuser", "hello");
    }

    @Test
    void login_InvalidCredentials_ReturnsUnauthorized() throws Exception {
        // Arrange
        Map<String, String> loginRequest = Map.of("username", "testuser", "password", "wrongpassword");
        when(userService.login("testuser", "wrongpassword")).thenReturn(null);

        // Act & Assert
        String jsonRequest = objectMapper.writeValueAsString(loginRequest);

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isUnauthorized());

        verify(userService).login("testuser", "wrongpassword");
    }

    @Test
    void login_MissingCredentials_ReturnsBadRequest() throws Exception {
        // Arrange
        Map<String, String> loginRequest = Map.of("username", "testuser");

        // Act & Assert
        String jsonRequest = objectMapper.writeValueAsString(loginRequest);

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFriends_ValidToken_ReturnsFriendsList() {
        // Arrange
        when(userService.validateAccessToken(validAccessToken)).thenReturn(Optional.of(testUser));
        when(userService.getFriends("testuser")).thenReturn(Arrays.asList("friend1", "friend2"));

        // Act
        ResponseEntity<?> response = chatController.getFriends(validAccessToken);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        List<String> friends = (List<String>) responseBody.get("friends");
        assertEquals(2, friends.size());
        assertTrue(friends.contains("friend1"));
        assertTrue(friends.contains("friend2"));

        verify(userService).validateAccessToken(validAccessToken);
        verify(userService).getFriends("testuser");
    }

    @Test
    void getFriends_InvalidToken_ReturnsUnauthorized() {
        // Arrange
        when(userService.validateAccessToken("invalid-token")).thenReturn(Optional.empty());

        // Act
        ResponseEntity<?> response = chatController.getFriends("invalid-token");

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(userService).validateAccessToken("invalid-token");
        verify(userService, never()).getFriends(anyString());
    }

    @Test
    void sendMessage_ValidTextMessage_ReturnsSuccess() {
        // Arrange
        when(userService.validateAccessToken(validAccessToken)).thenReturn(Optional.of(testUser));
        when(messageService.sendMessage("testuser", "friend1", "Hello!", null)).thenReturn(1);

        // Act
        ResponseEntity<?> response = chatController.sendMessage(validAccessToken, "friend1", "Hello!", null);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertEquals(1, responseBody.get("status"));
        assertEquals("Message sent, receiver online.", responseBody.get("message"));

        verify(userService).validateAccessToken(validAccessToken);
        verify(messageService).sendMessage("testuser", "friend1", "Hello!", null);
    }

    @Test
    void sendMessage_ValidFileMessage_ReturnsSuccess() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test content".getBytes());
        when(userService.validateAccessToken(validAccessToken)).thenReturn(Optional.of(testUser));
        when(messageService.sendMessage("testuser", "friend1", null, file)).thenReturn(1);

        // Act
        ResponseEntity<?> response = chatController.sendMessage(validAccessToken, "friend1", null, file);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(messageService).sendMessage("testuser", "friend1", null, file);
    }

    @Test
    void sendMessage_ReceiverOffline_ReturnsQueued() {
        // Arrange
        when(userService.validateAccessToken(validAccessToken)).thenReturn(Optional.of(testUser));
        when(messageService.sendMessage("testuser", "friend1", "Hello!", null)).thenReturn(2);

        // Act
        ResponseEntity<?> response = chatController.sendMessage(validAccessToken, "friend1", "Hello!", null);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertEquals(2, responseBody.get("status"));
        assertEquals("Message queued, receiver offline.", responseBody.get("message"));
    }

    @Test
    void sendMessage_NotFriends_ReturnsForbidden() {
        // Arrange
        when(userService.validateAccessToken(validAccessToken)).thenReturn(Optional.of(testUser));
        when(messageService.sendMessage("testuser", "stranger", "Hello!", null)).thenReturn(3);

        // Act
        ResponseEntity<?> response = chatController.sendMessage(validAccessToken, "stranger", "Hello!", null);

        // Assert
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertEquals(3, responseBody.get("status"));
        assertEquals("Sender not in receiver's friend list.", responseBody.get("message"));
    }

    @Test
    void sendMessage_NoContentOrFile_ReturnsBadRequest() {
        // Arrange
        when(userService.validateAccessToken(validAccessToken)).thenReturn(Optional.of(testUser));

        // Act
        ResponseEntity<?> response = chatController.sendMessage(validAccessToken, "friend1", null, null);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(messageService, never()).sendMessage(anyString(), anyString(), anyString(), any());
    }

    @Test
    void getNewMessages_ValidToken_WithMessages_ReturnsMessages() {
        // Arrange
        Message message1 = new Message();
        message1.setId("msg1");
        message1.setSender("friend1");
        message1.setReceiver("testuser");
        message1.setContent("Hello!");
        message1.setTimestamp(LocalDateTime.now());

        List<Message> messages = Arrays.asList(message1);

        when(userService.validateAccessToken(validAccessToken)).thenReturn(Optional.of(testUser));
        when(messageService.getNewMessages("testuser")).thenReturn(messages);

        // Act
        DeferredResult<List<Message>> result = chatController.getNewMessages(validAccessToken);

        // Assert
        assertNotNull(result);
        assertEquals(messages, result.getResult());
        verify(userService).validateAccessToken(validAccessToken);
        verify(userService).setOnlineStatus("testuser", true);
        verify(messageService).getNewMessages("testuser");
    }

    @Test
    void getNewMessages_InvalidToken_ReturnsEmptyList() {
        // Arrange
        when(userService.validateAccessToken("invalid-token")).thenReturn(Optional.empty());

        // Act
        DeferredResult<List<Message>> result = chatController.getNewMessages("invalid-token");

        // Assert
        assertNotNull(result);
        assertEquals(Collections.emptyList(), result.getResult());
        verify(userService).validateAccessToken("invalid-token");
        verify(userService, never()).setOnlineStatus(anyString(), anyBoolean());
    }

    @Test
    void downloadFile_ValidRequest_ReturnsFile() throws Exception {
        // Arrange
        String filename = "test-file.txt";
        Path testFilePath = Files.createTempFile("test-file", ".txt"); // Tạo tệp tạm thời
        Files.write(testFilePath, "This is test content.".getBytes()); // Ghi nội dung vào tệp

        // Mock messageService để trả về đường dẫn tệp tạm thời
        when(userService.validateAccessToken(validAccessToken)).thenReturn(Optional.of(testUser));
        when(messageService.getFilePath(filename, "testuser")).thenReturn(testFilePath);

        // Act
        ResponseEntity<Resource> response = chatController.downloadFile(validAccessToken, filename);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof UrlResource);
        assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());

        // Sửa: Kiểm tra filename thực tế từ resource thay vì hardcode
        Resource returnedResource = response.getBody();
        String actualFilename = returnedResource.getFilename();
        assertEquals("attachment; filename=\"" + actualFilename + "\"",
                response.getHeaders().getFirst("Content-Disposition"));

        // Verify content of the returned resource
        try (var is = returnedResource.getInputStream()) {
            String content = new String(is.readAllBytes());
            assertEquals("This is test content.", content);
        }

        verify(userService).validateAccessToken(validAccessToken);
        verify(messageService).getFilePath(filename, "testuser");

        Files.deleteIfExists(testFilePath); // Xóa tệp tạm thời sau kiểm thử
    }


    @Test
    void downloadFile_InvalidToken_ReturnsUnauthorized() throws IOException {
        // Arrange
        when(userService.validateAccessToken("invalid-token")).thenReturn(Optional.empty());

        // Act
        ResponseEntity<Resource> response = chatController.downloadFile("invalid-token", "test.txt");

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(messageService, never()).getFilePath(anyString(), anyString());
    }

    @Test
    void downloadFile_FileNotFound_ReturnsNotFound() throws IOException {
        // Arrange
        when(userService.validateAccessToken(validAccessToken)).thenReturn(Optional.of(testUser));
        when(messageService.getFilePath("nonexistent.txt", "testuser")).thenReturn(null);

        // Act
        ResponseEntity<Resource> response = chatController.downloadFile(validAccessToken, "nonexistent.txt");

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}