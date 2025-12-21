#!/bin/bash

# Fix remaining integration tests properly

echo "🔧 Fixing remaining integration tests..."

# Fix CreativeAIScenariosTest.java
echo "Fixing CreativeAIScenariosTest.java..."
file="src/test/java/com/ai/infrastructure/it/CreativeAIScenariosTest.java"

# Add import
sed -i '/import com.ai.infrastructure.service.AICapabilityService;/a import com.ai.infrastructure.service.VectorManagementService;' "$file"

# Add injection after last @Autowired
sed -i '/@Autowired/a\    @Autowired\n    private VectorManagementService vectorManagementService;' "$file"

# Fix getEmbeddings calls
sed -i 's/assertNotNull(entity\.getEmbeddings(), "Should have embeddings");/assertNotNull(entity.getVectorId(), "Should have vector ID");/g' "$file"
sed -i 's/assertFalse(entity\.getEmbeddings()\.isEmpty(), "Embeddings should not be empty");/assertFalse(entity.getVectorId().isEmpty(), "Vector ID should not be empty");/g' "$file"

# Add vector existence check
sed -i '/assertFalse(entity\.getVectorId()\.isEmpty(), "Vector ID should not be empty");/a\        \n        // Verify vector exists in vector database\n        assertTrue(vectorManagementService.vectorExists(entity.getEntityType(), entity.getEntityId()), \n                  "Vector should exist in vector database");' "$file"

# Fix logging
sed -i 's/Embeddings generated:/Vector ID:/g' "$file"
sed -i 's/entity\.getEmbeddings()\.size()/entity.getVectorId().length()/g' "$file"

echo "✅ Fixed CreativeAIScenariosTest.java"

# Fix RealAPIIntegrationTest.java
echo "Fixing RealAPIIntegrationTest.java..."
file="src/test/java/com/ai/infrastructure/it/RealAPIIntegrationTest.java"

# Add import
sed -i '/import com.ai.infrastructure.service.AICapabilityService;/a import com.ai.infrastructure.service.VectorManagementService;' "$file"

# Add injection after last @Autowired
sed -i '/@Autowired/a\    @Autowired\n    private VectorManagementService vectorManagementService;' "$file"

# Fix getEmbeddings calls
sed -i 's/assertNotNull(entity\.getEmbeddings(), "Should have embeddings");/assertNotNull(entity.getVectorId(), "Should have vector ID");/g' "$file"
sed -i 's/assertFalse(entity\.getEmbeddings()\.isEmpty(), "Embeddings should not be empty");/assertFalse(entity.getVectorId().isEmpty(), "Vector ID should not be empty");/g' "$file"

# Add vector existence check
sed -i '/assertFalse(entity\.getVectorId()\.isEmpty(), "Vector ID should not be empty");/a\        \n        // Verify vector exists in vector database\n        assertTrue(vectorManagementService.vectorExists(entity.getEntityType(), entity.getEntityId()), \n                  "Vector should exist in vector database");' "$file"

# Fix logging and other calls
sed -i 's/Embeddings generated:/Vector ID:/g' "$file"
sed -i 's/entity\.getEmbeddings()\.size()/entity.getVectorId().length()/g' "$file"
sed -i 's/entity\.getEmbeddings()\.subList/entity.getVectorId().substring/g' "$file"

echo "✅ Fixed RealAPIIntegrationTest.java"

echo "🎉 All remaining tests fixed!"
