package com.loomai.desk.ticket;

public final class TicketException extends RuntimeException {
  private final int status;
  private final String code;
  private final Object current;

  public TicketException(int status, String code, String message) {
    this(status, code, message, null);
  }

  public TicketException(int status, String code, String message, Object current) {
    super(message);
    this.status = status;
    this.code = code;
    this.current = current;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public Object current() {
    return current;
  }

  public static TicketException missing() {
    return new TicketException(404, "TICKET_NOT_FOUND", "Request is not available.");
  }

  public static TicketException invalid(String field) {
    return new TicketException(400, "TICKET_INPUT_INVALID", "Invalid " + field + ".");
  }

  public static TicketException unsupported() {
    return new TicketException(
        422, "TICKET_OPERATION_UNSUPPORTED", "The provider does not support this operation.");
  }
}
