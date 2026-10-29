package ru.aibuyer.mvp.auth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}","spring.datasource.username=${TEST_DATABASE_USER:sa}","spring.datasource.password=${TEST_DATABASE_PASSWORD:}","app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=","app.auth.attempt-limit=1000"})
@AutoConfigureMockMvc
class AuthIntegrationTest {
 @Autowired MockMvc http;@Autowired ObjectMapper json;@Autowired JdbcTemplate db;
 String register(String email)throws Exception{
  var response=http.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"name\":\" Buyer \",\"email\":\""+email+"\",\"password\":\"Password123\",\"companyName\":\" Store \"}"))
   .andExpect(status().isCreated()).andExpect(header().string("Location","/api/v1/auth/me")).andExpect(jsonPath("user.name").value("Buyer")).andExpect(jsonPath("company.name").value("Store")).andExpect(jsonPath("user.password_hash").doesNotExist()).andReturn().getResponse().getContentAsString();
  return json.readTree(response).get("accessToken").asText();
 }
 @Test void registerLoginProfileAndRevocation()throws Exception{
  String token=register("Buyer@Example.com");
  http.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("user.email").value("buyer@example.com"));
  var login=http.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"email\":\"BUYER@example.com\",\"password\":\"Password123\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  String second=json.readTree(login).get("accessToken").asText();
  http.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isNoContent()).andExpect(content().string(""));
  http.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
  http.perform(get("/api/v1/companies/current").header("Authorization","Bearer "+second)).andExpect(status().isOk());
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM revoked_session",Integer.class));
 }
 @Test void duplicateRollsBackCompany()throws Exception{
  register("duplicate@example.com");int before=db.queryForObject("SELECT COUNT(*) FROM company",Integer.class);
  http.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"name\":\"B\",\"email\":\"DUPLICATE@example.com\",\"password\":\"Password123\",\"companyName\":\"X\"}")).andExpect(status().isConflict()).andExpect(jsonPath("code").value("EMAIL_ALREADY_EXISTS"));
  assertEquals(before,db.queryForObject("SELECT COUNT(*) FROM company",Integer.class));
 }
 @Test void rejectsInvalidAndUnknownFields()throws Exception{
  http.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
  http.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"email\":\"missing@example.com\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("INVALID_CREDENTIALS"));
  http.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"email\":\"missing@example.com\",\"password\":\"wrong\",\"companyId\":\"1\"}")).andExpect(status().isBadRequest());
  http.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"name\":\"B\",\"email\":\"utf@example.com\",\"password\":\""+"я".repeat(37)+"\",\"companyName\":\"X\"}")).andExpect(status().isUnprocessableEntity());
 }
 @Test void companiesAreDistinct()throws Exception{
  String a=register("tenant-a@example.com"),b=register("tenant-b@example.com");
  var ra=http.perform(get("/api/v1/companies/current").header("Authorization","Bearer "+a)).andReturn().getResponse().getContentAsString();
  var rb=http.perform(get("/api/v1/companies/current").header("Authorization","Bearer "+b)).andReturn().getResponse().getContentAsString();
  assertNotEquals(json.readTree(ra).get("id"),json.readTree(rb).get("id"));
 }
 @Test void tamperingAndQueryAreRejected()throws Exception {
  String token=register("tamper@example.com");
  String[] parts=token.split("\\.");
  String signature=(parts[2].startsWith("A")?"B":"A")+parts[2].substring(1);
  http.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+parts[0]+"."+parts[1]+"."+signature)).andExpect(status().isUnauthorized());
  http.perform(get("/api/v1/auth/me?companyId=1").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
 }
 @Test void limiterIsPersistent()throws Exception {
  long now=java.time.Instant.now().getEpochSecond();
  db.update("INSERT INTO auth_rate_limit(bucket,window_start,attempts) VALUES (?,?,?)","192.0.2.10",now-now%60,1000);
  http.perform(post("/api/v1/auth/login").with(r->{r.setRemoteAddr("192.0.2.10");return r;}).contentType("application/json").content("{\"email\":\"missing@example.com\",\"password\":\"wrong\"}")).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
 }
}
