package com.example.simplechat.service;

import com.example.simplechat.model.User;
import com.example.simplechat.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private ConcurrentHashMap<String, Object> onlineUsers;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setPasswordHash("5d41402abc4b2a76b9719d911017c592"); // MD5 of "hello"
        testUser.setFriends(Arrays.asList("friend1", "friend2"));
        testUser.setAccessToken("valid-token-123");
        testUser.setAccessTokenExpiry(LocalDateTime.now().plusHours(1));

        onlineUsers = new ConcurrentHashMap<>();
        ReflectionTestUtils.setField(userService, "onlineUsers", onlineUsers);
    }

    @Test
    void login_ValidCredentials_ReturnsAccessToken() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act
        String result = userService.login("testuser", "hello");

        // Assert
        assertNotNull(result);
        verify(userRepository).findByUsername("testuser");
        verify(userRepository).save(argThat(user ->
                user.getAccessToken() != null &&
                        user.getAccessTokenExpiry() != null &&
                        user.getAccessTokenExpiry().isAfter(LocalDateTime.now())
        ));
        assertTrue(onlineUsers.containsKey("testuser"));
    }

    @Test
    void login_InvalidPassword_ReturnsNull() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act
        String result = userService.login("testuser", "wrongpassword");

        // Assert
        assertNull(result);
        verify(userRepository).findByUsername("testuser");
        verify(userRepository, never()).save(any(User.class));
        assertFalse(onlineUsers.containsKey("testuser"));
    }

    @Test
    void login_UserNotFound_ReturnsNull() {
        // Arrange
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act
        String result = userService.login("nonexistent", "password");

        // Assert
        assertNull(result);
        verify(userRepository).findByUsername("nonexistent");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void validateAccessToken_ValidToken_ReturnsUser() {
        // Arrange
        when(userRepository.findByAccessToken("valid-token-123")).thenReturn(Optional.of(testUser));

        // Act
        Optional<User> result = userService.validateAccessToken("valid-token-123");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testUser, result.get());
        verify(userRepository).findByAccessToken("valid-token-123");
    }

    @Test
    void validateAccessToken_ExpiredToken_ReturnsEmpty() {
        // Arrange
        testUser.setAccessTokenExpiry(LocalDateTime.now().minusHours(1)); // Expired token
        when(userRepository.findByAccessToken("expired-token")).thenReturn(Optional.of(testUser));

        // Act
        Optional<User> result = userService.validateAccessToken("expired-token");

        // Assert
        assertTrue(result.isEmpty());
        verify(userRepository).findByAccessToken("expired-token");
    }

    @Test
    void validateAccessToken_InvalidToken_ReturnsEmpty() {
        // Arrange
        when(userRepository.findByAccessToken("invalid-token")).thenReturn(Optional.empty());

        // Act
        Optional<User> result = userService.validateAccessToken("invalid-token");

        // Assert
        assertTrue(result.isEmpty());
        verify(userRepository).findByAccessToken("invalid-token");
    }

    @Test
    void getFriends_UserExists_ReturnsFriendsList() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act
        List<String> result = userService.getFriends("testuser");

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.contains("friend1"));
        assertTrue(result.contains("friend2"));
        verify(userRepository).findByUsername("testuser");
    }

    @Test
    void getFriends_UserNotFound_ReturnsEmptyList() {
        // Arrange
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act
        List<String> result = userService.getFriends("nonexistent");

        // Assert
        assertEquals(0, result.size());
        verify(userRepository).findByUsername("nonexistent");
    }

    @Test
    void isFriend_UserIsFriend_ReturnsTrue() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act
        boolean result = userService.isFriend("friend1", "testuser");

        // Assert
        assertTrue(result);
        verify(userRepository).findByUsername("testuser");
    }

    @Test
    void isFriend_UserIsNotFriend_ReturnsFalse() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act
        boolean result = userService.isFriend("stranger", "testuser");

        // Assert
        assertFalse(result);
        verify(userRepository).findByUsername("testuser");
    }

    @Test
    void isFriend_ReceiverNotFound_ReturnsFalse() {
        // Arrange
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act
        boolean result = userService.isFriend("friend1", "nonexistent");

        // Assert
        assertFalse(result);
        verify(userRepository).findByUsername("nonexistent");
    }

    @Test
    void hashPasswordMD5_ValidInput_ReturnsCorrectHash() {
        // Act
        String result = userService.hashPasswordMD5("hello");

        // Assert
        assertEquals("5d41402abc4b2a76b9719d911017c592", result);
    }

    @Test
    void setOnlineStatus_SetOnline_AddsToOnlineUsers() {
        // Act
        userService.setOnlineStatus("testuser", true);

        // Assert
        assertTrue(onlineUsers.containsKey("testuser"));
        assertTrue(userService.isUserOnline("testuser"));
    }

    @Test
    void setOnlineStatus_SetOffline_RemovesFromOnlineUsers() {
        // Arrange
        onlineUsers.put("testuser", new Object());

        // Act
        userService.setOnlineStatus("testuser", false);

        // Assert
        assertFalse(onlineUsers.containsKey("testuser"));
        assertFalse(userService.isUserOnline("testuser"));
    }

    @Test
    void isUserOnline_UserOnline_ReturnsTrue() {
        // Arrange
        onlineUsers.put("testuser", new Object());

        // Act
        boolean result = userService.isUserOnline("testuser");

        // Assert
        assertTrue(result);
    }

    @Test
    void isUserOnline_UserOffline_ReturnsFalse() {
        // Act
        boolean result = userService.isUserOnline("testuser");

        // Assert
        assertFalse(result);
    }

    @Test
    void getOnlineUsers_ReturnsOnlineUsersMap() {
        // Arrange
        onlineUsers.put("user1", new Object());
        onlineUsers.put("user2", new Object());

        // Act
        ConcurrentHashMap<String, Object> result = userService.getOnlineUsers();

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.containsKey("user1"));
        assertTrue(result.containsKey("user2"));
    }
}