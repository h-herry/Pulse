package com.pulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 迭代创建/更新请求。
 *
 * <p>{@code name} 迭代名，{@code startDate}/{@code endDate} 起止日期，
 * {@code status} 迭代状态（如 PLANNED / ACTIVE / CLOSED）。
 */
public class IterationRequest {
    @NotBlank @Size(max = 100)
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    @Size(max = 20)
    private String status;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
