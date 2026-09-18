package {{ package }}.shared.api;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Contract pagination envelope: {@code items/page/size/totalElements/totalPages} as defined by the
 * OpenAPI {@code UserPage} schema. Page numbers are 0-based externally.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements, int totalPages) {
        return new PageResponse<>(List.copyOf(items), page, size, totalElements, totalPages);
    }
}
