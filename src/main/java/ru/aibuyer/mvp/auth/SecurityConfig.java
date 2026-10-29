package ru.aibuyer.mvp.auth;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
@Configuration
public class SecurityConfig {
 @Bean SecretKeySpec jwtKey(@Value("${app.jwt.secret}") String secret) {
  byte[] bytes=Base64.getDecoder().decode(secret);
  if(bytes.length<32)throw new IllegalArgumentException("JWT_SECRET requires at least 32 random bytes, base64 encoded");
  return new SecretKeySpec(bytes,"HmacSHA256");
 }
 @Bean JwtEncoder encoder(SecretKeySpec key){return new NimbusJwtEncoder(new ImmutableSecret<>(key));}
 @Bean JwtDecoder decoder(SecretKeySpec key,JdbcTemplate db,@Value("${app.jwt.issuer}") String issuer,@Value("${app.jwt.audience}") String audience){
  var decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
  OAuth2TokenValidator<Jwt> claims=jwt->{
   boolean valid=false;
   try {
    valid=jwt.getAudience().contains(audience)&&jwt.getExpiresAt()!=null&&jwt.getIssuedAt()!=null
     &&jwt.getIssuedAt().isBefore(java.time.Instant.now().plusSeconds(1))
     &&jwt.getExpiresAt().isAfter(java.time.Instant.now())
     &&Long.parseLong(jwt.getSubject())>0&&Long.parseLong(jwt.getClaimAsString("companyId"))>0;
    UUID.fromString(jwt.getClaimAsString("sid"));
    valid=valid&&db.queryForObject("SELECT COUNT(*) FROM revoked_session WHERE sid=?",Integer.class,jwt.getClaimAsString("sid"))==0;
   }catch(IllegalArgumentException|NullPointerException e){valid=false;}
   return valid?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
  };
  decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),claims));
  return decoder;
 }
 @Bean SecurityFilterChain security(HttpSecurity http,ObjectMapper json,JdbcTemplate db,
   @Value("${app.auth.attempt-limit}") int limit,@Value("${app.auth.window-seconds}") int window) throws Exception {
  http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers(org.springframework.http.HttpMethod.POST,"/api/v1/auth/register","/api/v1/auth/login").permitAll().anyRequest().authenticated())
   .oauth2ResourceServer(o->o.jwt(j->{}).authenticationEntryPoint((req,res,e)->{res.setStatus(401);res.setContentType("application/json");json.writeValue(res.getOutputStream(),ApiError.of("UNAUTHORIZED","Необходим действующий JWT"));}))
   .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->{res.setStatus(401);res.setContentType("application/json");json.writeValue(res.getOutputStream(),ApiError.of("UNAUTHORIZED","Необходим действующий JWT"));}).accessDeniedHandler((req,res,ex)->{res.setStatus(403);res.setContentType("application/json");json.writeValue(res.getOutputStream(),ApiError.of("FORBIDDEN","Недостаточно прав"));}));
  http.addFilterBefore(new AuthRequestFilter(db,json,limit,window),BearerTokenAuthenticationFilter.class);
  return http.build();
 }
}
