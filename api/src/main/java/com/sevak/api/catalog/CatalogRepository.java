package com.sevak.api.catalog;

import com.sevak.api.catalog.dto.AreaOption;
import com.sevak.api.catalog.dto.ServiceItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {

    private static final String FROM_SERVICES = """
            FROM services s
            JOIN categories cat ON s.category_id = cat.category_id
            WHERE (CAST(:category AS text) IS NULL OR LOWER(cat.category_name) = LOWER(:category))
            """;

    public static final String SERVICES_SQL = """
            SELECT s.service_id, cat.category_name, s.service_name, s.base_price, s.description
            """ + FROM_SERVICES + """
            ORDER BY cat.category_name, s.base_price, s.service_id
            LIMIT :limit OFFSET :offset
            """;

    private final JdbcClient jdbc;

    public CatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ServiceItem> findServices(String category, int limit, long offset) {
        Map<String, Object> params = new HashMap<>();
        params.put("category", category);
        params.put("limit", limit);
        params.put("offset", offset);
        return jdbc.sql(SERVICES_SQL).params(params).query(ServiceItem.class).list();
    }

    public List<AreaOption> findAreas() {
        return jdbc.sql("""
                        SELECT ci.city_id, ci.city_name, ar.area_id, ar.area_name, ar.pincode
                        FROM areas ar
                        JOIN cities ci ON ci.city_id = ar.city_id
                        ORDER BY ci.city_name, ar.area_name
                        """).query(AreaOption.class).list();
    }

    public long countServices(String category) {
        Map<String, Object> params = new HashMap<>();
        params.put("category", category);
        return jdbc.sql("SELECT COUNT(*) " + FROM_SERVICES)
                .params(params)
                .query(Long.class)
                .single();
    }
}
