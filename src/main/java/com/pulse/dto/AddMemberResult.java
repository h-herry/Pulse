package com.pulse.dto;

import lombok.Data;

@Data
/**
 * 添加成员操作的结果。
 *
 * <p>{@code member} 为加入后的成员视图；当系统为新邮箱自动创建账号时，
 * 不再返回明文初始密码，而是返回一次性重置令牌 {@code resetToken} 与 {@code resetUrl}，
 * 由队长转发链接，新成员凭链接自助设密（避免明文凭据经 API 响应体泄露）。
 * 已注册用户直接加入团队时二者为 {@code null}。
 */
public class AddMemberResult {
    private MemberVO member;
    // 仅当系统自动为新邮箱创建账号时返回；已注册用户加入时为 null
    private String resetToken;
    private String resetUrl;
}
