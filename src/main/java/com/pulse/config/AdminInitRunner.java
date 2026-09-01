package com.pulse.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pulse.common.PasswordUtils;
import com.pulse.entity.User;
import com.pulse.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动初始化系统管理员。
 *
 * <p>原先管理员凭据（admin@pulse.dev / pulse123）以明文弱口令固化在 data.sql，
 * 且随仓库公开，等于把管理员权限直接送出。改为：
 * <ul>
 *   <li>空库（无系统管理员）时生成随机口令，mustChangePassword=true 强制改密；</li>
 *   <li>口令<b>不</b>明文写入日志（日志常被集中采集留存，属高危泄露面），默认仅在显式开启
 *       {@code pulse.print-init-password=true} 时打印；生产请通过受控渠道（Vault/KMS/当面交付）获取；</li>
 *   <li>已存在系统管理员则跳过，平滑升级不重复创建。</li>
 * </ul>
 */
@Component
public class AdminInitRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitRunner.class);
    private static final String ADMIN_EMAIL = "admin@pulse.dev";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final boolean printInitPassword;

    public AdminInitRunner(UserMapper userMapper, PasswordEncoder passwordEncoder,
                           @Value("${pulse.print-init-password:false}") boolean printInitPassword) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.printInitPassword = printInitPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long adminCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getIsSystemAdmin, true));
        if (adminCount != null && adminCount > 0) {
            return; // 已有系统管理员，跳过
        }

        String rawPassword = PasswordUtils.generateTempPassword(16);
        User admin = new User();
        admin.setEmail(ADMIN_EMAIL);
        admin.setDisplayName("管理员");
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setIsSystemAdmin(true);
        admin.setMustChangePassword(true);
        admin.setEnabled(true);
        userMapper.insert(admin);

        log.warn("============================================================");
        log.warn("初始系统管理员已创建：{}", ADMIN_EMAIL);
        if (printInitPassword) {
            log.warn("初始口令（仅当 pulse.print-init-password=true 时打印）：{}", rawPassword);
        } else {
            log.warn("初始口令已随机生成且未打印到日志；请通过受控渠道获取，并在首次登录后立即修改！");
        }
        log.warn("请立即登录并在「设置 → 安全」中修改口令！");
        log.warn("============================================================");
    }
}
