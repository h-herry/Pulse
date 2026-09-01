package com.pulse.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 基于 Spring {@code JavaMailSender} 的 SMTP 邮件实现。
 *
 * <p>安全/健壮性约定：
 * <ul>
 *   <li>发信失败（SMTP 不可用、鉴权失败等）只记日志、不抛出异常，
 *       避免阻断 {@code forgot-password} 的 200 响应与后续流程；</li>
 *   <li>{@code app.mail.enabled=false}（默认）或未配置 SMTP 时降级为记日志，便于本地联调；</li>
 *   <li>重置链接自动补全为绝对地址（基于 {@code app.mail.frontend-base-url}），否则邮件中无法点击。</li>
 * </ul>
 */
@Service
public class SmtpMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailService.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;
    private final String frontendBaseUrl;

    /**
     * {@code JavaMailSender} 经 {@link ObjectProvider} 可选注入：当未配置 {@code spring.mail.host}（默认）时，
     * Spring 不会创建该 bean，此时取 {@code null}，服务降级为记日志而不影响应用启动
     * （避免 MailSenderAutoConfiguration 的 host 条件导致上下文加载失败，连带所有测试/生产启动报错）。
     */
    public SmtpMailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                           @Value("${app.mail.enabled:false}") boolean enabled,
                           @Value("${app.mail.from:no-reply@pulse.local}") String from,
                           @Value("${app.mail.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.enabled = enabled;
        this.from = from;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetUrl) {
        String absoluteUrl = toAbsolute(resetUrl);
        if (!enabled || mailSender == null) {
            // 未启用 SMTP 或 JavaMailSender 不可用（未配置 host）时仅记日志，便于联调；不阻断主流程。
            log.warn("邮件能力未启用，密码重置链接仅记日志（非真实投递） to={} url={}", toEmail, absoluteUrl);
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject("Pulse 密码重置");
            helper.setText(buildBody(absoluteUrl), true);
            mailSender.send(msg);
            log.info("密码重置邮件已发送 to={}", toEmail);
        } catch (Exception e) {
            // 发信失败不得影响 forgot-password 返回 200；记日志供运维感知。
            log.error("密码重置邮件发送失败 to={}：{}", toEmail, e.getMessage(), e);
        }
    }

    private String toAbsolute(String resetUrl) {
        if (resetUrl == null) {
            return frontendBaseUrl;
        }
        if (resetUrl.startsWith("http://") || resetUrl.startsWith("https://")) {
            return resetUrl;
        }
        String base = frontendBaseUrl.endsWith("/") ? frontendBaseUrl : frontendBaseUrl + "/";
        String path = resetUrl.startsWith("/") ? resetUrl.substring(1) : resetUrl;
        return base + path;
    }

    private String buildBody(String url) {
        return "<p>我们收到了你的密码重置请求。请点击下面的链接在 15 分钟内设置新密码：</p>"
                + "<p><a href=\"" + url + "\">" + url + "</a></p>"
                + "<p>若非本人操作，请忽略此邮件，你的账户不会受到影响。</p>";
    }
}
