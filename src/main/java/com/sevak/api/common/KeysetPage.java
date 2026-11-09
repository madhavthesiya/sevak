package com.sevak.api.common;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A page read with keyset pagination: to get the next page, send nextBefore and nextBeforeId back.
 * Unlike OFFSET, the cost doesn't grow with how deep the client has paged.
 */
public record KeysetPage<T>(List<T> data, boolean hasMore, OffsetDateTime nextBefore, Integer nextBeforeId) {}
