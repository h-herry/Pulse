package com.pulse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pulse.dto.AiParseRequest;
import com.pulse.dto.AiSummaryRequest;
import com.pulse.dto.AssignmentSuggestRequest;
import com.pulse.dto.AssignmentSuggestion;
import com.pulse.dto.LlmCredentials;
import com.pulse.dto.MemberWorkload;
import com.pulse.dto.ParsedTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.*;

/**
 * AI 网关：自然语言建任务解析 + 项目/迭代智能概述。
 * per-user 模式：调用凭证来自用户自带的 LLM API Key（UserLlmConfig），
 * 由 AiConfigService.resolveCredentials 注入；未配置或调用失败时静默回退到本地启发式，保证功能始终可用。
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    // ============================ 自然语言建任务 ============================

    public List<ParsedTask> parse(AiParseRequest req, LlmCredentials creds) {
        if (req == null || req.getText() == null || req.getText().isBlank()) {
            return Collections.emptyList();
        }
        if (creds != null) {
            try {
                List<ParsedTask> fromLlm = callLlmParse(req.getText(), creds);
                if (fromLlm != null && !fromLlm.isEmpty()) return fromLlm;
            } catch (Exception e) {
                log.warn("AI 解析调用失败，回退本地启发式：{}", e.getMessage());
            }
        }
        return heuristicParse(req.getText());
    }

    // ---------- 本地启发式解析（离线可用，零依赖） ----------

    private List<ParsedTask> heuristicParse(String text) {
        String[] lines = text.split("[；;。\r\n]+");
        List<ParsedTask> result = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            result.add(parseLine(line));
        }
        return result;
    }

    private ParsedTask parseLine(String line) {
        ParsedTask t = new ParsedTask();
        String remain = line;

        // 优先级
        Matcher pm = Pattern.compile("(?i)(?:优先级\\s*)?(P[0-3])\\b").matcher(remain);
        if (pm.find()) {
            t.setPriority(pm.group(1).toUpperCase());
            remain = removeRange(remain, pm);
        }

        // 负责人：指派给/交给/分配给 X
        Matcher am = Pattern.compile("(?:指派给|交给|分配给|由)\\s*([\\u4e00-\\u9fa5A-Za-z]{1,10}?)(?:\\s*(?:负责|处理|跟进))?\\b")
                .matcher(remain);
        if (am.find()) {
            t.setAssigneeHint(am.group(1).trim());
            remain = removeRange(remain, am);
        }

        // 截止日期
        LocalDate due = parseDate(remain);
        if (due != null) {
            t.setDueDate(due);
            remain = stripDateKeywords(remain);
        }

        // 标题：清理动作词与标点
        String title = remain.replaceAll("^(完成|做|开发|实现|修复|编写|设计|测试|上线|处理|整理|更新|优化)\\s*", "")
                .replaceAll("[,，。；;]+$", "").trim();
        if (title.isEmpty()) title = line;
        t.setTitle(title);
        return t;
    }

    private LocalDate parseDate(String s) {
        // 显式日期 yyyy-MM-dd / yyyy/M/d
        Matcher dm = Pattern.compile("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})").matcher(s);
        if (dm.find()) {
            try { return LocalDate.of(Integer.parseInt(dm.group(1)), Integer.parseInt(dm.group(2)), Integer.parseInt(dm.group(3))); }
            catch (Exception e) { /* ignore */ }
        }
        LocalDate today = LocalDate.now();
        if (s.contains("大后天")) return today.plusDays(3);
        if (s.contains("后天")) return today.plusDays(2);
        if (s.contains("明天")) return today.plusDays(1);
        if (s.contains("今天") || s.contains("当日")) return today;
        Matcher rm = Pattern.compile("(\\d+)\\s*天[后以]?").matcher(s);
        if (rm.find()) return today.plusDays(Long.parseLong(rm.group(1)));
        Matcher wm = Pattern.compile("(?:下?周)([一二三四五六日天])").matcher(s);
        if (wm.find()) {
            DayOfWeek dw = mapWeekday(wm.group(1));
            if (dw != null) return today.with(TemporalAdjusters.next(dw));
        }
        return null;
    }

    private String stripDateKeywords(String s) {
        return s.replaceAll("(?i)(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2})", " ")
                .replaceAll("(?:大后天|后天|明天|今天|当日|\\d+\\s*天[以后]?|下?周[一二三四五六日天])前?", " ");
    }

    private static DayOfWeek mapWeekday(String c) {
        return switch (c) {
            case "一" -> DayOfWeek.MONDAY;
            case "二" -> DayOfWeek.TUESDAY;
            case "三" -> DayOfWeek.WEDNESDAY;
            case "四" -> DayOfWeek.THURSDAY;
            case "五" -> DayOfWeek.FRIDAY;
            case "六" -> DayOfWeek.SATURDAY;
            case "日", "天" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    private static String removeRange(String src, Matcher m) {
        return src.substring(0, m.start()) + src.substring(m.end());
    }

    // ---------- 生产级 LLM 调用（OpenAI 兼容 /chat/completions） ----------

    private List<ParsedTask> callLlmParse(String text, LlmCredentials creds) throws Exception {
        String sys = "你是任务管理助手。把用户的自然语言拆成结构化任务清单。"
                + "仅返回一个 JSON 数组，每个元素字段：title(必填,string),assigneeHint(可选,string),"
                + "priority(可选,P0-P3),dueDate(可选,yyyy-MM-dd)。不要输出任何解释、代码块标记或多余文字。";
        String content = chat(creds, sys, "把下面这段话拆成任务清单：\n" + text);
        JsonNode arr = extractJsonArray(content);
        if (arr == null) return null;
        List<ParsedTask> list = new ArrayList<>();
        for (JsonNode n : arr) {
            ParsedTask p = new ParsedTask();
            String title = n.path("title").asText("").trim();
            if (title.isEmpty()) continue;
            p.setTitle(title);
            String ah = n.path("assigneeHint").asText("");
            if (!ah.isBlank()) p.setAssigneeHint(ah.trim());
            String pr = n.path("priority").asText("").toUpperCase();
            if (pr.matches("P[0-3]")) p.setPriority(pr);
            String d = n.path("dueDate").asText("");
            if (!d.isBlank()) {
                try { p.setDueDate(LocalDate.parse(d.trim())); } catch (Exception ignored) {}
            }
            list.add(p);
        }
        return list;
    }

    // ============================ 智能 概述 ============================

    public String summarize(AiSummaryRequest req, LlmCredentials creds) {
        if (req == null || req.getTasks() == null || req.getTasks().isEmpty()) {
            return "暂无任务数据可供分析。";
        }
        if (creds != null) {
            try {
                String s = callLlmSummary(req, creds);
                if (s != null && !s.isBlank()) return s.trim();
            } catch (Exception e) {
                log.warn("AI 概述调用失败，回退本地启发式：{}", e.getMessage());
            }
        }
        return heuristicSummary(req);
    }

    private String heuristicSummary(AiSummaryRequest req) {
        List<AiSummaryRequest.TaskBrief> all = req.getTasks();
        int total = all.size();
        long done = all.stream().filter(t -> "DONE".equalsIgnoreCase(t.getStatus())).count();
        long inProgress = all.stream().filter(t -> "IN_PROGRESS".equalsIgnoreCase(t.getStatus())
                || "REVIEW".equalsIgnoreCase(t.getStatus())).count();
        long todo = all.stream().filter(t -> "TODO".equalsIgnoreCase(t.getStatus())).count();
        LocalDate today = LocalDate.now();
        List<AiSummaryRequest.TaskBrief> overdue = all.stream()
                .filter(t -> t.getDueDate() != null && !"DONE".equalsIgnoreCase(t.getStatus())
                        && t.getDueDate().compareTo(today.toString()) < 0)
                .toList();
        List<AiSummaryRequest.TaskBrief> p0 = all.stream()
                .filter(t -> "P0".equalsIgnoreCase(t.getPriority())).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("本项目共 ").append(total).append(" 个任务：已完成 ").append(done)
                .append("、进行中 ").append(inProgress).append("、待办 ").append(todo).append("。");
        double pct = total == 0 ? 0 : Math.round(done * 1000.0 / total) / 10.0;
        sb.append("完成率约 ").append(pct).append("%。");
        if (!overdue.isEmpty()) {
            sb.append("有 ").append(overdue.size()).append(" 个任务已逾期，建议优先处理：");
            for (int i = 0; i < Math.min(3, overdue.size()); i++) {
                AiSummaryRequest.TaskBrief t = overdue.get(i);
                sb.append("「").append(t.getTitle()).append("」");
                if (t.getAssigneeName() != null) sb.append("(").append(t.getAssigneeName()).append(")");
                if (i < Math.min(3, overdue.size()) - 1) sb.append("、");
            }
            sb.append("。");
        } else {
            sb.append("当前没有逾期任务，节奏良好。");
        }
        if (!p0.isEmpty()) {
            sb.append("另有 ").append(p0.size()).append(" 个 P0 紧急任务需关注。");
        }
        return sb.toString();
    }

    private String callLlmSummary(AiSummaryRequest req, LlmCredentials creds) throws Exception {
        // 把任务清单压缩成紧凑文本喂给模型
        StringBuilder rows = new StringBuilder();
        for (AiSummaryRequest.TaskBrief t : req.getTasks()) {
            rows.append("- ").append(nullSafe(t.getTitle()));
            if (t.getStatus() != null) rows.append(" | 状态:").append(t.getStatus());
            if (t.getPriority() != null) rows.append(" | 优先级:").append(t.getPriority());
            if (t.getDueDate() != null) rows.append(" | 截止:").append(t.getDueDate());
            if (t.getAssigneeName() != null) rows.append(" | 负责人:").append(t.getAssigneeName());
            rows.append("\n");
        }
        String ctx = req.getContext() != null ? req.getContext() : "";
        String sys = "你是资深项目管理助手。基于任务清单给出简明中文概述（150字以内）："
                + "整体进度、风险（逾期/P0）、建议下一步动作。只输出概述文本，不要列表或多余格式。";
        String user = (ctx.isBlank() ? "" : "上下文：" + ctx + "\n") + "任务清单：\n" + rows;
        return chat(creds, sys, user);
    }

    // ============================ 迭代复盘增强 ============================

    /**
     * 基于迭代统计与已有模板复盘，生成更具洞察的复盘草稿（Markdown）。
     * 无凭证时返回 null，由调用方回退到模板复盘；调用失败同样回退（不抛未捕获异常）。
     */
    public String generateRetroDraft(String statsText, String existingRetro, LlmCredentials creds) {
        if (creds == null || statsText == null) return null;
        try {
            String sys = "你是资深敏捷教练。基于下方的迭代统计数据和已有的模板复盘，"
                    + "撰写一份更具洞察力的迭代复盘（Markdown）。结构严格为："
                    + "## 一、亮点（做得好的地方） / ## 二、问题与根因 / ## 三、改进行动项（每条需具体、可指派、可验证）。"
                    + "只输出 Markdown，不要代码块包裹，不要任何解释性文字。";
            String user = "【迭代统计】\n" + statsText + "\n\n"
                    + (existingRetro != null && !existingRetro.isBlank()
                        ? "【已有模板复盘】\n" + existingRetro
                        : "（暂无已有复盘，请直接基于统计生成）");
            return chat(creds, sys, user).trim();
        } catch (Exception e) {
            log.warn("AI 复盘增强生成失败，回退模板：{}", e.getMessage());
            return null;
        }
    }

    // ============================ 周期 / 周报摘要 ============================

    /**
     * 基于迭代统计生成一段简明周期（周报）摘要（中文，纯文本）。无凭证时返回 null。
     */
    public String generateReportSummary(String statsText, LlmCredentials creds) {
        if (creds == null || statsText == null) return null;
        try {
            String sys = "你是项目管理助手。基于下方统计数据，生成一段 120 字以内的中文周期（周报）摘要："
                    + "整体进度、关键风险、下一步建议。只输出纯文本，不要列表或 Markdown 标记。";
            return chat(creds, sys, statsText).trim();
        } catch (Exception e) {
            log.warn("AI 周报摘要生成失败：{}", e.getMessage());
            return null;
        }
    }

    // ============================ 智能分派建议 ============================

    /**
     * 基于「待分配任务」与「各成员当前负载」给出分派建议（1–3 名）。
     * 仅采纳与已知成员 displayName 精确匹配的建议，并回填 memberUserId，
     * 由调用方据此写库，避免依赖前端姓名匹配、也避免误建议非团队成员。
     * 无凭证 / 无成员 / 调用失败 / 格式异常 均返回 null，由调用方回退手动分派。
     */
    public List<AssignmentSuggestion> suggestAssignees(AssignmentSuggestRequest req,
                                                       List<MemberWorkload> members,
                                                       LlmCredentials creds) {
        if (creds == null || members == null || members.isEmpty()) return null;
        try {
            Map<String, Long> nameToId = members.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            MemberWorkload::getDisplayName, MemberWorkload::getUserId, (a, b) -> a));

            StringBuilder sb = new StringBuilder();
            for (MemberWorkload m : members) {
                sb.append("- ").append(nullSafe(m.getDisplayName()))
                        .append(" | 进行中:").append(m.getInProgressTasks())
                        .append(" | 未完成:").append(m.getOpenTasks())
                        .append(" | 未完成故事点:").append(m.getOpenStoryPoints())
                        .append(" | 逾期:").append(m.getOverdueTasks()).append("\n");
            }
            String taskDesc = "标题:" + nullSafe(req.getTitle())
                    + (req.getDescription() != null ? " | 描述:" + req.getDescription() : "")
                    + (req.getPriority() != null ? " | 优先级:" + req.getPriority() : "")
                    + (req.getStoryPoints() != null ? " | 预估故事点:" + req.getStoryPoints() : "");

            String sys = "你是资深技术负责人。根据下方待分配任务与各成员当前负载，"
                    + "推荐最合适的人选（1-3 名），兼顾能力匹配与负载均衡。"
                    + "仅返回一个 JSON 数组，每个元素字段："
                    + "memberName(必填,string,须与给定成员名完全一致),"
                    + "reason(必填,string,简短理由),confidence(必填,high|medium|low)。"
                    + "不要输出任何解释、代码块标记或多余文字。";
            String user = "【待分配任务】\n" + taskDesc + "\n\n【成员负载】\n" + sb;
            String content = chat(creds, sys, user);
            JsonNode arr = extractJsonArray(content);
            if (arr == null) return null;

            List<AssignmentSuggestion> list = new ArrayList<>();
            for (JsonNode n : arr) {
                String name = n.path("memberName").asText("").trim();
                if (name.isEmpty()) continue;
                Long uid = nameToId.get(name);
                if (uid == null) continue; // 仅采纳与真实成员精确匹配的建议
                AssignmentSuggestion s = new AssignmentSuggestion();
                s.setMemberName(name);
                s.setMemberUserId(uid);
                s.setReason(n.path("reason").asText("").trim());
                String conf = n.path("confidence").asText("").toLowerCase();
                s.setConfidence(conf.matches("high|medium|low") ? conf : "medium");
                list.add(s);
            }
            return list.isEmpty() ? null : list;
        } catch (Exception e) {
            log.warn("AI 智能分派建议生成失败，回退手动分派：{}", e.getMessage());
            return null;
        }
    }

    // ---------- 通用 LLM 对话（健壮 JSON 构建 + 超时 + 响应解析） ----------

    private String chat(LlmCredentials creds, String system, String user) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", creds.getModel());
        body.put("temperature", 0.2);
        body.put("max_tokens", 1200);
        ArrayNode messages = body.putArray("messages");
        ObjectNode m1 = messages.addObject();
        m1.put("role", "system");
        m1.put("content", system);
        ObjectNode m2 = messages.addObject();
        m2.put("role", "user");
        m2.put("content", user);
        // 不强制 response_format：概述需纯文本、解析靠 extractJsonArray 兜底，
        // 且部分 OpenAI 兼容服务(DeepSeek/Moonshot/GLM)对 json_object 支持不一，强制可能 400。

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(creds.getBaseUrl().replaceAll("/$", "") + "/chat/completions"))
                .timeout(java.time.Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + creds.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("LLM HTTP " + resp.statusCode());
        }
        JsonNode root = MAPPER.readTree(resp.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            // 兼容部分网关把结果放在别处
            content = root.path("content").asText("");
        }
        return content;
    }

    /** 从可能夹杂代码块/解释的文本中稳健提取首个 JSON 数组。 */
    private JsonNode extractJsonArray(String content) {
        if (content == null || content.isBlank()) return null;
        String c = content.trim();
        // 去掉 ```json ... ``` 包裹
        Matcher fence = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE).matcher(c);
        if (fence.find()) c = fence.group(1).trim();
        try {
            JsonNode node = MAPPER.readTree(c);
            if (node.isArray()) return node;
            // 模型可能包成 {"tasks":[...]}
            if (node.isObject() && node.has("tasks") && node.get("tasks").isArray()) return node.get("tasks");
        } catch (Exception ignored) { /* 继续正则兜底 */ }
        Matcher am = Pattern.compile("\\[[\\s\\S]*\\]").matcher(c);
        if (am.find()) {
            try { return MAPPER.readTree(am.group()); } catch (Exception ignored) { /* */ }
        }
        return null;
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
}
