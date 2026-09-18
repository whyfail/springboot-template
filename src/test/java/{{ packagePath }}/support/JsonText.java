package {{ package }}.support;

import com.jayway.jsonpath.JsonPath;

/** JSON helpers for HTTP-contract assertions in integration tests. */
public final class JsonText {

    private JsonText() {}

    public static String extractString(String json, String field) {
        Object value = JsonPath.read(json, "$." + field);
        return String.valueOf(value);
    }

    public static int extractInt(String json, String field) {
        Object value = JsonPath.read(json, "$." + field);
        return ((Number) value).intValue();
    }
}
