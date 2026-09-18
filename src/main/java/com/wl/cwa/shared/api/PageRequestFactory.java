package com.wl.cwa.shared.api;

import com.wl.cwa.shared.error.BusinessException;
import com.wl.cwa.shared.error.ErrorCode;
import com.wl.cwa.shared.error.FieldErrorDto;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Converts external {@code page/size/sort} query parameters into a Spring {@link PageRequest}.
 * Sort fields are whitelist-controlled so client input never reaches SQL order-by unvalidated;
 * {@code size} is capped at 100; pages are 0-based.
 */
@Component
public class PageRequestFactory {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "updatedAt", "username", "displayName");

    public PageRequest create(Integer page, Integer size, String sort) {
        int pageNumber = page == null ? 0 : Math.max(page, 0);
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(pageNumber, pageSize, parseSort(sort));
    }

    private Sort parseSort(String sort) {
        String candidate = sort == null || sort.isBlank() ? "createdAt,desc" : sort.trim();
        String[] parts = candidate.split(",", -1);
        String field = parts[0].trim();
        if (!SORTABLE_FIELDS.contains(field)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "Sort field is not allowed: " + field,
                    "排序字段不支持",
                    List.of(new FieldErrorDto("sort", "仅支持 " + String.join(", ", SORTABLE_FIELDS.stream().sorted().toList()))));
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length > 1) {
            String raw = parts[1].trim().toLowerCase(Locale.ROOT);
            if (!raw.equals("asc") && !raw.equals("desc")) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_FAILED,
                        "Sort direction must be asc or desc",
                        "排序方向不支持",
                        List.of(new FieldErrorDto("sort", "仅支持 asc 或 desc")));
            }
            direction = raw.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        }
        return Sort.by(direction, field);
    }
}
