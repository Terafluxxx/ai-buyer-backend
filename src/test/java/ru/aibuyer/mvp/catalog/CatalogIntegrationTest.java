package ru.aibuyer.mvp.catalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:catalog;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}","spring.datasource.username=${TEST_DATABASE_USER:sa}","spring.datasource.password=${TEST_DATABASE_PASSWORD:}","app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=","app.auth.attempt-limit=1000"})
@AutoConfigureMockMvc
class CatalogIntegrationTest {
 @Autowired MockMvc http;@Autowired ObjectMapper json;
 String token()throws Exception {String email=java.util.UUID.randomUUID()+"@example.com";return json.readTree(http.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"name\":\"B\",\"email\":\""+email+"\",\"password\":\"Password123\",\"companyName\":\"Store\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();}
 String product(String t)throws Exception{return json.readTree(http.perform(post("/api/v1/products").header("Authorization","Bearer "+t).contentType("application/json").content("{\"sku\":\"TEA\",\"name\":\"Tea\",\"category\":\"Drink\"}")).andExpect(status().isCreated()).andExpect(jsonPath("version").value(1)).andReturn().getResponse().getContentAsString()).get("id").asText();}
 @Test void inventorySalesAndDeletion()throws Exception{
  String t=token(),id=product(t),day=java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1).toString();String path="/api/v1/products/"+id;
  http.perform(get(path+"/inventory").header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("quantity").value(0));
  http.perform(put(path+"/inventory").header("Authorization","Bearer "+t).contentType("application/json").content("{\"quantity\":10,\"version\":1}")).andExpect(status().isOk()).andExpect(jsonPath("version").value(2));
  http.perform(put(path+"/inventory").header("Authorization","Bearer "+t).contentType("application/json").content("{\"quantity\":20,\"version\":1}")).andExpect(status().isConflict());
  http.perform(put(path+"/sales/"+day).header("Authorization","Bearer "+t).contentType("application/json").content("{\"quantity\":60,\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("version").value(1));
  http.perform(put(path+"/sales/"+day).header("Authorization","Bearer "+t).contentType("application/json").content("{\"quantity\":60,\"version\":1}")).andExpect(status().isOk()).andExpect(jsonPath("version").value(1));
  http.perform(get(path+"/inventory").header("Authorization","Bearer "+t)).andExpect(jsonPath("quantity").value(10));
  http.perform(get("/api/v1/sales/summary?dateFrom="+day+"&dateTo="+day).header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("totalQuantity").value(60)).andExpect(jsonPath("productCount").value(1));
  http.perform(delete(path+"?version=1").header("Authorization","Bearer "+t)).andExpect(status().isConflict());
 }
 @Test void isolationAndValidation()throws Exception{
  String a=token(),b=token(),id=product(a);product(b);
  http.perform(get("/api/v1/products/"+id).header("Authorization","Bearer "+b)).andExpect(status().isNotFound());
  http.perform(get("/api/v1/inventory?productId="+id).header("Authorization","Bearer "+b)).andExpect(status().isNotFound());
  http.perform(put("/api/v1/products/"+id+"/inventory").header("Authorization","Bearer "+b).contentType("application/json").content("{\"quantity\":8,\"version\":1}")).andExpect(status().isNotFound());
  http.perform(put("/api/v1/products/"+id+"/inventory").header("Authorization","Bearer "+a).contentType("application/json").content("{\"quantity\":1.5,\"version\":1}")).andExpect(status().isUnprocessableEntity());
  http.perform(get("/api/v1/products?size=101").header("Authorization","Bearer "+a)).andExpect(status().isBadRequest());
  http.perform(get("/api/v1/products?unknown=x").header("Authorization","Bearer "+a)).andExpect(status().isBadRequest());
 }
 @Test void patchNoopPaginationAndUniqueSku()throws Exception{
  String t=token(),id=product(t),path="/api/v1/products/"+id;
  http.perform(patch(path).header("Authorization","Bearer "+t).contentType("application/json").content("{\"name\":\"Tea\",\"version\":1}")).andExpect(status().isOk()).andExpect(jsonPath("version").value(1));
  http.perform(patch(path).header("Authorization","Bearer "+t).contentType("application/json").content("{\"category\":null,\"version\":1}")).andExpect(status().isOk()).andExpect(jsonPath("version").value(2));
  http.perform(post("/api/v1/products").header("Authorization","Bearer "+t).contentType("application/json").content("{\"sku\":\"TEA\",\"name\":\"duplicate\"}")).andExpect(status().isConflict()).andExpect(jsonPath("code").value("SKU_ALREADY_EXISTS"));
  http.perform(get("/api/v1/products?q=tea&page=2&size=1").header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("items").isEmpty()).andExpect(jsonPath("totalElements").value(1));
  http.perform(delete(path+"?version=2").header("Authorization","Bearer "+t)).andExpect(status().isNoContent());
  http.perform(get(path).header("Authorization","Bearer "+t)).andExpect(status().isNotFound());
 }
}
