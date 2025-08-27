package io.df.tdd.renderer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageRendererTest {

    @Test
    fun `should render message as valid HTML`() {
        // Given
        val renderer = MessageRenderer()
        val message = "Hello World"

        // When
        val result = renderer.render(message)

        // Then - 구현 세부사항이 아닌 결과에 집중
        assertTrue(result.contains(message), "Result should contain the message")
        assertTrue(result.matches(Regex("<\\w+>.*</\\w+>")), "Result should be valid HTML")
        assertFalse(result.contains("<script>"), "Result should not contain unescaped characters")
    }

    @Test
    fun `should handle empty message gracefully`() {
        // Given
        val renderer = MessageRenderer()
        val emptyMessage = ""

        // When
        val result = renderer.render(emptyMessage)

        // Then
        assertTrue(result.matches(Regex("<\\w+></\\w+>")), "Result should still be valid HTML structure")
    }

    @Test
    fun `should allow changing tag type`() {
        // Given
        val renderer = MessageRenderer()
        val message = "Hello World"
        
        // When
        renderer.setTagType("span")
        val result = renderer.render(message)

        // Then
        assertTrue(result.startsWith("<span>"), "Result should start with span tag")
        assertTrue(result.endsWith("</span>"), "Result should end with span tag")
    }
}
