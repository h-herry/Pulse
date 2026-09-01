-- Pulse 脉搏 - 测试库清表脚本（仅测试 profile 使用）
-- 因 schema.sql 使用 CREATE TABLE IF NOT EXISTS，线上库表结构变更不会回灌到已存在的表，
-- 测试库需每次先清空再按最新 schema 重建，避免字段/约束与当前实体不一致。
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS task_tags;
DROP TABLE IF EXISTS task_activity_log;
DROP TABLE IF EXISTS burn_down_snapshots;
DROP TABLE IF EXISTS tasks;
DROP TABLE IF EXISTS tags;
DROP TABLE IF EXISTS board_columns;
DROP TABLE IF EXISTS iterations;
DROP TABLE IF EXISTS team_members;
DROP TABLE IF EXISTS projects;
DROP TABLE IF EXISTS teams;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS saved_views;
DROP TABLE IF EXISTS task_dependencies;
DROP TABLE IF EXISTS task_attachments;
DROP TABLE IF EXISTS custom_fields;
DROP TABLE IF EXISTS custom_field_values;

SET FOREIGN_KEY_CHECKS = 1;
