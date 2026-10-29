package ru.aibuyer.mvp.auth;
import static ru.aibuyer.mvp.auth.AuthDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
@RestController
@RequestMapping("/api/v1")
public class AuthController {
 private final AuthService service;
 public AuthController(AuthService service){this.service=service;}
 @PostMapping("/auth/register") public ResponseEntity<AuthResult> register(@Valid @RequestBody Register body){return ResponseEntity.created(URI.create("/api/v1/auth/me")).body(service.register(body));}
 @PostMapping("/auth/login") public AuthResult login(@Valid @RequestBody Login body){return service.login(body);}
 @GetMapping("/auth/me") public Profile me(@AuthenticationPrincipal Jwt jwt){return service.profile(jwt);}
 @GetMapping("/companies/current") public Company company(@AuthenticationPrincipal Jwt jwt){return service.profile(jwt).company();}
 @PostMapping("/auth/logout") public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt){service.logout(jwt);return ResponseEntity.noContent().build();}
}
