package com.caremao.storemanager.srd;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Carga el catálogo SRD la primera vez que arranca la app (si la tabla está vacía). */
@Component
public class SrdImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SrdImporter.class);
    private static final String RESOURCE = "srd/srd-items-2014.json";

    record SeedItem(String index, String name, String source, String type, String category, String rarity,
            Double weight, Long valueCp, String description, Map<String, Object> properties) {
    }

    private final JdbcTemplate jdbc;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public SrdImporter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        Long count = jdbc.queryForObject("select count(*) from srd_item", Long.class);
        if (count != null && count > 0) {
            return;
        }
        List<SeedItem> items;
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            items = mapper.readValue(in, new TypeReference<>() {
            });
        }
        jdbc.batchUpdate("""
                insert into srd_item (srd_index, name, source, type, category, rarity, weight, value_cp, description, properties)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, items, 200, (ps, item) -> {
            ps.setString(1, item.index());
            ps.setString(2, item.name());
            ps.setString(3, item.source());
            ps.setString(4, item.type());
            ps.setString(5, item.category());
            ps.setString(6, item.rarity());
            ps.setObject(7, item.weight());
            ps.setObject(8, item.valueCp());
            ps.setString(9, item.description());
            ps.setString(10, item.properties() == null ? null : mapper.writeValueAsString(item.properties()));
        });
        log.info("Catálogo SRD cargado: {} items", items.size());
    }
}
