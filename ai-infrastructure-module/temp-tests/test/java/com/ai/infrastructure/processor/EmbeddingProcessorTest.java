package com.ai.infrastructure.processor;

import com.ai.infrastructure.config.AIProviderConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EmbeddingProcessorTest {
    
    @Mock
    private AIProviderConfig config;
    
    private EmbeddingProcessor embeddingProcessor;
    
    @BeforeEach
    void setUp() {
        embeddingProcessor = new EmbeddingProcessor(config);
    }
    
    @Test
    void testProcessText() {
        // Given
        String text = "This is a test text with   multiple   spaces and special characters!@#$%";
        
        // When
        String result = embeddingProcessor.processText(text);
        
        // Then
        assertNotNull(result);
        assertTrue(result.length() <= text.length());
        assertFalse(result.contains("   ")); // Multiple spaces should be normalized
    }
    
    @Test
    void testProcessTextWithNull() {
        // When
        String result = embeddingProcessor.processText(null);
        
        // Then
        assertEquals("", result);
    }
    
    @Test
    void testChunkText() {
        // Given
        String text = "This is a long text that should be chunked into smaller pieces. " +
                     "Each chunk should be manageable for embedding generation. " +
                     "The chunking should preserve sentence boundaries when possible.";
        
        // When
        List<String> chunks = embeddingProcessor.chunkText(text, 50, 10);
        
        // Then
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());
        // Allow some flexibility in chunk size due to overlap
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 60));
    }
    
    @Test
    void testExtractKeyPhrases() {
        // Given
        String text = "This is a test document about artificial intelligence and machine learning. " +
                     "It discusses various AI techniques and their applications in real-world scenarios.";
        
        // When
        List<String> phrases = embeddingProcessor.extractKeyPhrases(text, 5);
        
        // Then
        assertNotNull(phrases);
        assertFalse(phrases.isEmpty());
        assertTrue(phrases.size() <= 5);
    }
    
    @Test
    void testCalculateTextSimilarity() {
        // Given
        String text1 = "This is a test document about artificial intelligence";
        String text2 = "This is a test document about machine learning";
        String text3 = "Completely different content about cooking recipes";
        
        // When
        double similarity1 = embeddingProcessor.calculateTextSimilarity(text1, text2);
        double similarity2 = embeddingProcessor.calculateTextSimilarity(text1, text3);
        double similarity3 = embeddingProcessor.calculateTextSimilarity(text1, text1);
        
        // Then
        assertTrue(similarity1 > similarity2); // Similar texts should have higher similarity
        assertEquals(1.0, similarity3); // Identical texts should have similarity of 1.0
        assertTrue(similarity1 >= 0.0 && similarity1 <= 1.0);
        assertTrue(similarity2 >= 0.0 && similarity2 <= 1.0);
    }
}
