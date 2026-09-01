package com.pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pulse.common.BusinessException;
import com.pulse.dto.LoginRequest;
import com.pulse.dto.LoginResponse;
import com.pulse.dto.RegisterRequest;
import com.pulse.dto.TokenPair;
import com.pulse.dto.UpdateProfileRequest;
import com.pulse.entity.User;
import com.pulse.mapper.UserMapper;
import com.pulse.security.JwtUtils;
import com.pulse.service.MailService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.JwtException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证与账户服务。
 *
 * <p>负责注册、登录、刷新令牌、改密、个人资料更新，以及（MVP 占位）密码重置请求。
 * 密码统一经 BCrypt 哈希；返回的 {@code LoginResponse}/{@code User} 会回填
 * {@code isAdmin}（系统管理员，依据 {@code users.is_system_admin}）与 {@code mustChangePassword}。
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final MailService mailService;

    // 账户级登录失败计数（邮箱小写 -> 失败记录），用于临时锁定防撞库。
    // 注意：内存计数仅单实例生效；多实例部署须迁移至 Redis 共享计数。
    private final ConcurrentHashMap<String, FailRecord> failMap = new ConcurrentHashMap<>();
    private static final int MAX_FAIL = 5;
    private static final long LOCK_MS = 15 * 60_000L;

    public AuthService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder, JwtUtils jwtUtils,
                       MailService mailService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.mailService = mailService;
    }

    private static class FailRecord {
        int failCount;
        long lockUntil;
    }

    private void registerFail(String email) {
        FailRecord rec = failMap.computeIfAbsent(email, k -> new FailRecord());
        rec.failCount++;
        if (rec.failCount >= MAX_FAIL) {
            rec.lockUntil = System.currentTimeMillis() + LOCK_MS;
        }
    }

    private void clearFail(String email) {
        failMap.remove(email);
    }

    public LoginResponse register(RegisterRequest req) {
        boolean exists = userMapper.exists(
                new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail()));
        if (exists) {
            throw new BusinessException("邮箱已被注册");
        }
        User user = new User();
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setDisplayName(req.getDisplayName());
        // enabled 默认 true（User 实体字段默认值）
        userMapper.insert(user);

        String token = jwtUtils.generateToken(user.getId(), user.getEmail(), user.getTokenVersion());
        LoginResponse resp = new LoginResponse(token, user.getId(), user.getDisplayName(), user.getEmail());
        resp.setMustChangePassword(user.getMustChangePassword() != null && user.getMustChangePassword());
        resp.setIsSystemAdmin(user.getIsSystemAdmin() != null && user.getIsSystemAdmin());
        return resp;
    }

    public LoginResponse login(LoginRequest req) {
        // 账户级锁定：同一邮箱连续失败达阈值后临时锁定，与 IP 级限流互补防撞库
        String email = (req.getEmail() == null ? "" : req.getEmail().toLowerCase());
        FailRecord rec = failMap.get(email);
        long now = System.currentTimeMillis();
        if (rec != null && rec.failCount >= MAX_FAIL && now < rec.lockUntil) {
            long remainMin = Math.max(1, (rec.lockUntil - now + 59_999) / 60_000);
            throw new BusinessException("账户已临时锁定，请于 " + remainMin + " 分钟后重试");
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            registerFail(email); // 不论邮箱是否存在都计入失败，避免被用于探测账号
            throw new BusinessException("邮箱或密码错误");
        }
        clearFail(email);
        if (user.getEnabled() != null && !user.getEnabled()) {
            throw new BusinessException("账户已被禁用，请联系管理员");
        }
        String token = jwtUtils.generateToken(user.getId(), user.getEmail(), user.getTokenVersion());
        LoginResponse resp = new LoginResponse(token, user.getId(), user.getDisplayName(), user.getEmail());
        resp.setMustChangePassword(user.getMustChangePassword() != null && user.getMustChangePassword());
        resp.setIsSystemAdmin(user.getIsSystemAdmin() != null && user.getIsSystemAdmin());
        return resp;
    }

    public String issueRefreshToken(Long userId) {
        // 刷新令牌必须携带用户当前令牌代际号（tv），否则改密/重置/禁用后的吊销将无从判定
        User user = userMapper.selectById(userId);
        return jwtUtils.generateRefreshToken(userId, user == null ? 0 : user.getTokenVersion());
    }

    public TokenPair refresh(String refreshToken) {
        if (!jwtUtils.validateRefreshToken(refreshToken)) {
            throw new BusinessException(401, "登录已过期，请重新登录");
        }
        Long userId = jwtUtils.getUserIdFromToken(refreshToken);
        User user = getUserById(userId);
        if (user == null) {
            throw new BusinessException(401, "登录已过期，请重新登录");
        }
        if (user.getEnabled() != null && !user.getEnabled()) {
            throw new BusinessException("账户已被禁用，请联系管理员");
        }
        // 令牌吊销校验：改密 / 管理员重置密码 / 禁用启用账户会使库中 token_version 自增，
        // 此前签发的 refresh token 因 tv 失配而在此被拒（旧令牌无 tv 声明按 0 处理，兼容存量登录）。
        int tokenTv = jwtUtils.getTokenVersionFromToken(refreshToken);
        int currentTv = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        if (tokenTv != currentTv) {
            throw new BusinessException(401, "登录凭据已失效，请重新登录");
        }
        String access = jwtUtils.generateToken(userId, user.getEmail(), user.getTokenVersion());
        String refresh = jwtUtils.generateRefreshToken(userId, user.getTokenVersion());
        return new TokenPair(access, refresh, userId, user.getDisplayName(), user.getEmail());
    }

    /**
     * 首次登录改密 / 主动改密。
     * 校验当前密码后写入新密码哈希，并清除 mustChangePassword 标记。
     */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (oldPassword == null || newPassword == null) {
            throw new BusinessException("密码不能为空");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException("当前密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        // 令牌代际号自增：改密后此前签发的 refresh token 在下次刷新时被拒（凭据吊销）
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        userMapper.updateById(user);
    }

    /**
     * 管理员重置用户密码：不再把明文临时密码写入响应体，改为签发一次性重置令牌（15 分钟有效），
     * 由管理员/用户凭令牌在 /reset-password 自助设密，杜绝明文凭据经 API 响应泄露。
     */
    public Map<String, String> resetPasswordForAdmin(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 重置后强制改密，并使旧会话（refresh token）立即失效
        user.setMustChangePassword(true);
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        userMapper.updateById(user);
        String token = jwtUtils.generatePasswordResetToken(user.getId());
        Map<String, String> result = new LinkedHashMap<>();
        result.put("resetToken", token);
        result.put("resetUrl", "/reset-password?token=" + token);
        return result;
    }

    /**
     * 凭一次性重置令牌设置新密码；令牌过期/无效/用途不符均按「重置链接无效或已过期」处理。
     */
    public void confirmPasswordReset(String token, String newPassword) {
        if (token == null || token.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new BusinessException("参数无效");
        }
        Long userId;
        try {
            userId = jwtUtils.parsePasswordResetToken(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException("重置链接无效或已过期");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        userMapper.updateById(user);
        clearFail(user.getEmail() == null ? "" : user.getEmail().toLowerCase());
    }

    public User getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user != null) {
            // 系统级管理员依据 users.is_system_admin（与团队内角色解耦）
            boolean admin = user.getIsSystemAdmin() != null && user.getIsSystemAdmin();
            user.setIsAdmin(admin);
        }
        return user;
    }

    /**
     * 更新当前登录用户个人资料（displayName / email / avatarUrl）。
     * 邮箱变更时需校验唯一性；密码不可通过此接口修改。
     */
    public User updateProfile(Long userId, UpdateProfileRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!req.getEmail().equals(user.getEmail())) {
            boolean exists = userMapper.exists(
                    new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail()));
            if (exists) {
                throw new BusinessException("邮箱已被使用");
            }
            user.setEmail(req.getEmail());
        }
        user.setDisplayName(req.getDisplayName());
        user.setAvatarUrl(req.getAvatarUrl());
        userMapper.updateById(user);
        return user;
    }

    /**
     * R-025: 忘记密码（自助重置令牌签发）。
     *
     * 安全说明（v6 修复 P1-1 延续）：该接口无需认证即可访问，绝不修改任何用户密码，
     * 仅在邮箱命中时签发一次性重置令牌（15 分钟有效，purp=pwreset），杜绝「凭已知邮箱
     * 把他人账户锁死」的未授权账户锁定（DoS）。
     *
     * 令牌经邮件下发；MVP 未接入 SMTP，暂将重置链接记入服务端日志，待邮件能力就绪后
     * 改为真实投递。无论邮箱是否存在都返回成功，不泄露邮箱是否注册。重置链接的使用与
     * 旧会话失效由 {@code confirmPasswordReset} 完成。
     */
    public void requestPasswordReset(String email) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            // 邮箱未注册：不生成令牌、不泄露存在性，统一返回成功（防邮箱枚举）
            return;
        }
        // 生成一次性重置令牌（15 分钟有效），经邮件下发（未配置 SMTP 时 MailService 降级为记日志）。
        String token = jwtUtils.generatePasswordResetToken(user.getId());
        String resetUrl = "/reset-password?token=" + token;
        mailService.sendPasswordResetEmail(user.getEmail(), resetUrl);
    }
}
