package com.pulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 标签创建/更新请求。
 *
 * <p>{@code name} 标签名，{@code color} 展示颜色（如 #RRGGBB）。标签自身归属由当前团队上下文决定。
 */
public class TagRequest {
    @NotBlank @Size(max = 50)
    private String name;
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "颜色格式错误")
    private String color;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
