package in.madipakkam.kudumbam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private static final Pattern PHONE = Pattern.compile("^\\+?[1-9][0-9]{7,14}$");
  private final JdbcTemplate jdbc;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
  @Value("${app.jwt-secret}") private String secret;
  @Value("${app.jwt-expiry-hours:72}") private long expiryHours;
  public AuthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public record AuthRequest(@NotBlank String identifier, @NotBlank @Size(min=8,max=72) String password, String name) {}
  public record AuthResponse(String accessToken, String tokenType, long expiresIn, String displayName) {}

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse register(@Valid @RequestBody AuthRequest request) {
    String id = normalise(request.identifier());
    if (request.name() == null || request.name().isBlank() || request.name().length() > 120)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your name.");
    String email = EMAIL.matcher(id).matches() ? id : null;
    String mobile = PHONE.matcher(id).matches() ? id : null;
    if (email == null && mobile == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address or international mobile number.");
    try {
      jdbc.update("INSERT INTO app_user(display_name,email,mobile,password_hash) VALUES(?,?,?,?)",
        request.name().trim(), email, mobile, encoder.encode(request.password()));
    } catch (DuplicateKeyException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This email or mobile number is already registered.");
    }
    return issue(request.name().trim(), id);
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody AuthRequest request) {
    String id = normalise(request.identifier());
    var rows = jdbc.query("SELECT display_name,password_hash FROM app_user WHERE email=? OR mobile=?",
      (rs,n) -> Map.of("name", rs.getString("display_name"), "hash", rs.getString("password_hash")), id, id);
    if (rows.isEmpty() || !encoder.matches(request.password(), (String)rows.get(0).get("hash")))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email/mobile number or password.");
    return issue((String)rows.get(0).get("name"), id);
  }

  @GetMapping("/me")
  public Map<String,String> me(@RequestHeader(value="Authorization", required=false) String auth) {
    String subject = verify(auth);
    return Map.of("identifier", subject);
  }

  private AuthResponse issue(String name, String subject) {
    long now = Instant.now().getEpochSecond(), exp = now + expiryHours * 3600;
    String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    String payload = b64("{\"sub\":\"" + subject + "\",\"name\":\"" + name.replace("\\","").replace("\"","") + "\",\"iat\":" + now + ",\"exp\":" + exp + "}");
    String unsigned = header + "." + payload;
    return new AuthResponse(unsigned + "." + sign(unsigned), "Bearer", expiryHours * 3600, name);
  }

  private String verify(String authorization) {
    try {
      if (authorization == null || !authorization.startsWith("Bearer ")) throw new Exception();
      String token = authorization.substring(7);
      String[] p = token.split("\\.");
      if (p.length != 3 || !MessageDigest.isEqual(sign(p[0]+"."+p[1]).getBytes(StandardCharsets.UTF_8), p[2].getBytes(StandardCharsets.UTF_8))) throw new Exception();
      String json = new String(Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8);
      java.util.regex.Matcher sub = Pattern.compile("\"sub\":\"([^\"]+)\"").matcher(json);
      java.util.regex.Matcher exp = Pattern.compile("\"exp\":([0-9]+)").matcher(json);
      if (!sub.find() || !exp.find() || Long.parseLong(exp.group(1)) < Instant.now().getEpochSecond()) throw new Exception();
      return sub.group(1);
    } catch (Exception e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired session."); }
  }
  private String sign(String value) {
    try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")); return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))); }
    catch(Exception e){ throw new IllegalStateException(e); }
  }
  private String b64(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
  private String normalise(String value) { return value.trim().toLowerCase(Locale.ROOT).replace(" ",""); }
}
