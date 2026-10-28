package com.caremao.storemanager.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.caremao.storemanager.admin.AdminDtos.AdminCampaignDto;
import com.caremao.storemanager.admin.AdminDtos.AdminLoginRequest;
import com.caremao.storemanager.admin.AdminDtos.ResetPassphraseRequest;
import com.caremao.storemanager.auth.Actor;
import com.caremao.storemanager.auth.AuthDtos.MeResponse;
import com.caremao.storemanager.auth.AuthService;
import com.caremao.storemanager.auth.AuthService.Login;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService admin;
    private final AuthService auth;

    public AdminController(AdminService admin, AuthService auth) {
        this.admin = admin;
        this.auth = auth;
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody AdminLoginRequest body, HttpServletRequest request) {
        String token = admin.login(body.key(), request.getRemoteAddr());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, admin.sessionCookie(token).toString()).build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Admin current) {
        admin.logout(current);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, admin.clearedCookie().toString()).build();
    }

    /** 204 con sesión, 401 sin ella, 404 si el portal está apagado. */
    @GetMapping("/session")
    public ResponseEntity<Void> session(Admin current) {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/campaigns")
    public List<AdminCampaignDto> campaigns(Admin current) {
        return admin.campaigns();
    }

    /** Deja a este navegador como GM de la campaña; la sesión del portal sigue abierta aparte. */
    @PostMapping("/campaigns/{id}/gm-session")
    public ResponseEntity<MeResponse> enterAsGm(Admin current, @PathVariable UUID id, HttpServletRequest request) {
        Actor actor = (Actor) request.getAttribute(Actor.REQUEST_ATTRIBUTE);
        Login login = admin.enterAsGm(id, actor);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, auth.sessionCookie(login).toString()).body(login.me());
    }

    @PutMapping("/campaigns/{id}/passphrase")
    public ResponseEntity<Void> resetPassphrase(Admin current, @PathVariable UUID id,
            @Valid @RequestBody ResetPassphraseRequest body) {
        admin.resetPassphrase(id, body.passphrase());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/campaigns/{id}")
    public ResponseEntity<Void> deleteCampaign(Admin current, @PathVariable UUID id) {
        admin.deleteCampaign(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> closeSession(Admin current, @PathVariable UUID id) {
        admin.closeSession(id);
        return ResponseEntity.noContent().build();
    }
}
