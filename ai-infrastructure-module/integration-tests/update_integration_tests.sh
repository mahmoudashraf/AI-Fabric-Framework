#!/bin/bash

# Update integration tests to work with new vector storage architecture

# Add VectorManagementService import to all test files
for file in src/test/java/com/ai/infrastructure/it/*.java; do
    if grep -q "getEmbeddings()" "$file" && ! grep -q "VectorManagementService" "$file"; then
        echo "Updating $file"
        
        # Add import
        sed -i '/import com.ai.infrastructure.service.AICapabilityService;/a import com.ai.infrastructure.service.VectorManagementService;' "$file"
        
        # Add injection
        sed -i '/@Autowired/a\    @Autowired\n    private VectorManagementService vectorManagementService;' "$file"
        
        # Replace getEmbeddings() calls with vectorId checks
        sed -i 's/assertNotNull(entity\.getEmbeddings(), "Should have embeddings");/assertNotNull(entity.getVectorId(), "Should have vector ID");/g' "$file"
        sed -i 's/assertFalse(entity\.getEmbeddings()\.isEmpty(), "Embeddings should not be empty");/assertFalse(entity.getVectorId().isEmpty(), "Vector ID should not be empty");/g' "$file"
        
        # Add vector existence check after vectorId checks
        sed -i '/assertFalse(entity\.getVectorId()\.isEmpty(), "Vector ID should not be empty");/a\        \n        // Verify vector exists in vector database\n        assertTrue(vectorManagementService.vectorExists(entity.getEntityType(), entity.getEntityId()), \n                  "Vector should exist in vector database");' "$file"
        
        # Handle other getEmbeddings() patterns
        sed -i 's/entity\.getEmbeddings()/entity.getVectorId()/g' "$file"
        sed -i 's/\.getEmbeddings()/\.getVectorId()/g' "$file"
    fi
done

echo "Integration tests updated successfully!"
