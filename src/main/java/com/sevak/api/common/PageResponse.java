package com.sevak.api.common;

import java.util.List;

/** One page of results plus what a client needs to build paging controls. */
public record PageResponse<T>(List<T> data, int page, int limit, long total, int totalPages) {

    public static <T> PageResponse<T> of(List<T> data, int page, int limit, long total) {
        return new PageResponse<>(data, page, limit, total, (int) ((total + limit - 1) / limit));
    }

    /** Uses long arithmetic so a very large page number can't overflow into a negative OFFSET. */
    public static long offset(int page, int limit) {
        return (page - 1L) * limit;
    }
}
