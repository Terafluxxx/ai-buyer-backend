package ru.aibuyer.mvp.auth;
import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
/** Database-backed fixed-window limiter. Forwarded IP headers are deliberately not trusted. */
final class AuthRequestFilter extends OncePerRequestFilter {
 private final JdbcTemplate db;private final ObjectMapper json;private final int limit,window;
 AuthRequestFilter(JdbcTemplate db,ObjectMapper json,int limit,int window){
  if(limit<1||window<1)throw new IllegalArgumentException("Invalid auth rate limits");
  this.db=db;this.json=json;this.limit=limit;this.window=window;
 }
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  String path=req.getRequestURI().substring(req.getContextPath().length());
  if(path.startsWith("/api/v1/auth/")||path.equals("/api/v1/companies/current")){
   if(req.getQueryString()!=null){error(res,400,"BAD_REQUEST","Query-параметры не поддерживаются");return;}
  }
  if(req.getMethod().equals("POST")&&(path.equals("/api/v1/auth/login")||path.equals("/api/v1/auth/register"))){
   long now=java.time.Instant.now().getEpochSecond();long start=now-now%window;
   String bucket=req.getRemoteAddr();
   // Atomic insert/update per statement; a conflicting first insert is retried as update.
   int updated=db.update("UPDATE auth_rate_limit SET attempts=CASE WHEN window_start=? THEN attempts+1 ELSE 1 END, window_start=? WHERE bucket=?",start,start,bucket);
   if(updated==0){try{db.update("INSERT INTO auth_rate_limit(bucket,window_start,attempts) VALUES (?,?,1)",bucket,start);}catch(org.springframework.dao.DuplicateKeyException e){db.update("UPDATE auth_rate_limit SET attempts=CASE WHEN window_start=? THEN attempts+1 ELSE 1 END, window_start=? WHERE bucket=?",start,start,bucket);}}
   int count=db.queryForObject("SELECT attempts FROM auth_rate_limit WHERE bucket=?",Integer.class,bucket);
   if(count>limit){res.setHeader("Retry-After",Long.toString(window-now%window));error(res,429,"TOO_MANY_REQUESTS","Слишком много попыток");return;}
  }
  chain.doFilter(req,res);
 }
 private void error(HttpServletResponse res,int status,String code,String message)throws IOException{res.setStatus(status);res.setContentType("application/json");json.writeValue(res.getOutputStream(),ApiError.of(code,message));}
}
