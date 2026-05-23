package org.jeecg.modules.teaching.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.common.controller.BaseController;
import org.jeecg.modules.teaching.entity.TeachingAdditionalWork;
import org.jeecg.modules.teaching.entity.TeachingCourseUnit;
import org.jeecg.modules.teaching.enums.DepartDayLogType;
import org.jeecg.modules.teaching.service.ITeachingAdditionalWorkService;
import org.jeecg.modules.teaching.service.ITeachingCourseDeptService;
import org.jeecg.modules.teaching.service.ITeachingCourseUnitService;
import org.jeecg.modules.teaching.service.ITeachingDepartDayLogService;
import org.jeecg.modules.system.service.ISysUserDepartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Api(tags = "Objective Homework")
@RestController
@RequestMapping("/teaching/objectiveHomework")
public class TeachingObjectiveHomeworkController extends BaseController {

    @Autowired
    private ITeachingAdditionalWorkService teachingAdditionalWorkService;
    @Autowired
    private ITeachingCourseUnitService teachingCourseUnitService;
    @Autowired
    private ITeachingCourseDeptService teachingCourseDeptService;
    @Autowired
    private ITeachingDepartDayLogService teachingDepartDayLogService;
    @Autowired
    private ISysUserDepartService sysUserDepartService;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ApiOperation("Save additional objective homework")
    @PostMapping("/saveAdditional")
    public Result<?> saveAdditional(@RequestBody JSONObject payload) {
        try {
            JSONObject source = payload.getJSONObject("additionalWork");
            if (source == null) {
                return Result.error("missing additionalWork");
            }
            TeachingAdditionalWork additionalWork = source.toJavaObject(TeachingAdditionalWork.class);
            Result<?> permissionCheck = ensureObjectiveWritePermission("additional", additionalWork.getId());
            if (permissionCheck != null) {
                return permissionCheck;
            }
            additionalWork.setAssignmentMode("objective");
            teachingAdditionalWorkService.saveOrUpdate(additionalWork);
            saveObjectiveConfig("additional", additionalWork.getId(), payload.getJSONObject("objectiveConfig"));
            addAdditionalAssignLog(additionalWork);
            return Result.ok(teachingAdditionalWorkService.getById(additionalWork.getId()));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @ApiOperation("Save course unit objective homework")
    @PostMapping("/saveCourseUnit")
    public Result<?> saveCourseUnit(@RequestBody JSONObject payload) {
        try {
            JSONObject source = payload.getJSONObject("courseUnit");
            if (source == null) {
                return Result.error("missing courseUnit");
            }
            TeachingCourseUnit courseUnit = source.toJavaObject(TeachingCourseUnit.class);
            Result<?> permissionCheck = ensureObjectiveWritePermission("courseUnit", courseUnit.getId());
            if (permissionCheck != null) {
                return permissionCheck;
            }
            courseUnit.setAssignmentMode("objective");
            teachingCourseUnitService.saveOrUpdate(courseUnit);
            saveObjectiveConfig("courseUnit", courseUnit.getId(), payload.getJSONObject("objectiveConfig"));
            return Result.ok(teachingCourseUnitService.getById(courseUnit.getId()));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @ApiOperation("Get config by source")
    @GetMapping("/getBySource")
    public Result<?> getBySource(@RequestParam String sourceType, @RequestParam String sourceId) {
        try {
            Result<?> permissionCheck = ensureObjectiveReadPermission(sourceType);
            if (permissionCheck != null) {
                return permissionCheck;
            }
            Map<String, Object> homework = queryOne("select * from teaching_objective_homework where source_type=? and source_id=? limit 1", sourceType, sourceId);
            JSONObject result = new JSONObject(true);
            result.put("sourceType", sourceType);
            result.put("sourceId", sourceId);
            int redoLimit = homework == null ? 0 : normalizeRedoLimit(homework);
            result.put("redoLimit", redoLimit);
            result.put("allowRedo", redoLimit > 0);
            result.put("showResultAfterSubmit", homework == null ? Boolean.TRUE : toBool(homework.get("show_result_after_submit")));
            result.put("questionCount", homework == null ? 0 : toInt(homework.get("question_count")));
            result.put("totalScore", homework == null ? 0 : toInt(homework.get("total_score")));
            result.put("sourceMarkdown", homework == null ? null : homework.get("source_markdown"));
            result.put("questions", homework == null ? new JSONArray() : loadQuestions(String.valueOf(homework.get("id")), true));
            return Result.ok(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @ApiOperation("Student view")
    @GetMapping("/studentView")
    public Result<?> studentView(@RequestParam String sourceType,
                                 @RequestParam String sourceId,
                                 @RequestParam(required = false) String departId) {
        try {
            String userId = getCurrentUser().getId();
            JSONObject context = resolveSourceContext(sourceType, sourceId, departId, userId);
            Map<String, Object> homework = queryOne("select * from teaching_objective_homework where source_type=? and source_id=? limit 1", sourceType, sourceId);
            if (homework == null) {
                return Result.error("objective config not found");
            }
            JSONObject result = new JSONObject(true);
            result.put("sourceType", sourceType);
            result.put("sourceId", sourceId);
            result.put("title", context.getString("title"));
            result.put("description", context.getString("description"));
            result.put("documentUrl", context.getString("documentUrl"));
            result.put("departId", context.getString("departId"));
            int redoLimit = normalizeRedoLimit(homework);
            result.put("redoLimit", redoLimit);
            result.put("allowRedo", redoLimit > 0);
            result.put("showResultAfterSubmit", toBool(homework.get("show_result_after_submit")));
            result.put("questionCount", toInt(homework.get("question_count")));
            result.put("totalScore", toInt(homework.get("total_score")));
            result.put("questions", loadQuestions(String.valueOf(homework.get("id")), false));
            Map<String, Object> latestSubmit = queryLatestSubmit(String.valueOf(homework.get("id")), userId, context.getString("departId"));
            boolean submitted = latestSubmit != null;
            int remainingRedoCount = latestSubmit == null ? redoLimit : Math.max(0, redoLimit - toInt(latestSubmit.get("attempt_no")));
            result.put("submitted", submitted);
            result.put("remainingRedoCount", remainingRedoCount);
            result.put("canSubmit", !submitted || remainingRedoCount > 0);
            if (submitted) {
                result.put("latestResult", buildSubmitResult(homework, latestSubmit, toBool(homework.get("show_result_after_submit"))));
            }
            return Result.ok(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @ApiOperation("Submit objective homework")
    @PostMapping("/submit")
    public Result<?> submit(@RequestBody JSONObject payload) {
        try {
            String sourceType = payload.getString("sourceType");
            String sourceId = payload.getString("sourceId");
            if (StringUtils.isBlank(sourceType) || StringUtils.isBlank(sourceId)) {
                return Result.error("missing source info");
            }
            String userId = getCurrentUser().getId();
            JSONObject context = resolveSourceContext(sourceType, sourceId, payload.getString("departId"), userId);
            Map<String, Object> homework = queryOne("select * from teaching_objective_homework where source_type=? and source_id=? limit 1", sourceType, sourceId);
            if (homework == null) {
                return Result.error("objective config not found");
            }
            Map<String, Object> latestSubmit = queryLatestSubmit(String.valueOf(homework.get("id")), userId, context.getString("departId"));
            int redoLimit = normalizeRedoLimit(homework);
            int latestAttemptNo = latestSubmit == null ? 0 : toInt(latestSubmit.get("attempt_no"));
            if (latestSubmit != null && latestAttemptNo >= (redoLimit + 1)) {
                return Result.error("redo is disabled");
            }
            JSONArray questionList = loadQuestions(String.valueOf(homework.get("id")), true);
            if (questionList.isEmpty()) {
                return Result.error("no questions");
            }
            Map<String, String> answerMap = new HashMap<>();
            JSONArray answers = payload.getJSONArray("answers");
            if (answers != null) {
                for (int i = 0; i < answers.size(); i++) {
                    JSONObject answer = answers.getJSONObject(i);
                    if (answer != null && StringUtils.isNotBlank(answer.getString("questionId"))) {
                        answerMap.put(answer.getString("questionId"), normalizeAnswer(answer.getString("answer")));
                    }
                }
            }
            String submitId = nextId();
            int attemptNo = latestAttemptNo + 1;
            Timestamp now = now();
            jdbcTemplate.update(
                "insert into teaching_objective_submit (id, create_by, create_time, update_by, update_time, sys_org_code, homework_id, source_type, source_id, student_id, depart_id, submit_status, objective_score, right_count, question_count, attempt_no, submitted_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                submitId, getCurrentUser().getUsername(), now, getCurrentUser().getUsername(), now, getCurrentUser().getOrgCode(),
                String.valueOf(homework.get("id")), sourceType, sourceId, userId, context.getString("departId"), "submitted", 0, 0,
                questionList.size(), attemptNo, now);
            int totalScore = 0;
            int rightCount = 0;
            for (int i = 0; i < questionList.size(); i++) {
                JSONObject question = questionList.getJSONObject(i);
                String studentAnswer = answerMap.get(question.getString("id"));
                boolean correct = StringUtils.equals(normalizeAnswer(question.getString("correctAnswer")), studentAnswer);
                int awardedScore = correct ? defaultScore(question.getInteger("score")) : 0;
                if (correct) {
                    rightCount++;
                }
                totalScore += awardedScore;
                jdbcTemplate.update(
                    "insert into teaching_objective_submit_item (id, submit_id, question_id, question_snapshot_json, student_answer, is_correct, awarded_score) values (?,?,?,?,?,?,?)",
                    nextId(), submitId, question.getString("id"), JSON.toJSONString(question), studentAnswer, correct ? 1 : 0, awardedScore);
            }
            jdbcTemplate.update("update teaching_objective_submit set objective_score=?, right_count=?, update_by=?, update_time=? where id=?",
                totalScore, rightCount, getCurrentUser().getUsername(), now, submitId);
            addSubmitLog(sourceType, context.getString("departId"), submitId);
            Map<String, Object> submitRow = queryOne("select * from teaching_objective_submit where id=?", submitId);
            return Result.ok(buildSubmitResult(homework, submitRow, toBool(homework.get("show_result_after_submit"))));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    private void saveObjectiveConfig(String sourceType, String sourceId, JSONObject config) {
        if (config == null) {
            throw new IllegalArgumentException("missing objectiveConfig");
        }
        JSONArray normalizedQuestions = normalizeQuestions(config.getJSONArray("questions"));
        Map<String, Object> existing = queryOne("select * from teaching_objective_homework where source_type=? and source_id=? limit 1", sourceType, sourceId);
        String homeworkId = existing == null ? nextId() : String.valueOf(existing.get("id"));
        clearHomework(homeworkId);
        int redoLimit = normalizeRedoLimit(config);
        boolean allowRedo = redoLimit > 0;
        boolean showResultAfterSubmit = config.getBoolean("showResultAfterSubmit") == null || config.getBoolean("showResultAfterSubmit");
        String sourceMarkdown = trimToNull(config.getString("sourceMarkdown"));
        int totalScore = 0;
        for (int i = 0; i < normalizedQuestions.size(); i++) {
            totalScore += defaultScore(normalizedQuestions.getJSONObject(i).getInteger("score"));
        }
        Timestamp now = now();
        if (existing == null) {
            jdbcTemplate.update(
                "insert into teaching_objective_homework (id, create_by, create_time, update_by, update_time, sys_org_code, source_type, source_id, allow_redo, redo_limit, show_result_after_submit, question_count, total_score, source_markdown) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                homeworkId, getCurrentUser().getUsername(), now, getCurrentUser().getUsername(), now, getCurrentUser().getOrgCode(),
                sourceType, sourceId, allowRedo ? 1 : 0, redoLimit, showResultAfterSubmit ? 1 : 0, normalizedQuestions.size(), totalScore, sourceMarkdown);
        } else {
            jdbcTemplate.update(
                "update teaching_objective_homework set update_by=?, update_time=?, allow_redo=?, redo_limit=?, show_result_after_submit=?, question_count=?, total_score=?, source_markdown=? where id=?",
                getCurrentUser().getUsername(), now, allowRedo ? 1 : 0, redoLimit, showResultAfterSubmit ? 1 : 0, normalizedQuestions.size(), totalScore, sourceMarkdown, homeworkId);
        }
        for (int i = 0; i < normalizedQuestions.size(); i++) {
            JSONObject question = normalizedQuestions.getJSONObject(i);
            String questionId = nextId();
            jdbcTemplate.update(
                "insert into teaching_objective_question (id, homework_id, question_no, question_type, stem_text, stem_images, analysis_text, analysis_images, correct_answer, score, sort_order) values (?,?,?,?,?,?,?,?,?,?,?)",
                questionId, homeworkId, i + 1, question.getString("questionType"), question.getString("stemText"), question.getString("stemImages"),
                question.getString("analysisText"), question.getString("analysisImages"), normalizeAnswer(question.getString("correctAnswer")),
                defaultScore(question.getInteger("score")), i + 1);
            JSONArray options = question.getJSONArray("options");
            if (options != null) {
                for (int j = 0; j < options.size(); j++) {
                    JSONObject option = options.getJSONObject(j);
                    jdbcTemplate.update(
                        "insert into teaching_objective_question_option (id, question_id, option_key, option_text, option_image, sort_order) values (?,?,?,?,?,?)",
                        nextId(), questionId, option.getString("optionKey"), option.getString("optionText"), option.getString("optionImage"), j + 1);
                }
            }
        }
    }

    private JSONArray normalizeQuestions(JSONArray questions) {
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("questions required");
        }
        JSONArray normalized = new JSONArray();
        for (int i = 0; i < questions.size(); i++) {
            JSONObject raw = questions.getJSONObject(i);
            if (raw == null) {
                continue;
            }
            JSONObject question = new JSONObject(true);
            String questionType = raw.getString("questionType");
            question.put("questionType", questionType);
            question.put("stemText", trimToNull(raw.getString("stemText")));
            question.put("stemImages", trimToNull(raw.getString("stemImages")));
            question.put("analysisText", trimToNull(raw.getString("analysisText")));
            question.put("analysisImages", trimToNull(raw.getString("analysisImages")));
            question.put("score", defaultScore(raw.getInteger("score")));
            if (StringUtils.isBlank(question.getString("stemText")) && StringUtils.isBlank(question.getString("stemImages"))) {
                throw new IllegalArgumentException("question stem required");
            }
            if (StringUtils.equalsIgnoreCase("single", questionType)) {
                JSONArray options = raw.getJSONArray("options");
                if (options == null || options.size() < 2) {
                    throw new IllegalArgumentException("single choice needs at least 2 options");
                }
                JSONArray normalizedOptions = new JSONArray();
                Set<String> optionKeys = new LinkedHashSet<>();
                for (int j = 0; j < options.size(); j++) {
                    JSONObject rawOption = options.getJSONObject(j);
                    JSONObject option = new JSONObject(true);
                    String optionKey = StringUtils.isNotBlank(rawOption.getString("optionKey")) ? rawOption.getString("optionKey") : buildOptionKey(j);
                    option.put("optionKey", optionKey);
                    option.put("optionText", trimToNull(rawOption.getString("optionText")));
                    option.put("optionImage", trimToNull(rawOption.getString("optionImage")));
                    if (StringUtils.isBlank(option.getString("optionText")) && StringUtils.isBlank(option.getString("optionImage"))) {
                        throw new IllegalArgumentException("option required");
                    }
                    normalizedOptions.add(option);
                    optionKeys.add(optionKey);
                }
                String correctAnswer = normalizeAnswer(raw.getString("correctAnswer"));
                if (!optionKeys.contains(correctAnswer)) {
                    throw new IllegalArgumentException("correctAnswer invalid");
                }
                question.put("correctAnswer", correctAnswer);
                question.put("options", normalizedOptions);
            } else if (StringUtils.equalsIgnoreCase("judge", questionType)) {
                String correctAnswer = normalizeAnswer(raw.getString("correctAnswer"));
                if (!StringUtils.equals("T", correctAnswer) && !StringUtils.equals("F", correctAnswer)) {
                    throw new IllegalArgumentException("judge answer invalid");
                }
                question.put("correctAnswer", correctAnswer);
                question.put("options", new JSONArray());
            } else {
                throw new IllegalArgumentException("unsupported questionType");
            }
            normalized.add(question);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("questions required");
        }
        return normalized;
    }

    private JSONObject resolveSourceContext(String sourceType, String sourceId, String departId, String userId) {
        JSONObject context = new JSONObject(true);
        if (StringUtils.equals("additional", sourceType)) {
            TeachingAdditionalWork additionalWork = teachingAdditionalWorkService.getById(sourceId);
            if (additionalWork == null) {
                throw new IllegalArgumentException("additional work not found");
            }
            if (!StringUtils.equals("objective", additionalWork.getAssignmentMode())) {
                throw new IllegalArgumentException("not objective homework");
            }
            List<String> myDepartIds = sysUserDepartService.userDepartIds(userId);
            List<String> workDepartIds = splitComma(additionalWork.getWorkDept());
            String matchedDepartId = null;
            if (StringUtils.isNotBlank(departId) && myDepartIds.contains(departId) && workDepartIds.contains(departId)) {
                matchedDepartId = departId;
            } else {
                for (String myDepartId : myDepartIds) {
                    if (workDepartIds.contains(myDepartId)) {
                        matchedDepartId = myDepartId;
                        break;
                    }
                }
            }
            if (StringUtils.isBlank(matchedDepartId)) {
                throw new IllegalArgumentException("no permission");
            }
            context.put("departId", matchedDepartId);
            context.put("title", additionalWork.getWorkName());
            context.put("description", additionalWork.getWorkDesc());
            context.put("documentUrl", additionalWork.getWorkDocumentUrl());
            return context;
        }
        if (StringUtils.equals("courseUnit", sourceType)) {
            TeachingCourseUnit courseUnit = teachingCourseUnitService.getById(sourceId);
            if (courseUnit == null) {
                throw new IllegalArgumentException("course unit not found");
            }
            if (!StringUtils.equals("objective", courseUnit.getAssignmentMode())) {
                throw new IllegalArgumentException("not objective homework");
            }
            if (!teachingCourseDeptService.checkCoursePermission(courseUnit.getCourseId(), userId)) {
                throw new IllegalArgumentException("no permission");
            }
            context.put("departId", teachingCourseUnitService.getUserDepartIdByUnitId(userId, sourceId));
            context.put("title", courseUnit.getUnitName());
            context.put("description", courseUnit.getUnitIntro());
            context.put("documentUrl", null);
            return context;
        }
        throw new IllegalArgumentException("unsupported sourceType");
    }

    private JSONArray loadQuestions(String homeworkId, boolean includeAnswer) {
        JSONArray result = new JSONArray();
        List<Map<String, Object>> questionRows = queryList("select * from teaching_objective_question where homework_id=? order by question_no asc, sort_order asc", homeworkId);
        for (Map<String, Object> row : questionRows) {
            JSONObject question = new JSONObject(true);
            question.put("id", row.get("id"));
            question.put("questionId", row.get("id"));
            question.put("questionNo", toInt(row.get("question_no")));
            question.put("questionType", row.get("question_type"));
            question.put("stemText", row.get("stem_text"));
            question.put("stemImages", row.get("stem_images"));
            question.put("analysisText", includeAnswer ? row.get("analysis_text") : null);
            question.put("analysisImages", includeAnswer ? row.get("analysis_images") : null);
            question.put("score", toInt(row.get("score")));
            if (includeAnswer) {
                question.put("correctAnswer", row.get("correct_answer"));
            }
            JSONArray options = new JSONArray();
            List<Map<String, Object>> optionRows = queryList("select * from teaching_objective_question_option where question_id=? order by sort_order asc", String.valueOf(row.get("id")));
            for (Map<String, Object> optionRow : optionRows) {
                JSONObject option = new JSONObject(true);
                option.put("id", optionRow.get("id"));
                option.put("optionKey", optionRow.get("option_key"));
                option.put("optionText", optionRow.get("option_text"));
                option.put("optionImage", optionRow.get("option_image"));
                option.put("sortOrder", toInt(optionRow.get("sort_order")));
                options.add(option);
            }
            question.put("options", options);
            result.add(question);
        }
        return result;
    }

    private JSONObject buildSubmitResult(Map<String, Object> homework, Map<String, Object> submit, boolean includeItems) {
        JSONObject result = new JSONObject(true);
        result.put("submitId", submit.get("id"));
        result.put("sourceType", submit.get("source_type"));
        result.put("sourceId", submit.get("source_id"));
        int redoLimit = normalizeRedoLimit(homework);
        result.put("redoLimit", redoLimit);
        result.put("allowRedo", redoLimit > 0);
        result.put("showResultAfterSubmit", toBool(homework.get("show_result_after_submit")));
        result.put("submitted", true);
        result.put("objectiveScore", toInt(submit.get("objective_score")));
        result.put("totalScore", toInt(homework.get("total_score")));
        result.put("rightCount", toInt(submit.get("right_count")));
        result.put("questionCount", toInt(submit.get("question_count")));
        result.put("attemptNo", toInt(submit.get("attempt_no")));
        result.put("remainingRedoCount", Math.max(0, redoLimit - toInt(submit.get("attempt_no"))));
        result.put("submittedAt", submit.get("submitted_at"));
        JSONArray items = new JSONArray();
        List<Map<String, Object>> itemRows = queryList("select * from teaching_objective_submit_item where submit_id=?", String.valueOf(submit.get("id")));
        List<JSONObject> parsed = new ArrayList<>();
        for (Map<String, Object> itemRow : itemRows) {
            JSONObject snapshot = JSON.parseObject(String.valueOf(itemRow.get("question_snapshot_json")));
            JSONObject item = new JSONObject(true);
            item.put("questionId", itemRow.get("question_id"));
            item.put("questionNo", snapshot.getInteger("questionNo"));
            item.put("questionType", snapshot.getString("questionType"));
            item.put("studentAnswer", itemRow.get("student_answer"));
            item.put("correct", toBool(itemRow.get("is_correct")));
            item.put("score", snapshot.getInteger("score"));
            item.put("awardedScore", toInt(itemRow.get("awarded_score")));
            if (includeItems) {
                item.put("stemText", snapshot.getString("stemText"));
                item.put("stemImages", snapshot.getString("stemImages"));
                item.put("correctAnswer", snapshot.getString("correctAnswer"));
                item.put("analysisText", snapshot.getString("analysisText"));
                item.put("analysisImages", snapshot.getString("analysisImages"));
                item.put("options", snapshot.getJSONArray("options"));
            }
            parsed.add(item);
        }
        parsed.sort(Comparator.comparing(o -> o.getInteger("questionNo")));
        parsed.forEach(items::add);
        result.put("items", items);
        return result;
    }

    private void clearHomework(String homeworkId) {
        List<Map<String, Object>> submitRows = queryList("select id from teaching_objective_submit where homework_id=?", homeworkId);
        if (!submitRows.isEmpty()) {
            List<String> submitIds = submitRows.stream().map(row -> String.valueOf(row.get("id"))).collect(Collectors.toList());
            jdbcTemplate.update("delete from teaching_objective_submit_item where submit_id in (" + placeholders(submitIds.size()) + ")", submitIds.toArray());
            jdbcTemplate.update("delete from teaching_objective_submit where id in (" + placeholders(submitIds.size()) + ")", submitIds.toArray());
        }
        List<Map<String, Object>> questionRows = queryList("select id from teaching_objective_question where homework_id=?", homeworkId);
        if (!questionRows.isEmpty()) {
            List<String> questionIds = questionRows.stream().map(row -> String.valueOf(row.get("id"))).collect(Collectors.toList());
            jdbcTemplate.update("delete from teaching_objective_question_option where question_id in (" + placeholders(questionIds.size()) + ")", questionIds.toArray());
            jdbcTemplate.update("delete from teaching_objective_question where id in (" + placeholders(questionIds.size()) + ")", questionIds.toArray());
        }
    }

    private Map<String, Object> queryLatestSubmit(String homeworkId, String userId, String departId) {
        if (StringUtils.isBlank(departId)) {
            List<Map<String, Object>> rows = queryList("select * from teaching_objective_submit where homework_id=? and student_id=? order by attempt_no desc, submitted_at desc, create_time desc limit 1", homeworkId, userId);
            return rows.isEmpty() ? null : rows.get(0);
        }
        List<Map<String, Object>> rows = queryList("select * from teaching_objective_submit where homework_id=? and student_id=? and depart_id=? order by attempt_no desc, submitted_at desc, create_time desc limit 1", homeworkId, userId, departId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void addAdditionalAssignLog(TeachingAdditionalWork additionalWork) {
        if (additionalWork == null || StringUtils.isBlank(additionalWork.getWorkDept())) {
            return;
        }
        for (String departId : splitComma(additionalWork.getWorkDept())) {
            String key = String.format("departLog:addiWorkAssign:%s", departId);
            if (!redisUtil.sHasKey(key, additionalWork.getId())) {
                redisUtil.sSet(key, additionalWork.getId());
                teachingDepartDayLogService.addLog(departId, DepartDayLogType.COURSE_WORK_ASSIGN_COUNT);
            }
        }
    }

    private void addSubmitLog(String sourceType, String departId, String submitId) {
        if (StringUtils.isBlank(departId)) {
            return;
        }
        if (StringUtils.equals("additional", sourceType)) {
            String key = String.format("departLog:addiWorkSubmit:%s", departId);
            if (!redisUtil.sHasKey(key, submitId)) {
                redisUtil.sSet(key, submitId);
                teachingDepartDayLogService.addLog(departId, DepartDayLogType.ADDITIONAL_WORK_SUBMIT_COUNT);
            }
            return;
        }
        if (StringUtils.equals("courseUnit", sourceType)) {
            String key = String.format("departLog:courseWorkSubmit:%s", departId);
            if (!redisUtil.sHasKey(key, submitId)) {
                redisUtil.sSet(key, submitId);
                teachingDepartDayLogService.addLog(departId, DepartDayLogType.COURSE_WORK_SUBMIT_COUNT);
            }
        }
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = queryList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> queryList(String sql, Object... args) {
        return jdbcTemplate.queryForList(sql, args);
    }

    private List<String> splitComma(String value) {
        if (StringUtils.isBlank(value)) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(StringUtils::isNotBlank).collect(Collectors.toList());
    }

    private String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private String normalizeAnswer(String answer) {
        return StringUtils.isBlank(answer) ? null : answer.trim().toUpperCase();
    }

    private int defaultScore(Integer score) {
        return score == null || score <= 0 ? 5 : score;
    }

    private String buildOptionKey(int index) {
        return String.valueOf((char) ('A' + index));
    }

    private boolean toBool(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String normalized = String.valueOf(value);
        return StringUtils.equalsIgnoreCase(normalized, "1") || StringUtils.equalsIgnoreCase(normalized, "true");
    }

    private int normalizeRedoLimit(Map<String, Object> homework) {
        if (homework == null) {
            return 0;
        }
        Object redoLimit = homework.get("redo_limit");
        if (redoLimit != null) {
            return Math.max(0, toInt(redoLimit));
        }
        return toBool(homework.get("allow_redo")) ? 1 : 0;
    }

    private int normalizeRedoLimit(JSONObject config) {
        if (config == null) {
            return 0;
        }
        if (config.containsKey("redoLimit")) {
            Integer value = config.getInteger("redoLimit");
            return Math.max(0, value == null ? 0 : value);
        }
        if (config.containsKey("allowRedo")) {
            return config.getBooleanValue("allowRedo") ? 1 : 0;
        }
        return 1;
    }

    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private String nextId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }

    private String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private Result<?> ensureObjectiveReadPermission(String sourceType) {
        return ensureTeacherPermission(false, "objective:manage:view", sourcePerm(sourceType, "mode"), sourcePerm(sourceType, "edit"));
    }

    private Result<?> ensureObjectiveWritePermission(String sourceType, String sourceId) {
        String action = sourceExists(sourceType, sourceId) ? "edit" : "add";
        return ensureTeacherPermission(true, sourcePerm(sourceType, "mode"), sourcePerm(sourceType, action));
    }

    private Result<?> ensureTeacherPermission(boolean requireAll, String... perms) {
        LoginUser currentUser = getCurrentUser();
        if (currentUser == null) {
            return Result.error("请先登录");
        }
        boolean teacherOperator = hasRole("admin") || hasRole("dev") || hasRole("teacher") || Integer.valueOf(2).equals(currentUser.getUserIdentity());
        if (!teacherOperator) {
            return Result.error("仅教师可操作客观题作业");
        }
        if (hasRole("admin") || hasRole("dev")) {
            return null;
        }
        Subject subject = SecurityUtils.getSubject();
        List<String> effectivePerms = Arrays.stream(perms).filter(StringUtils::isNotBlank).collect(Collectors.toList());
        if (effectivePerms.isEmpty()) {
            return null;
        }
        boolean passed = requireAll;
        for (String perm : effectivePerms) {
            boolean permitted = subject.isPermitted(perm);
            if (requireAll && !permitted) {
                return Result.error("当前账号没有客观题相关权限");
            }
            if (!requireAll && permitted) {
                passed = true;
                break;
            }
        }
        if (!requireAll && !passed) {
            return Result.error("当前账号没有客观题相关权限");
        }
        return null;
    }

    private String sourcePerm(String sourceType, String action) {
        if (StringUtils.equals("additional", sourceType)) {
            return "objective:additional:" + action;
        }
        if (StringUtils.equals("courseUnit", sourceType)) {
            return "objective:courseunit:" + action;
        }
        return null;
    }

    private boolean sourceExists(String sourceType, String sourceId) {
        if (StringUtils.isBlank(sourceId)) {
            return false;
        }
        if (StringUtils.equals("additional", sourceType)) {
            return teachingAdditionalWorkService.getById(sourceId) != null;
        }
        if (StringUtils.equals("courseUnit", sourceType)) {
            return teachingCourseUnitService.getById(sourceId) != null;
        }
        return false;
    }
}

