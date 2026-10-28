package com.caremao.storemanager.campaign;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.caremao.storemanager.auth.Actor;
import com.caremao.storemanager.auth.AuthDtos.MeResponse;
import com.caremao.storemanager.auth.AuthService;
import com.caremao.storemanager.auth.AuthService.Login;
import com.caremao.storemanager.campaign.CampaignDtos.CampaignDto;
import com.caremao.storemanager.campaign.CampaignDtos.CreateCampaignRequest;
import com.caremao.storemanager.campaign.CampaignDtos.ServerConfig;
import com.caremao.storemanager.campaign.CampaignDtos.UpdateCampaignRequest;
import com.caremao.storemanager.config.AppProperties;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class CampaignController {

    private final CampaignService campaigns;
    private final AuthService auth;
    private final AppProperties props;

    public CampaignController(CampaignService campaigns, AuthService auth, AppProperties props) {
        this.campaigns = campaigns;
        this.auth = auth;
        this.props = props;
    }

    @GetMapping("/config")
    public ServerConfig config() {
        return new ServerConfig(props.campaignCreationKeyRequired());
    }

    /** Crea la campaña y deja al creador con sesión de GM. */
    @PostMapping("/campaigns")
    public ResponseEntity<MeResponse> create(@Valid @RequestBody CreateCampaignRequest body) {
        Login login = auth.startGmSession(campaigns.create(body));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, auth.sessionCookie(login).toString())
                .body(login.me());
    }

    /** Reglas de la mesa de la campaña del GM con sesión. */
    @PatchMapping("/campaign")
    public CampaignDto update(Actor actor, @Valid @RequestBody UpdateCampaignRequest body) {
        return campaigns.update(actor, body);
    }
}
