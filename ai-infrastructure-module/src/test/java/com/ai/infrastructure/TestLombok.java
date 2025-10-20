package com.ai.infrastructure;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestLombok {
    private String name;
    private int value;
    
    public static void main(String[] args) {
        TestLombok test = TestLombok.builder()
            .name("test")
            .value(42)
            .build();
        
        System.out.println("Name: " + test.getName());
        System.out.println("Value: " + test.getValue());
    }
}