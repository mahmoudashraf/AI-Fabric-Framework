package com.ai.infrastructure.relationship.it.config;

import com.ai.infrastructure.relationship.it.entity.AccountEntity;
import com.ai.infrastructure.relationship.it.entity.BrandEntity;
import com.ai.infrastructure.relationship.it.entity.DocumentEntity;
import com.ai.infrastructure.relationship.it.entity.ProductEntity;
import com.ai.infrastructure.relationship.it.entity.TransactionEntity;
import com.ai.infrastructure.relationship.it.entity.UserEntity;
import com.ai.infrastructure.relationship.service.EntityRelationshipMapper;
import com.ai.infrastructure.relationship.dto.RelationshipDirection;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the test entities and relationships with the mapper so that the schema
 * exposed to the planner is deterministic.
 */
@Configuration
public class RelationshipEntityMappingConfiguration {

    @Bean
    public CommandLineRunner relationshipEntityInitializer(EntityRelationshipMapper mapper) {
        return args -> {
            mapper.registerEntityType(DocumentEntity.class);
            mapper.registerEntityType(UserEntity.class);
            mapper.registerRelationship("document", "user", "author", RelationshipDirection.FORWARD, false);

            mapper.registerEntityType(ProductEntity.class);
            mapper.registerEntityType(BrandEntity.class);
            mapper.registerRelationship("product", "brand", "brand", RelationshipDirection.FORWARD, false);

            mapper.registerEntityType(TransactionEntity.class);
            mapper.registerEntityType(AccountEntity.class);
            mapper.registerEntityType("destination-account", AccountEntity.class);
            mapper.registerEntityType("origin-account", AccountEntity.class);
            mapper.registerRelationship("transaction", "destination-account", "destinationAccount", RelationshipDirection.FORWARD, false);
            mapper.registerRelationship("transaction", "origin-account", "sourceAccount", RelationshipDirection.FORWARD, false);
        };
    }

}
