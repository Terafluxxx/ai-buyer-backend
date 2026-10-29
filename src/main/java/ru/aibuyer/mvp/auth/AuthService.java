package ru.aibuyer.mvp.auth;
import static ru.aibuyer.mvp.auth.AuthDtos.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AuthService {
 private final JdbcTemplate db; private final JwtEncoder encoder;
 private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(12);
 private final String dummy=passwords.encode("dummy-password-for-timing");
 private final String issuer,audience;
 public AuthService(JdbcTemplate db,JwtEncoder encoder,@Value("${app.jwt.issuer}") String issuer,@Value("${app.jwt.audience}") String audience) {
  this.db=db;this.encoder=encoder;this.issuer=issuer;this.audience=audience;
 }
 private void password(String password,int min) {
  int bytes=password.getBytes(StandardCharsets.UTF_8).length;
  if(bytes<min||bytes>72) throw new ApiException(422,"VALIDATION_ERROR","Пароль должен содержать от "+min+" до 72 байт UTF-8");
 }
 private long insert(String sql,Object... args) {
  var keys=new GeneratedKeyHolder();
  db.update(c->{var p=c.prepareStatement(sql,new String[]{"id"});for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},keys);
  return Objects.requireNonNull(keys.getKey()).longValue();
 }
 @Transactional public AuthResult register(Register input) {
  password(input.password(),8);
  long company=insert("INSERT INTO company(name) VALUES (?)",input.companyName());
  long user=insert("INSERT INTO app_user(company_id,name,email,password_hash) VALUES (?,?,?,?)",company,input.name(),input.email().toLowerCase(Locale.ROOT),passwords.encode(input.password()));
  return token(profile(user,company));
 }
 public AuthResult login(Login input) {
  password(input.password(),1);
  var rows=db.queryForList("SELECT id,company_id,password_hash FROM app_user WHERE email=?",input.email().toLowerCase(Locale.ROOT));
  String hash=rows.isEmpty()?dummy:(String)rows.get(0).get("password_hash");
  boolean valid=passwords.matches(input.password(),hash);
  if(rows.isEmpty()||!valid)throw new ApiException(401,"INVALID_CREDENTIALS","Неверный email или пароль");
  var row=rows.get(0);
  return token(profile(((Number)row.get("id")).longValue(),((Number)row.get("company_id")).longValue()));
 }
 public Profile profile(long user,long company) {
  var users=db.query("SELECT * FROM app_user WHERE id=? AND company_id=?",(r,n)->new User(r.getString("id"),r.getObject("created_at",OffsetDateTime.class).toInstant().toString(),r.getString("company_id"),r.getString("name"),r.getString("email")),user,company);
  if(users.isEmpty())throw new ApiException(401,"UNAUTHORIZED","Сессия недействительна");
  var c=db.queryForObject("SELECT * FROM company WHERE id=?",(r,n)->new Company(r.getString("id"),r.getObject("created_at",OffsetDateTime.class).toInstant().toString(),r.getString("name")),company);
  return new Profile(users.get(0),c);
 }
 public Profile profile(Jwt jwt) {return profile(Long.parseLong(jwt.getSubject()),Long.parseLong(jwt.getClaimAsString("companyId")));}
 private AuthResult token(Profile p) {
  var now=Instant.now();
  var claims=JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject(p.user().id()).claim("companyId",p.company().id()).claim("sid",UUID.randomUUID().toString()).issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
  String token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
  return new AuthResult(token,"Bearer",3600,p.user(),p.company());
 }
 public void logout(Jwt jwt) {
  try {db.update("INSERT INTO revoked_session(sid,expires_at) VALUES (?,?)",jwt.getClaimAsString("sid"),OffsetDateTime.ofInstant(jwt.getExpiresAt(),java.time.ZoneOffset.UTC));}
  catch(org.springframework.dao.DuplicateKeyException e){throw new ApiException(401,"TOKEN_REVOKED","Сессия отозвана");}
 }
}
