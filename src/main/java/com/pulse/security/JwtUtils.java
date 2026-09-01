package com.pulse.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 工具（算法 HS256）。
 *
 * <p>职责：签发 access token 与 refresh token，并提供校验与解析能力。
 * <ul>
 *   <li>access token：携带 subject(用户ID) 与 email，短期有效；</li>
 *   <li>refresh token：额外携带 {@code type=refresh} 声明，长期有效，仅用于无感续期，
 *       经 HttpOnly+Secure+SameSite Cookie 传递。</li>
 * </ul>
 *
 * <p>安全约束（SEC-05）：构造时若 {@code jwt.secret} 缺失或长度 &lt; 32 字节，
 * 直接抛异常使应用启动失败（fail-fast），杜绝沿用公开弱默认密钥伪造任意用户令牌。
 */
@Component
public class JwtUtils {

    private final SecretKey key;
    private final long expiration;
    private final long refreshExpiration;

    public JwtUtils(@Value("${jwt.secret}") String secret,
                    @Value("${jwt.expiration}") long expiration,
                    @Value("${jwt.refresh-expiration:604800000}") long refreshExpiration) {
        // SEC-05: JWT 密钥缺失或过短直接 fail-fast，避免沿用弱默认值带病上线
        // （历史上默认口令为公开字符串，可被用于伪造任意用户令牌）。
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secret == null || secret.isBlank() || keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET 未配置或过短（需 ≥ 32 字节 / 256 位）。"
                            + "请通过环境变量注入强随机密钥，缺失将拒绝启动。");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = expiration;
        this.refreshExpiration = refreshExpiration;
    }

    public String generateToken(Long userId, String email) {
        return generateToken(userId, email, 0);
    }

    /**
     * 签发 access token，并写入令牌代际号声明 {@code tv}（取自 {@code users.token_version}）。
     *
     * @param tokenVersion 用户当前令牌代际号，null 视为 0
     */
    public String generateToken(Long userId, String email, Integer tokenVersion) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("tv", tokenVersion == null ? 0 : tokenVersion)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        return generateRefreshToken(userId, 0);
    }

    /**
     * 签发 refresh token，并写入令牌代际号声明 {@code tv}。
     * 改密 / 重置密码 / 禁用账户后库中 token_version 自增，旧令牌的 tv 即失配，刷新时被拒（吊销）。
     *
     * @param tokenVersion 用户当前令牌代际号，null 视为 0
     */
    public String generateRefreshToken(Long userId, Integer tokenVersion) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .claim("tv", tokenVersion == null ? 0 : tokenVersion)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpiration))
                .signWith(key)
                .compact();
    }

    /**
     * 读取令牌中的代际号 {@code tv}；缺失或格式异常按 0 处理，
     * 保证升级前签发的旧令牌（无 tv 声明）与初始 token_version=0 兼容。
     */
    public int getTokenVersionFromToken(String token) {
        Object tv = parseToken(token).get("tv");
        if (tv instanceof Number number) {
            return number.intValue();
        }
        if (tv instanceof String str) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    public boolean validateRefreshToken(String token) {
        try {
            return "refresh".equals(parseToken(token).get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    public String getEmailFromToken(String token) {
        return parseToken(token).get("email", String.class);
    }

    /**
     * 签发一次性密码重置令牌（用途声明 purp=pwreset，15 分钟过期）。
     * 仅用于 /reset-password 自助设密，响应体中不包含任何明文凭据。
     */
    public String generatePasswordResetToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("purp", "pwreset")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 15 * 60_000L))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验密码重置令牌，返回用户 ID；签名无效、过期或用途不符（非 pwreset）抛 JwtException。
     */
    public Long parsePasswordResetToken(String token) {
        Claims claims = parseToken(token);
        if (!"pwreset".equals(claims.get("purp"))) {
            throw new JwtException("令牌用途不符");
        }
        return Long.parseLong(claims.getSubject());
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseToken(String token) {
        // 严格校验 JWS 结构：防止 jjwt 宽松 base64url 解码吞掉签名段尾部字符，
        // 导致「合法 token 末尾追加字符」仍被接受（签名校验形同虚设，可绕过鉴权）。
        assertWellFormedJws(token);
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 严格校验 JWS 三段结构与签名段规范编码，拒绝签名段含多余字符/非法字符的 token。
     * 仅当 signature 经严格 base64url 解码后规范重编码恰好等于原串时才放行；
     * 末尾被追加字符（如 {@code validToken + "x"}）会因重编码不一致而被拒。
     */
    private void assertWellFormedJws(String token) {
        if (token == null) {
            throw new JwtException("token 为空");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtException("token 结构非法");
        }
        String sig = parts[2];
        int pad = Math.floorMod(4 - sig.length(), 4);
        String padded = sig + "====".substring(4 - pad);
        byte[] sigBytes;
        try {
            sigBytes = BASE64URL.decode(padded);
        } catch (IllegalArgumentException e) {
            throw new JwtException("签名段 base64url 非法");
        }
        if (!BASE64URL_ENC.encodeToString(sigBytes).equals(sig)) {
            throw new JwtException("签名段存在多余字符，疑似被篡改");
        }
    }

    private static final Base64.Decoder BASE64URL = Base64.getUrlDecoder();
    private static final Base64.Encoder BASE64URL_ENC = Base64.getUrlEncoder().withoutPadding();
}
