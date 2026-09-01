package com.pulse.service;

import com.pulse.entity.Project;
import com.pulse.mapper.ProjectMapper;
import com.pulse.mapper.TaskMapper;
import com.pulse.mapper.TaskSequenceMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动引导：为所有已存在项目补齐任务号序列行。
 * 这样 createTask 的热路径只需对已有行做 FOR UPDATE（不会触发并发 INSERT 死锁）。
 * 运行期新建的项目在 ProjectService.createProject 中即时写入序列行。
 */
@Component
public class TaskSequenceBootstrap implements CommandLineRunner {

    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final TaskSequenceMapper taskSequenceMapper;

    public TaskSequenceBootstrap(ProjectMapper projectMapper, TaskMapper taskMapper,
                                 TaskSequenceMapper taskSequenceMapper) {
        this.projectMapper = projectMapper;
        this.taskMapper = taskMapper;
        this.taskSequenceMapper = taskSequenceMapper;
    }

    @Override
    public void run(String... args) {
        List<Project> projects = projectMapper.selectList(null);
        for (Project p : projects) {
            Integer max = taskMapper.selectMaxTaskNumber(p.getId());
            int next = (max == null ? 0 : max) + 1;
            try {
                taskSequenceMapper.insertSeq(p.getId(), next);
            } catch (DuplicateKeyException e) {
                // 序列行已存在，忽略
            }
        }
    }
}
