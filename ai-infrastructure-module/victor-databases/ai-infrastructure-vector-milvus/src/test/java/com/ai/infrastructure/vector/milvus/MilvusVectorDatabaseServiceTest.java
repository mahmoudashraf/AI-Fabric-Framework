package com.ai.infrastructure.vector.milvus;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MilvusVectorDatabaseServiceTest {

    @Test
    void normalizeEntityTypeTokenLowercasesAndTrims() {
        assertThat(MilvusVectorDatabaseService.normalizeEntityTypeToken(" Test-Product "))
            .isEqualTo("test-product");
    }

    @Test
    void normalizeEntityTypeTokenRejectsBlank() {
        assertThatThrownBy(() -> MilvusVectorDatabaseService.normalizeEntityTypeToken("   "))
            .isInstanceOf(com.ai.infrastructure.exception.AIServiceException.class);
    }

    @Test
    void toCollectionNameRemovesInvalidCharactersAndIsStable() {
        String collection = MilvusVectorDatabaseService.toCollectionName("test-product");
        assertThat(collection)
            .doesNotContain("-")
            .matches("^[a-z_][a-z0-9_]*$");
    }

    @Test
    void toCollectionNameAvoidsCollisionsBetweenHyphenAndUnderscore() {
        String fromHyphen = MilvusVectorDatabaseService.toCollectionName("test-product");
        String fromUnderscore = MilvusVectorDatabaseService.toCollectionName("test_product");
        assertThat(fromHyphen).isNotEqualTo(fromUnderscore);
    }

    @Test
    void toCollectionNamePrefixesDigitStart() {
        String collection = MilvusVectorDatabaseService.toCollectionName("123abc");
        assertThat(collection).startsWith("c_");
    }
}
