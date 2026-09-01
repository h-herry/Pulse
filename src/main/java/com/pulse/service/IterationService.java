package com.pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pulse.common.BusinessException;
import com.pulse.common.TenantContext;
import com.pulse.dto.IterationRequest;
import com.pulse.entity.Iteration;
import com.pulse.entity.Project;
import com.pulse.mapper.IterationMapper;
import com.pulse.mapper.ProjectMapper;
import com.pulse.service.ReportService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 迭代（Iteration）服务（R-024）。
 * 负责迭代的增删改查与状态流转；迭代关闭且无复盘时自动生成模板化复盘（Markdown）。
 * 安全：所有操作基于 TenantContext 的 teamId 校验团队归属；
 * 复盘统计由 ReportService 提供，并支持 buildStatsText 供 AI 复盘增强 / 周报提示词使用。
 */
@Service
public class IterationService {

    private final IterationMapper iterationMapper;
    private final ProjectMapper projectMapper;
    private final ReportService reportService;
    private final AuthorizationService authorizationService;

    public IterationService(IterationMapper iterationMapper, ProjectMapper projectMapper,
                            ReportService reportService, AuthorizationService authorizationService) {
        this.iterationMapper = iterationMapper;
        this.projectMapper = projectMapper;
        this.reportService = reportService;
        this.authorizationService = authorizationService;
    }

    /**
     * 查询项目的全部迭代（按创建时间倒序），按当前团队隔离。
     */
    public List<Iteration> listIterations(Long projectId) {
        Long teamId = TenantContext.getTenantId();
        return iterationMapper.selectList(
                new LambdaQueryWrapper<Iteration>()
                        .eq(Iteration::getProjectId, projectId)
                        .eq(Iteration::getTeamId, teamId)
                        .orderByDesc(Iteration::getCreatedAt));
    }

    /**
     * 在项目下创建迭代，校验项目属于当前团队；默认状态 PLANNED。
     */
    public Iteration createIteration(Long projectId, IterationRequest req) {
        Long teamId = TenantContext.getTenantId();
        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getTeamId().equals(teamId)) {
            throw new BusinessException("无权访问该项目");
        }
        Iteration iter = new Iteration();
        iter.setProjectId(projectId);
        iter.setTeamId(teamId);
        iter.setName(req.getName());
        iter.setStartDate(req.getStartDate());
        iter.setEndDate(req.getEndDate());
        iter.setStatus(req.getStatus() != null ? req.getStatus() : "PLANNED");
        iterationMapper.insert(iter);
        return iter;
    }

    /**
     * 更新迭代信息；当状态置为 CLOSED 且尚无复盘时自动生成模板复盘。
     * 校验迭代属于当前团队。
     */
    public Iteration updateIteration(Long id, IterationRequest req) {
        Iteration iter = iterationMapper.selectById(id);
        if (iter == null || !iter.getTeamId().equals(TenantContext.getTenantId())) {
            throw new BusinessException("迭代不存在");
        }
        String oldStatus = iter.getStatus();
        iter.setName(req.getName());
        iter.setStartDate(req.getStartDate());
        iter.setEndDate(req.getEndDate());
        if (req.getStatus() != null) iter.setStatus(req.getStatus());
        iterationMapper.updateById(iter);

        // 迭代置为「已关闭」且尚无复盘时，自动生成复盘
        if ("CLOSED".equals(iter.getStatus()) && !"CLOSED".equals(oldStatus)
                && (iter.getRetroContent() == null || iter.getRetroContent().isBlank())) {
            return generateRetro(id);
        }
        return iter;
    }

    /**
     * 删除迭代（按 ID 删除），校验迭代属于当前团队。
     */
    public void deleteIteration(Long id) {
        Iteration iter = iterationMapper.selectById(id);
        if (iter == null || !iter.getTeamId().equals(TenantContext.getTenantId())) {
            throw new BusinessException("迭代不存在");
        }
        authorizationService.requireTeamLead(iter.getTeamId());
        iterationMapper.deleteById(id);
    }

    /**
     * 按 ID 获取迭代（校验团队归属），供其他写操作复用鉴权。
     */
    public Iteration getById(Long id) {
        Iteration iter = iterationMapper.selectById(id);
        if (iter == null || !iter.getTeamId().equals(TenantContext.getTenantId())) {
            throw new BusinessException("迭代不存在");
        }
        return iter;
    }

    /**
     * 模板化生成迭代复盘（Markdown）。不依赖外部 AI，完全基于统计数据生成，
     * 包含概览、速度趋势、状态分布、风险与改进建议。
     */
    public Iteration generateRetro(Long id) {
        Iteration iter = iterationMapper.selectById(id);
        if (iter == null || !iter.getTeamId().equals(TenantContext.getTenantId())) {
            throw new BusinessException("迭代不存在");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> s = reportService.getIterationStats(id);

        long total = toLong(s.get("total"));
        long done = toLong(s.get("done"));
        long todo = toLong(s.get("todo"));
        long inProgress = toLong(s.get("inProgress"));
        long review = toLong(s.get("review"));
        long overdue = toLong(s.get("overdueCount"));
        long progress = toLong(s.get("progress"));
        long overdueRate = toLong(s.get("overdueRate"));
        long spTotal = toLong(s.get("storyPointsTotal"));
        long spDone = toLong(s.get("storyPointsDone"));
        long spProgress = toLong(s.get("storyPointsProgress"));
        long velocity = toLong(s.get("velocity"));
        long prevVelocity = toLong(s.get("previousVelocity"));

        StringBuilder md = new StringBuilder();
        md.append("# 迭代复盘：").append(s.get("iterationName")).append("\n\n");
        md.append("**周期**：").append(orNull(s.get("startDate"))).append(" ~ ").append(orNull(s.get("endDate"))).append("  \n");
        md.append("**状态**：").append(statusZh(String.valueOf(s.get("status")))).append("\n\n");

        md.append("## 一、概览\n");
        md.append("- 任务总数：**").append(total).append("**\n");
        md.append("- 已完成：**").append(done).append("**（完成率 **").append(progress).append("%**）\n");
        md.append("- 逾期：**").append(overdue).append("**（逾期率 ").append(overdueRate).append("%）\n");
        md.append("- 故事点：**").append(spDone).append("/").append(spTotal).append("**（").append(spProgress).append("%）\n\n");

        md.append("## 二、迭代速度\n");
        md.append("- 本迭代完成故事点：**").append(velocity).append("**\n");
        md.append("- 上一迭代完成故事点：").append(prevVelocity).append("\n");
        String trend = velocity > prevVelocity ? "↑ 提速" : (velocity < prevVelocity ? "↓ 减速" : "→ 持平");
        md.append("- 速度趋势：").append(trend).append("\n\n");

        md.append("## 三、状态分布\n");
        md.append("- 待办 TODO：").append(todo).append("\n");
        md.append("- 进行中 IN_PROGRESS：").append(inProgress).append("\n");
        md.append("- 评审 REVIEW：").append(review).append("\n");
        md.append("- 已完成 DONE：").append(done).append("\n\n");

        md.append("## 四、风险与逾期\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> overdueTasks = (List<Map<String, Object>>) s.get("overdueTasks");
        if (overdueTasks != null && !overdueTasks.isEmpty()) {
            for (Map<String, Object> t : overdueTasks) {
                md.append("- [逾期] ").append(t.get("title"))
                        .append("（截止 ").append(t.get("dueDate")).append("，负责人 ").append(t.get("assigneeName")).append("）\n");
            }
        } else {
            md.append("- 本迭代无逾期任务 🎉\n");
        }
        md.append("\n");

        md.append("## 五、改进建议\n");
        List<String> suggestions = new ArrayList<>();
        if (overdueRate >= 30) {
            suggestions.add("逾期率偏高（≥30%），建议复盘排期合理性，将大任务进一步拆解为 1–2 天可交付的小颗粒度。");
        }
        if (velocity < prevVelocity) {
            suggestions.add("本迭代速度低于上一迭代，建议识别协作瓶颈（如评审排队、依赖阻塞）并在下个迭代前清除。");
        }
        if ("CLOSED".equals(iter.getStatus()) && done < total) {
            suggestions.add("迭代已关闭但仍有 " + (total - done) + " 个任务未完成，建议明确遗留项归属（顺延或移出迭代）。");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("整体健康度良好，保持当前节奏；可考虑适度提升本迭代承诺的故事点以挑战产能上限。");
        }
        for (int i = 0; i < suggestions.size(); i++) {
            md.append((i + 1)).append(". ").append(suggestions.get(i)).append("\n");
        }

        iter.setRetroContent(md.toString());
        iterationMapper.updateById(iter);
        return iter;
    }

    /**
     * 把迭代统计转成可读文本，供 AI 复盘增强 / 周报摘要的提示词使用。
     * 仅含业务指标，不含任何密钥或敏感明文。
     */
    public String buildStatsText(Long id) {
        Iteration iter = iterationMapper.selectById(id);
        if (iter == null || !iter.getTeamId().equals(TenantContext.getTenantId())) {
            throw new BusinessException("迭代不存在");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> s = reportService.getIterationStats(id);
        StringBuilder sb = new StringBuilder();
        sb.append("迭代名称：").append(orNull(s.get("iterationName"))).append("\n");
        sb.append("周期：").append(orNull(s.get("startDate"))).append(" ~ ").append(orNull(s.get("endDate"))).append("\n");
        sb.append("状态：").append(statusZh(String.valueOf(s.get("status")))).append("\n");
        sb.append("任务总数：").append(toLong(s.get("total"))).append("\n");
        sb.append("已完成：").append(toLong(s.get("done"))).append("（完成率 ").append(toLong(s.get("progress"))).append("%）\n");
        sb.append("逾期：").append(toLong(s.get("overdueCount"))).append("（逾期率 ").append(toLong(s.get("overdueRate"))).append("%）\n");
        sb.append("故事点：").append(toLong(s.get("storyPointsDone"))).append("/").append(toLong(s.get("storyPointsTotal")))
                .append("（").append(toLong(s.get("storyPointsProgress"))).append("%）\n");
        sb.append("本迭代速度：").append(toLong(s.get("velocity"))).append(" 故事点\n");
        sb.append("上一迭代速度：").append(toLong(s.get("previousVelocity"))).append(" 故事点\n");
        Object sc = s.get("statusCounts");
        if (sc instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> counts = (Map<String, Object>) sc;
            sb.append("状态分布：TODO=").append(orNull(counts.get("TODO")))
                    .append(" IN_PROGRESS=").append(orNull(counts.get("IN_PROGRESS")))
                    .append(" REVIEW=").append(orNull(counts.get("REVIEW")))
                    .append(" DONE=").append(orNull(counts.get("DONE"))).append("\n");
        }
        Object ot = s.get("overdueTasks");
        if (ot instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) ot;
            int limit = Math.min(list.size(), 10);
            sb.append("逾期任务（前 ").append(limit).append(" 条）：\n");
            for (int i = 0; i < limit; i++) {
                Map<String, Object> t = list.get(i);
                sb.append("- ").append(orNull(t.get("title")))
                        .append("（截止 ").append(orNull(t.get("dueDate")))
                        .append("，负责人 ").append(orNull(t.get("assigneeName"))).append("）\n");
            }
        }
        return sb.toString();
    }

    /** 保存复盘内容：用于 AI 草稿确认落库，也支持手动编辑。 */
    public Iteration saveRetroContent(Long id, String content) {
        Iteration iter = getById(id);
        iter.setRetroContent(content != null ? content : "");
        iterationMapper.updateById(iter);
        return iter;
    }

    private static long toLong(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }

    private static String orNull(Object v) {
        return v == null ? "—" : String.valueOf(v);
    }

    private static String statusZh(String s) {
        return switch (s) {
            case "PLANNED" -> "规划中";
            case "ACTIVE" -> "进行中";
            case "CLOSED" -> "已关闭";
            default -> s;
        };
    }
}
