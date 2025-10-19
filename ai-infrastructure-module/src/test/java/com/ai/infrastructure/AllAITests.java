package com.ai.infrastructure;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Test Suite for All AI Infrastructure Tests
 * 
 * This test suite runs all AI infrastructure tests including:
 * - Integration tests
 * - Monitoring tests
 * - Provider tests
 * - RAG tests
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Suite
@SuiteDisplayName("AI Infrastructure Test Suite")
@SelectPackages({
    "com.ai.infrastructure",
    "com.ai.infrastructure.monitoring",
    "com.ai.infrastructure.provider",
    "com.ai.infrastructure.rag"
})
public class AllAITests {
    // This class serves as a test suite container
    // All tests are discovered automatically by the @SelectPackages annotation
}
