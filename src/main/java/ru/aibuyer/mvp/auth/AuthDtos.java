package ru.aibuyer.mvp.auth;
import jakarta.validation.constraints.*;
public final class AuthDtos {
 private AuthDtos() {}
 static String trim(String s) { return s==null?null:s.trim(); }
 public record Register(@NotBlank @Size(max=255) String name, @NotBlank @Email @Size(max=255) String email,
                        @NotNull @Size(min=8,max=72) String password, @NotBlank @Size(max=255) String companyName) {
  public Register { name=trim(name); email=trim(email); companyName=trim(companyName); }
 }
 public record Login(@NotBlank @Email @Size(max=255) String email,@NotNull @Size(min=1,max=72) String password) {
  public Login { email=trim(email); }
 }
 public record Company(String id,String createdAt,String name) {}
 public record User(String id,String createdAt,String companyId,String name,String email) {}
 public record Profile(User user,Company company) {}
 public record AuthResult(String accessToken,String tokenType,int expiresIn,User user,Company company) {}
}
