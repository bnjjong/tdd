package io.df.tdd.service

import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.test.assertTrue

class NotificationServiceTest {

    @Test
    fun `should send notification successfully`() {
        // Given
        val mockEmailService = mock(EmailService::class.java)
        val notificationService = NotificationService(mockEmailService)
        
        `when`(mockEmailService.sendEmail(anyString(), anyString())).thenReturn(true)

        // When
        val result = notificationService.notifyUser("user123", "Test message")

        // Then
        assertTrue(result, "Notification should be sent successfully")
        verify(mockEmailService).sendEmail("user123@example.com", "Test message")
    }

    @Test
    fun `should handle email service failure`() {
        // Given
        val mockEmailService = mock(EmailService::class.java)
        val notificationService = NotificationService(mockEmailService)
        
        `when`(mockEmailService.sendEmail(anyString(), anyString())).thenReturn(false)

        // When
        val result = notificationService.notifyUser("user123", "Test message")

        // Then
        assertTrue(!result, "Notification should fail when email service fails")
        verify(mockEmailService).sendEmail("user123@example.com", "Test message")
    }
}
