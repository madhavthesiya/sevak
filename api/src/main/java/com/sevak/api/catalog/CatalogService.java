package com.sevak.api.catalog;

import com.sevak.api.catalog.dto.ServiceItem;
import com.sevak.api.common.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final CatalogRepository repo;

    public CatalogService(CatalogRepository repo) {
        this.repo = repo;
    }

    public PageResponse<ServiceItem> listServices(String category, int page, int limit) {
        return PageResponse.of(
                repo.findServices(category, limit, PageResponse.offset(page, limit)),
                page,
                limit,
                repo.countServices(category));
    }
}
