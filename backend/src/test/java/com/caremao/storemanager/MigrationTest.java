package com.caremao.storemanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * La base de THREE está en V2 con datos reales: las migraciones nuevas tienen que dejarlos igual que
 * antes (valor conocido, nada oculto) y darle un QR de vitrina a cada tienda.
 */
class MigrationTest {

    private final String url = "jdbc:h2:mem:migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

    private Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration");
        return (target == null ? config : config.target(target)).load();
    }

    @Test
    void existingDataSurvivesTheNewMigrations() throws SQLException {
        flyway("2").migrate();
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement()) {
            s.execute("insert into campaign (id, slug, name, gm_passphrase_hash, created_at) values "
                    + "('00000000-0000-0000-0000-000000000001', 'mesa', 'Mesa', 'x', now())");
            s.execute("insert into inventory (id, campaign_id, kind, name, created_at, updated_at) values "
                    + "('00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000001', 'SHOP', "
                    + "'Tienda', now(), now())");
            s.execute("insert into inventory (id, campaign_id, kind, name, access_token, created_at, updated_at) values "
                    + "('00000000-0000-0000-0000-000000000020', '00000000-0000-0000-0000-000000000001', 'CHARACTER', "
                    + "'Gharra', 'tok', now(), now())");
            s.execute("insert into item (id, inventory_id, name, type, rarity, quantity, value_cp, created_at, updated_at) "
                    + "values ('00000000-0000-0000-0000-000000000100', '00000000-0000-0000-0000-000000000020', "
                    + "'Gema', 'MISC', 'COMMON', 1, 1234, now(), now())");
            s.execute("insert into trade_request (id, campaign_id, shop_id, character_id, item_id, item_name, kind, "
                    + "quantity, unit_price_cp, status, created_at) values ('00000000-0000-0000-0000-000000001000', "
                    + "'00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010', "
                    + "'00000000-0000-0000-0000-000000000020', '00000000-0000-0000-0000-000000000100', 'Gema', 'SELL', "
                    + "1, 617, 'PENDING', now())");
        }

        flyway(null).migrate();

        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement()) {
            try (ResultSet item = s.executeQuery(
                    "select perceived_value_cp, value_revealed, negotiable, hidden, price_denomination from item")) {
                item.next();
                assertThat(item.getLong("perceived_value_cp")).as("lo que cree el dueño = lo que valía").isEqualTo(1234);
                assertThat(item.getBoolean("value_revealed")).isTrue();
                assertThat(item.getBoolean("negotiable")).isFalse();
                assertThat(item.getBoolean("hidden")).isFalse();
                assertThat(item.getString("price_denomination")).as("null = oro").isNull();
            }
            try (ResultSet shops = s.executeQuery("select kind, public_token from inventory order by kind")) {
                shops.next();
                assertThat(shops.getString("kind")).isEqualTo("CHARACTER");
                assertThat(shops.getString("public_token")).isNull();
                shops.next();
                assertThat(shops.getString("public_token")).as("cada tienda recibe su QR de vitrina").hasSize(32);
            }
            try (ResultSet trade = s.executeQuery("select list_unit_price_cp, last_offer_by from trade_request")) {
                trade.next();
                assertThat(trade.getLong("list_unit_price_cp")).isEqualTo(617);
                assertThat(trade.getString("last_offer_by")).isNull();
            }
            try (ResultSet rules = s.executeQuery("select players_can_add_coins from campaign")) {
                rules.next();
                assertThat(rules.getBoolean("players_can_add_coins")).isTrue();
            }
        }
    }
}
