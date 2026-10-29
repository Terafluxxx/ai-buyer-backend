package ru.aibuyer.mvp.catalog;
import java.net.URI;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1")
public class CatalogController {
 private final CatalogService service;
 public CatalogController(CatalogService service){this.service=service;}
 private long company(Jwt jwt){return CatalogService.id(jwt.getClaimAsString("companyId"));}
 private Map<String,String> query(HttpServletRequest r,String... allowed){var names=Set.of(allowed);var out=new HashMap<String,String>();r.getParameterMap().forEach((k,v)->{if(!names.contains(k)||v.length!=1||v[0].isEmpty())throw CatalogService.error(400,"BAD_REQUEST");out.put(k,v[0]);});return out;}
 @GetMapping("/products") public Object products(@AuthenticationPrincipal Jwt jwt,HttpServletRequest r){return service.products(company(jwt),query(r,"page","size","q","sort","category"));}
 @PostMapping("/products") public Object create(@AuthenticationPrincipal Jwt jwt,@RequestBody JsonNode b,HttpServletRequest r){query(r);var p=service.create(company(jwt),b);return ResponseEntity.created(URI.create("/api/v1/products/"+p.get("id"))).body(p);}
 @GetMapping("/products/{id}") public Object product(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,HttpServletRequest r){query(r);return service.product(company(jwt),CatalogService.id(id));}
 @PatchMapping("/products/{id}") public Object patch(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@RequestBody JsonNode b,HttpServletRequest r){query(r);return service.patch(company(jwt),CatalogService.id(id),b);}
 @DeleteMapping("/products/{id}") public Object delete(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,HttpServletRequest r){var q=query(r,"version");if(!q.containsKey("version"))throw CatalogService.error(400,"BAD_REQUEST");service.delete(company(jwt),CatalogService.id(id),CatalogService.number(q.get("version"),0));return ResponseEntity.noContent().build();}
 @GetMapping("/inventory") public Object inventoryList(@AuthenticationPrincipal Jwt jwt,HttpServletRequest r){return service.inventories(company(jwt),query(r,"page","size","productId"));}
 @GetMapping("/products/{id}/inventory") public Object inventory(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,HttpServletRequest r){query(r);return service.inventory(company(jwt),CatalogService.id(id));}
 @PutMapping("/products/{id}/inventory") public Object inventorySet(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@RequestBody JsonNode b,HttpServletRequest r){query(r);return service.setInventory(company(jwt),CatalogService.id(id),b);}
 @PutMapping("/products/{id}/sales/{day}") public Object sale(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@PathVariable String day,@RequestBody JsonNode b,HttpServletRequest r){query(r);return service.setSale(company(jwt),CatalogService.id(id),day,b);}
 @GetMapping("/sales") public Object sales(@AuthenticationPrincipal Jwt jwt,HttpServletRequest r){return service.sales(company(jwt),query(r,"page","size","productId","dateFrom","dateTo"),false);}
 @GetMapping("/sales/summary") public Object summary(@AuthenticationPrincipal Jwt jwt,HttpServletRequest r){return service.sales(company(jwt),query(r,"productId","dateFrom","dateTo"),true);}
}
