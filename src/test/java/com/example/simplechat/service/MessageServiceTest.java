package com.example.simplechat.service;

import com.example.simplechat.model.Message;
import com.example.simplechat.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor; // Thêm import này

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private MessageService messageService;

    @TempDir
    Path tempDir;

    private ConcurrentHashMap<String, LinkedBlockingQueue<Message>> messageQueues;
    private String tempStoragePath;

    @BeforeEach
    void setUp() throws IOException {
        tempStoragePath = tempDir.toAbsolutePath().toString() + "/";
        ReflectionTestUtils.setField(messageService, "STORAGE_ROOT", tempStoragePath);

        messageQueues = new ConcurrentHashMap<>();
        ReflectionTestUtils.setField(messageService, "messageQueues", messageQueues);

        Files.createDirectories(Paths.get(tempStoragePath));
    }

    @Test
    void sendMessage_TextMessage_ReceiverOnline_ReturnsOne() {
        when(userService.isFriend("sender", "receiver")).thenReturn(true);
        when(userService.isUserOnline("receiver")).thenReturn(true);

        int result = messageService.sendMessage("sender", "receiver", "Hello!", null);

        assertEquals(1, result);
        verify(userService).isFriend("sender", "receiver");
        verify(userService).isUserOnline("receiver");
        verify(messageRepository).save(any(Message.class));
        assertTrue(messageQueues.containsKey("receiver"));
        assertEquals(1, messageQueues.get("receiver").size());
    }

    @Test
    void sendMessage_TextMessage_ReceiverOffline_ReturnsTwo() {
        when(userService.isFriend("sender", "receiver")).thenReturn(true);
        when(userService.isUserOnline("receiver")).thenReturn(false);

        int result = messageService.sendMessage("sender", "receiver", "Hello!", null);

        assertEquals(2, result);
        verify(userService).isFriend("sender", "receiver");
        verify(userService).isUserOnline("receiver");
        verify(messageRepository).save(any(Message.class));
        assertTrue(messageQueues.containsKey("receiver"));
        assertEquals(1, messageQueues.get("receiver").size());
    }

    @Test
    void sendMessage_NotFriends_ReturnsThree() {
        when(userService.isFriend("sender", "stranger")).thenReturn(false);

        int result = messageService.sendMessage("sender", "stranger", "Hello!", null);

        assertEquals(3, result);
        verify(userService).isFriend("sender", "stranger");
        verify(userService, never()).isUserOnline(anyString());
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void sendMessage_FileMessage_Success() throws IOException {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test content".getBytes());
        when(userService.isFriend("sender", "receiver")).thenReturn(true);
        when(userService.isUserOnline("receiver")).thenReturn(true);

        // Act
        int result = messageService.sendMessage("sender", "receiver", null, file);

        // Assert
        assertEquals(1, result);

        // Sử dụng ArgumentCaptor để bắt đối tượng Message được truyền vào messageRepository.save()
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture()); // Bắt đối tượng Message

        Message savedMessage = messageCaptor.getValue(); // Lấy đối tượng Message đã bắt được
        assertEquals("[FILE]", savedMessage.getContent()); // Bây giờ bạn có thể truy cập getContent()
        assertTrue(savedMessage.getFileLink().startsWith("/files/")); // Và getFileLink()

        // Verify the file was physically saved to the temp directory
        String savedFileLink = savedMessage.getFileLink(); // Sử dụng savedMessage đã được ép kiểu
        String savedFileName = savedFileLink.substring("/files/".length());
        Path storedFilePath = Paths.get(tempStoragePath, savedFileName);
        assertTrue(Files.exists(storedFilePath), "File should exist at: " + storedFilePath.toAbsolutePath());
        assertEquals("test content", Files.readString(storedFilePath));
    }

    @Test
    void getNewMessages_WithUnreadFromDB_ReturnsAllMessages() {
        String username = "testuser";

        Message dbMessage = new Message();
        dbMessage.setId("db-msg-1");
        dbMessage.setSender("friend1");
        dbMessage.setReceiver(username);
        dbMessage.setContent("From DB");
        dbMessage.setTimestamp(LocalDateTime.now());

        Message queueMessage = new Message();
        queueMessage.setId("queue-msg-1");
        queueMessage.setSender("friend2");
        queueMessage.setReceiver(username);
        queueMessage.setContent("From Queue");
        queueMessage.setTimestamp(LocalDateTime.now());

        when(messageRepository.findUnreadMessagesForUser(username))
                .thenReturn(Arrays.asList(dbMessage));

        LinkedBlockingQueue<Message> userQueue = new LinkedBlockingQueue<>();
        userQueue.offer(queueMessage);
        messageQueues.put(username, userQueue);

        List<Message> result = messageService.getNewMessages(username);

        assertEquals(2, result.size());
        assertTrue(result.contains(queueMessage));
        assertTrue(result.contains(dbMessage));
        verify(messageRepository).findUnreadMessagesForUser(username);
        verify(messageRepository).markMessagesAsRead(argThat(list ->
                list.size() == 2 && list.contains(dbMessage) && list.contains(queueMessage)
        ));

        assertTrue(messageQueues.get(username).isEmpty());
    }

    @Test
    void getNewMessages_NoMessages_ReturnsEmptyList() {
        String username = "testuser";
        when(messageRepository.findUnreadMessagesForUser(username))
                .thenReturn(new ArrayList<>());

        List<Message> result = messageService.getNewMessages(username);

        assertEquals(0, result.size());
        verify(messageRepository).findUnreadMessagesForUser(username);
        verify(messageRepository, never()).markMessagesAsRead(anyList());
    }

    @Test
    void getFilePath_ValidFile_ReturnsPath() throws IOException {
        String filename = "test-file.txt";
        Path testFile = Paths.get(tempStoragePath, filename);
        Files.createFile(testFile);

        Path result = messageService.getFilePath(filename, "testuser");

        assertNotNull(result);
        assertEquals(testFile.normalize(), result.normalize());
        assertTrue(Files.exists(result));
    }

    @Test
    void getFilePath_NonexistentFile_ReturnsNull() throws IOException {
        String filename = "nonexistent.txt";

        Path result = messageService.getFilePath(filename, "testuser");

        assertNull(result);
    }

    @Test
    void getFilePath_DirectoryTraversal_ThrowsIOException() {
        String maliciousFilename = "../../../etc/passwd";

        assertThrows(IOException.class, () -> {
            messageService.getFilePath(maliciousFilename, "testuser");
        });
    }
}