package com.caremao.storemanager.campaign;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "campaign")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String slug;
    private String name;
    private String gmPassphraseHash;
    /** Si es false, un jugador puede gastar sus monedas pero no sumárselas; eso lo hace el GM. */
    private boolean playersCanAddCoins = true;
    private Instant createdAt = Instant.now();

    protected Campaign() {
    }

    public Campaign(String slug, String name, String gmPassphraseHash) {
        this.slug = slug;
        this.name = name;
        this.gmPassphraseHash = gmPassphraseHash;
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getGmPassphraseHash() {
        return gmPassphraseHash;
    }

    public boolean isPlayersCanAddCoins() {
        return playersCanAddCoins;
    }

    public void setPlayersCanAddCoins(boolean playersCanAddCoins) {
        this.playersCanAddCoins = playersCanAddCoins;
    }
}
