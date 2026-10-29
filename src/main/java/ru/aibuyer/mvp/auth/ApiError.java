package ru.aibuyer.mvp.auth;
import java.util.List;
import java.util.UUID;
public record ApiError(String code, String message, String traceId, List<Detail> details) {
 public record Detail(String field, String code, String message) {}
 public static ApiError of(String code, String message) { return new ApiError(code, message, UUID.randomUUID().toString(), List.of()); }
}
