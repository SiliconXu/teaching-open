SET NAMES utf8mb4;

SET @teaching_additional_work_assignment_mode_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'teaching_additional_work'
      AND COLUMN_NAME = 'assignment_mode'
);
SET @teaching_additional_work_assignment_mode_sql = IF(
    @teaching_additional_work_assignment_mode_exists = 0,
    'ALTER TABLE teaching_additional_work ADD COLUMN assignment_mode varchar(32) DEFAULT ''file'' COMMENT ''assignment mode file/objective'' AFTER code_type',
    'SELECT 1'
);
PREPARE teaching_additional_work_assignment_mode_stmt FROM @teaching_additional_work_assignment_mode_sql;
EXECUTE teaching_additional_work_assignment_mode_stmt;
DEALLOCATE PREPARE teaching_additional_work_assignment_mode_stmt;

SET @teaching_course_unit_assignment_mode_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'teaching_course_unit'
      AND COLUMN_NAME = 'assignment_mode'
);
SET @teaching_course_unit_assignment_mode_sql = IF(
    @teaching_course_unit_assignment_mode_exists = 0,
    'ALTER TABLE teaching_course_unit ADD COLUMN assignment_mode varchar(32) DEFAULT ''file'' COMMENT ''assignment mode file/objective'' AFTER course_work_type',
    'SELECT 1'
);
PREPARE teaching_course_unit_assignment_mode_stmt FROM @teaching_course_unit_assignment_mode_sql;
EXECUTE teaching_course_unit_assignment_mode_stmt;
DEALLOCATE PREPARE teaching_course_unit_assignment_mode_stmt;

CREATE TABLE IF NOT EXISTS teaching_objective_homework (
    id varchar(40) NOT NULL,
    create_by varchar(50) DEFAULT NULL,
    create_time datetime DEFAULT NULL,
    update_by varchar(50) DEFAULT NULL,
    update_time datetime DEFAULT NULL,
    sys_org_code varchar(64) DEFAULT NULL,
    source_type varchar(32) NOT NULL,
    source_id varchar(40) NOT NULL,
    allow_redo tinyint(1) DEFAULT 1,
    show_result_after_submit tinyint(1) DEFAULT 1,
    question_count int DEFAULT 0,
    total_score int DEFAULT 0,
    source_markdown longtext,
    PRIMARY KEY (id),
    UNIQUE KEY uk_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @teaching_objective_homework_source_markdown_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'teaching_objective_homework'
      AND COLUMN_NAME = 'source_markdown'
);
SET @teaching_objective_homework_source_markdown_sql = IF(
    @teaching_objective_homework_source_markdown_exists = 0,
    'ALTER TABLE teaching_objective_homework ADD COLUMN source_markdown longtext COMMENT ''raw markdown source'' AFTER total_score',
    'SELECT 1'
);
PREPARE teaching_objective_homework_source_markdown_stmt FROM @teaching_objective_homework_source_markdown_sql;
EXECUTE teaching_objective_homework_source_markdown_stmt;
DEALLOCATE PREPARE teaching_objective_homework_source_markdown_stmt;

SET @teaching_objective_homework_redo_limit_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'teaching_objective_homework'
      AND COLUMN_NAME = 'redo_limit'
);
SET @teaching_objective_homework_redo_limit_sql = IF(
    @teaching_objective_homework_redo_limit_exists = 0,
    'ALTER TABLE teaching_objective_homework ADD COLUMN redo_limit int DEFAULT 1 COMMENT ''redo limit'' AFTER allow_redo',
    'SELECT 1'
);
PREPARE teaching_objective_homework_redo_limit_stmt FROM @teaching_objective_homework_redo_limit_sql;
EXECUTE teaching_objective_homework_redo_limit_stmt;
DEALLOCATE PREPARE teaching_objective_homework_redo_limit_stmt;

UPDATE teaching_objective_homework
SET redo_limit = CASE WHEN allow_redo = 1 THEN 1 ELSE 0 END
WHERE redo_limit IS NULL;

CREATE TABLE IF NOT EXISTS teaching_objective_question (
    id varchar(40) NOT NULL,
    homework_id varchar(40) NOT NULL,
    question_no int DEFAULT NULL,
    question_type varchar(32) NOT NULL,
    stem_text text,
    stem_images text,
    analysis_text text,
    analysis_images text,
    correct_answer varchar(32) NOT NULL,
    score int DEFAULT 0,
    sort_order int DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_homework (homework_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS teaching_objective_question_option (
    id varchar(40) NOT NULL,
    question_id varchar(40) NOT NULL,
    option_key varchar(16) NOT NULL,
    option_text text,
    option_image varchar(500) DEFAULT NULL,
    sort_order int DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS teaching_objective_submit (
    id varchar(40) NOT NULL,
    create_by varchar(50) DEFAULT NULL,
    create_time datetime DEFAULT NULL,
    update_by varchar(50) DEFAULT NULL,
    update_time datetime DEFAULT NULL,
    sys_org_code varchar(64) DEFAULT NULL,
    homework_id varchar(40) NOT NULL,
    source_type varchar(32) NOT NULL,
    source_id varchar(40) NOT NULL,
    student_id varchar(40) NOT NULL,
    depart_id varchar(40) DEFAULT NULL,
    submit_status varchar(32) DEFAULT 'submitted',
    objective_score int DEFAULT 0,
    right_count int DEFAULT 0,
    question_count int DEFAULT 0,
    attempt_no int DEFAULT 1,
    submitted_at datetime DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_submit_homework_student (homework_id, student_id, depart_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS teaching_objective_submit_item (
    id varchar(40) NOT NULL,
    submit_id varchar(40) NOT NULL,
    question_id varchar(40) NOT NULL,
    question_snapshot_json longtext,
    student_answer varchar(32) DEFAULT NULL,
    is_correct tinyint(1) DEFAULT 0,
    awarded_score int DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_submit (submit_id),
    KEY idx_submit_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_dict (id, dict_name, dict_code, description, del_flag, create_by, create_time, type)
SELECT '1900000000000000100', '作业形态', 'assignment_mode', '作业形态', 0, 'admin', NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'assignment_mode');

INSERT INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time)
SELECT '1900000000000000101', d.id, '文件作业', 'file', '文件作业', 1, 1, 'admin', NOW()
FROM sys_dict d
WHERE d.dict_code = 'assignment_mode'
  AND NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_id = d.id AND i.item_value = 'file');

INSERT INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time)
SELECT '1900000000000000102', d.id, '线上客观题', 'objective', '线上客观题', 2, 1, 'admin', NOW()
FROM sys_dict d
WHERE d.dict_code = 'assignment_mode'
  AND NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_id = d.id AND i.item_value = 'objective');

INSERT INTO sys_permission (id, parent_id, name, url, component, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_route, is_leaf, keep_alive, hidden, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
SELECT '1900000000000001001', '1478631237925072897', '客观题作业管理', '/work/objectiveHomework', 'teaching/ObjectiveHomeworkManage', 'ObjectiveHomeworkManage', NULL, 1, NULL, '1', 3.00, 0, 'profile', 1, 1, 0, 0, '线上客观题后台管理页', 'admin', NOW(), 'admin', NOW(), 0, 0, '1', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = '1900000000000001001');

INSERT INTO sys_permission (id, parent_id, name, url, component, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_route, is_leaf, keep_alive, hidden, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
SELECT '1900000000000001002', '1900000000000001001', '查看', NULL, NULL, NULL, NULL, 2, 'objective:manage:view', '1', 1.00, 0, NULL, 1, 1, 0, 0, '客观题管理页查看权限', 'admin', NOW(), 'admin', NOW(), 0, 0, '1', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = '1900000000000001002');

INSERT INTO sys_permission (id, parent_id, name, url, component, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_route, is_leaf, keep_alive, hidden, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
SELECT '1900000000000001101', '1900000000000001001', '班级客观题新增', NULL, NULL, NULL, NULL, 2, 'objective:additional:add', '1', 2.00, 0, NULL, 1, 1, 0, 0, '布置班级客观题作业', 'admin', NOW(), 'admin', NOW(), 0, 0, '1', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = '1900000000000001101');

INSERT INTO sys_permission (id, parent_id, name, url, component, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_route, is_leaf, keep_alive, hidden, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
SELECT '1900000000000001102', '1900000000000001001', '班级客观题编辑', NULL, NULL, NULL, NULL, 2, 'objective:additional:edit', '1', 3.00, 0, NULL, 1, 1, 0, 0, '编辑班级客观题作业', 'admin', NOW(), 'admin', NOW(), 0, 0, '1', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = '1900000000000001102');

INSERT INTO sys_permission (id, parent_id, name, url, component, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_route, is_leaf, keep_alive, hidden, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
SELECT '1900000000000001103', '1900000000000001001', '班级客观题删除', NULL, NULL, NULL, NULL, 2, 'objective:additional:delete', '1', 4.00, 0, NULL, 1, 1, 0, 0, '删除班级客观题作业', 'admin', NOW(), 'admin', NOW(), 0, 0, '1', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = '1900000000000001103');

INSERT INTO sys_permission (id, parent_id, name, url, component, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_route, is_leaf, keep_alive, hidden, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
SELECT '1900000000000001201', '1900000000000001001', '单元客观题新增', NULL, NULL, NULL, NULL, 2, 'objective:courseunit:add', '1', 5.00, 0, NULL, 1, 1, 0, 0, '新增客观题单元', 'admin', NOW(), 'admin', NOW(), 0, 0, '1', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = '1900000000000001201');

INSERT INTO sys_permission (id, parent_id, name, url, component, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_route, is_leaf, keep_alive, hidden, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
SELECT '1900000000000001202', '1900000000000001001', '单元客观题编辑', NULL, NULL, NULL, NULL, 2, 'objective:courseunit:edit', '1', 6.00, 0, NULL, 1, 1, 0, 0, '编辑客观题单元', 'admin', NOW(), 'admin', NOW(), 0, 0, '1', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = '1900000000000001202');

INSERT INTO sys_permission (id, parent_id, name, url, component, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_route, is_leaf, keep_alive, hidden, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
SELECT '1900000000000001203', '1900000000000001001', '单元客观题删除', NULL, NULL, NULL, NULL, 2, 'objective:courseunit:delete', '1', 7.00, 0, NULL, 1, 1, 0, 0, '删除客观题单元', 'admin', NOW(), 'admin', NOW(), 0, 0, '1', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = '1900000000000001203');

INSERT INTO sys_permission (id, parent_id, name, url, component, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_route, is_leaf, keep_alive, hidden, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
SELECT '1900000000000001301', '1478631727777837058', '启用客观题形态', NULL, NULL, NULL, NULL, 2, 'objective:additional:mode', '1', 10.00, 0, NULL, 1, 1, 0, 0, '允许班级作业切换为线上客观题', 'admin', NOW(), 'admin', NOW(), 0, 0, '1', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = '1900000000000001301');

INSERT INTO sys_permission (id, parent_id, name, url, component, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_route, is_leaf, keep_alive, hidden, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
SELECT '1900000000000001302', '1249928626473635842', '启用客观题形态', NULL, NULL, NULL, NULL, 2, 'objective:courseunit:mode', '1', 10.00, 0, NULL, 1, 1, 0, 0, '允许课程单元切换为线上客观题', 'admin', NOW(), 'admin', NOW(), 0, 0, '1', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = '1900000000000001302');

UPDATE sys_permission SET
    name = '客观题作业管理',
    url = '/work/objectiveHomework',
    component = 'teaching/ObjectiveHomeworkManage',
    component_name = 'ObjectiveHomeworkManage',
    menu_type = 1,
    status = '1'
WHERE id = '1900000000000001001';

UPDATE sys_permission SET name = '查看', perms = 'objective:manage:view', menu_type = 2, status = '1' WHERE id = '1900000000000001002';
UPDATE sys_permission SET name = '班级客观题新增', perms = 'objective:additional:add', menu_type = 2, status = '1' WHERE id = '1900000000000001101';
UPDATE sys_permission SET name = '班级客观题编辑', perms = 'objective:additional:edit', menu_type = 2, status = '1' WHERE id = '1900000000000001102';
UPDATE sys_permission SET name = '班级客观题删除', perms = 'objective:additional:delete', menu_type = 2, status = '1' WHERE id = '1900000000000001103';
UPDATE sys_permission SET name = '单元客观题新增', perms = 'objective:courseunit:add', menu_type = 2, status = '1' WHERE id = '1900000000000001201';
UPDATE sys_permission SET name = '单元客观题编辑', perms = 'objective:courseunit:edit', menu_type = 2, status = '1' WHERE id = '1900000000000001202';
UPDATE sys_permission SET name = '单元客观题删除', perms = 'objective:courseunit:delete', menu_type = 2, status = '1' WHERE id = '1900000000000001203';
UPDATE sys_permission SET name = '启用客观题形态', perms = 'objective:additional:mode', menu_type = 2, status = '1' WHERE id = '1900000000000001301';
UPDATE sys_permission SET name = '启用客观题形态', perms = 'objective:courseunit:mode', menu_type = 2, status = '1' WHERE id = '1900000000000001302';

INSERT INTO sys_role_permission (id, role_id, permission_id, data_rule_ids)
SELECT CONCAT('obj_admin_', '1900000000000001001'), r.id, '1900000000000001001', NULL
FROM sys_role r
WHERE r.role_code = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = '1900000000000001001');

INSERT INTO sys_role_permission (id, role_id, permission_id, data_rule_ids)
SELECT CONCAT('obj_dev_', '1900000000000001001'), r.id, '1900000000000001001', NULL
FROM sys_role r
WHERE r.role_code = 'dev'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = '1900000000000001001');

INSERT INTO sys_role_permission (id, role_id, permission_id, data_rule_ids)
SELECT CONCAT('obj_teacher_', '1900000000000001001'), r.id, '1900000000000001001', NULL
FROM sys_role r
WHERE r.role_code = 'teacher'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = '1900000000000001001');

INSERT INTO sys_role_permission (id, role_id, permission_id, data_rule_ids)
SELECT CONCAT('obj_', r.role_code, '_', p.permission_id), r.id, p.permission_id, NULL
FROM sys_role r
JOIN (
    SELECT '1900000000000001002' AS permission_id UNION ALL
    SELECT '1900000000000001101' UNION ALL
    SELECT '1900000000000001102' UNION ALL
    SELECT '1900000000000001103' UNION ALL
    SELECT '1900000000000001201' UNION ALL
    SELECT '1900000000000001202' UNION ALL
    SELECT '1900000000000001203' UNION ALL
    SELECT '1900000000000001301' UNION ALL
    SELECT '1900000000000001302'
) p
WHERE r.role_code IN ('admin', 'dev', 'teacher')
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.permission_id);
