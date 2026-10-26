package com.loomai.desk.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.loomai.desk.channel.persistence.BasicInboxRepository.InboxConflictException;
import com.loomai.desk.channel.persistence.BasicInboxRepository.InboxNotFoundException;
import com.loomai.desk.channel.service.InboxSecurityService.InboxSecurityException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class DeskErrorHandler {
  @ExceptionHandler(InboxConflictException.class)
  ResponseEntity<?> conflict(InboxConflictException e) {
    return error(409, e.code(), e.getMessage());
  }

  @ExceptionHandler(InboxNotFoundException.class)
  ResponseEntity<?> missing(InboxNotFoundException e) {
    return error(404, e.code(), e.getMessage());
  }

  @ExceptionHandler(InboxSecurityException.class)
  ResponseEntity<?> denied(InboxSecurityException e) {
    return error(403, e.code(), e.getMessage());
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<?> status(ResponseStatusException e) {
    return error(
        e.getStatusCode().value(),
        "DESK_REQUEST_REJECTED",
        e.getReason() == null ? "Request rejected." : e.getReason());
  }

  @ExceptionHandler({
    JsonProcessingException.class,
    HttpMessageNotReadableException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<?> bad(Exception e) {
    return error(400, "DESK_REQUEST_INVALID", "The request does not match the Desk contract.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> integrity(Exception e) {
    return error(
        409,
        "DESK_IDENTITY_CONFLICT",
        "The requested identity or binding conflicts with an existing resource.");
  }

  private ResponseEntity<?> error(int status, String code, String message) {
    return ResponseEntity.status(status)
        .body(Map.of("success", false, "code", code, "message", message));
  }
}
