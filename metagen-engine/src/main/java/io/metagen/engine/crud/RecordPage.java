package io.metagen.engine.crud;

import java.util.List;

/**
 * Generic paginated response for dynamic entity records.
 *
 * <p>Each record is represented as a {@code Map<String, Object>} because
 * the shape of the data varies per entity — there are no static Java types
 * for user-defined entities.</p>
 *
 * @param content       records for the current page
 * @param totalElements total matching records (before pagination)
 * @param totalPages    total number of pages
 * @param page          current page (0-based)
 * @param size          requested page size
 */
public record RecordPage<T>(
    List<T> content,
    long    totalElements,
    int     totalPages,
    int     page,
    int     size
) {
    /** Convenience factory — computes totalPages automatically. */
    public static <T> RecordPage<T> of(List<T> content, long totalElements, int page, int size) {
        int totalPages = size == 0 ? 1 : (int) Math.ceil((double) totalElements / size);
        return new RecordPage<>(content, totalElements, totalPages, page, size);
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    public boolean isFirst() {
        return page == 0;
    }

    public boolean isLast() {
        return page >= totalPages - 1;
    }
}
