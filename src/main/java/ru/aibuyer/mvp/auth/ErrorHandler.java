package ru.aibuyer.mvp.auth;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DuplicateKeyException;
import org.slf4j.LoggerFactory;
@RestControllerAdvice
public class ErrorHandler {
 @ExceptionHandler(ApiException.class) ResponseEntity<ApiError> business(ApiException e) {
  return ResponseEntity.status(e.status).body(ApiError.of(e.code,e.getMessage()));
 }
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
  var error=ApiError.of("VALIDATION_ERROR","Проверьте данные запроса");
  return ResponseEntity.unprocessableEntity().body(new ApiError(error.code(),error.message(),error.traceId(),e.getBindingResult().getFieldErrors().stream().map(f->new ApiError.Detail(f.getField(),"INVALID_VALUE",f.getDefaultMessage())).toList()));
 }
 @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<ApiError> malformed(Exception e) {
  return ResponseEntity.badRequest().body(ApiError.of("BAD_REQUEST","Некорректный JSON"));
 }
 @ExceptionHandler(DuplicateKeyException.class) ResponseEntity<ApiError> duplicate(Exception e) {
  return ResponseEntity.status(409).body(ApiError.of("EMAIL_ALREADY_EXISTS","Email уже зарегистрирован"));
 }
 @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class) ResponseEntity<ApiError> media(Exception e) {
  return ResponseEntity.status(415).body(ApiError.of("UNSUPPORTED_MEDIA_TYPE","Ожидается application/json"));
 }
 @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class) ResponseEntity<ApiError> method(Exception e) {
  return ResponseEntity.status(405).body(ApiError.of("BAD_REQUEST","Метод не поддерживается"));
 }
 @ExceptionHandler(Exception.class) ResponseEntity<ApiError> unexpected(Exception e) {
  var error=ApiError.of("INTERNAL_ERROR","Внутренняя ошибка");
  LoggerFactory.getLogger(getClass()).error("traceId={}",error.traceId(),e);
  return ResponseEntity.internalServerError().body(error);
 }
}
