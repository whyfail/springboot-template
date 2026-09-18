package {{ package }}.shared.error;

/** A single request-field validation failure rendered in the Problem Details {@code errors} array. */
public record FieldErrorDto(String field, String message) {}
