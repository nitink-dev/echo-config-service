package com.eh.digiatalpathalogy.admin.model;


import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (int) Math.ceil(totalElements / (double) size);
        boolean hasNext = page + 1 < totalPages;
        boolean hasPrevious = page > 0;
        return new PageResponse<>(content, page, size, totalElements, totalPages, hasNext, hasPrevious);
    }
}
