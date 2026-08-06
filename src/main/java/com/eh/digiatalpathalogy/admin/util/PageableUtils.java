package com.eh.digiatalpathalogy.admin.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Objects;

public final class PageableUtils {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 50;

    private PageableUtils() {
    }

    public static Pageable create(Integer page, Integer size, String sortProperty, Sort.Direction direction) {

        int p = Math.max(0, Objects.requireNonNullElse(page, DEFAULT_PAGE));
        int s = Math.max(1, Objects.requireNonNullElse(size, DEFAULT_SIZE));

        if (isBlank(sortProperty)) {
            return PageRequest.of(p, s);
        }

        Sort.Direction dir = Objects.requireNonNullElse(direction, Sort.Direction.DESC);
        return PageRequest.of(p, s, Sort.by(dir, sortProperty.trim()));
    }

    public static Pageable createWithDefaultSort(Integer page, Integer size, Sort.Direction direction) {
        return create(page, size, "updatedAt", direction);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

}