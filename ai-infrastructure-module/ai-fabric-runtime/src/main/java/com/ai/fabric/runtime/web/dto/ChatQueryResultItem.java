package com.ai.fabric.runtime.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatQueryResultItem(String type, boolean success, String answer, String fallbackReason) {
}
