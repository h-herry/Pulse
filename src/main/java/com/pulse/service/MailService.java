package com.pulse.service;

/**
 * 邮件发送接缝（事务邮件，如密码重置链接）。
 *
 * <p>实现类在 {@code app.mail.enabled=true} 且已配置 SMTP 时真实发信；
 * 未启用或未配置时降级为记日志，保证 MVP/本地开发无需 SMTP 即可运行，且不阻断主流程。
 */
public interface MailService {

    /**
     * 发送密码重置邮件。
     *
     * @param toEmail  收件人邮箱
     * @param resetUrl 重置链接（相对或绝对均可，实现类负责补全为可点击的绝对地址）
     */
    void sendPasswordResetEmail(String toEmail, String resetUrl);
}
