package com.pulse.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
/**
 * AI 解析出的单条任务（解析结果中间结构）。
 *
 * <p>含标题、指派提示（{@code assigneeHint}）、优先级、起止与到期日期、备注等，
 * 由 AI 从自然语言中提取，供前端确认或落库。
 */
public class ParsedTask {
    private String title;
    private String assigneeHint;
    private String priority;
    private LocalDate startDate;
    private LocalDate dueDate;
    private String notes;
}
