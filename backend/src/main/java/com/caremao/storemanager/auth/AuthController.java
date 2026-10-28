package com.caremao.storemanager.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.caremao.storemanager.auth.AuthDtos.GmLoginRequest;
import com.caremao.storemanager.auth.AuthDtos.JoinRequest;
import com.caremao.storemanager.auth.AuthDtos.MeResponse;
import com.caremao.storemanager.auth.AuthService.Login;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/campaigns/{slug}/gm-login")
    public ResponseEntity<MeResponse> gmLogin(@PathVariable String slug, @Valid @RequestBody GmLoginRequest body,
            HttpServletRequest request) {
        return withSession(auth.gmLogin(slug, body.passphrase(), request.getRemoteAddr()));
    }

    @PostMapping("/join")
    public ResponseEntity<MeResponse> join(@Valid @RequestBody JoinRequest body) {
        return withSession(auth.join(body.token()));
    }

    @GetMapping("/me")
    public MeResponse me(Actor actor) {
        return auth.me(actor);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Actor actor) {
        auth.logout(actor);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, auth.clearedCookie().toString()).build();
    }

    ResponseEntity<MeResponse> withSession(Login login) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, auth.sessionCookie(login).toString())
                .body(login.me());
    }
}
