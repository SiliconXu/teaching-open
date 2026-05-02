package org.jeecg.modules.teaching.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.config.QiniuConfig;
import org.jeecg.modules.common.util.QiniuUtil;
import org.jeecg.modules.system.entity.SysFile;
import org.jeecg.modules.system.service.ISysFileService;
import org.jeecg.modules.teaching.entity.TeachingAdditionalWork;
import org.jeecg.modules.teaching.entity.TeachingCourseUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TeachingAssetCleanupService {

    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*\\]\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_IMAGE_PATTERN = Pattern.compile("<img\\b[^>]*\\bsrc\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>", Pattern.CASE_INSENSITIVE);

    @Autowired
    private ISysFileService sysFileService;
    @Autowired
    private QiniuUtil qiniuUtil;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${jeecg.path.upload}")
    private String uploadPath;
    @Value("${jeecg.path.staticDomain:}")
    private String staticDomain;

    public void cleanupAdditionalWorkAssets(TeachingAdditionalWork work) {
        if (work == null) {
            return;
        }
        deleteStoredFiles(Arrays.asList(work.getWorkCover(), work.getWorkDocumentUrl(), work.getWorkUrl()));
        cleanupObjectiveHomeworkAssets("additional", work.getId());
    }

    public void cleanupCourseUnitAssets(TeachingCourseUnit unit) {
        if (unit == null) {
            return;
        }
        deleteStoredFiles(Arrays.asList(
            unit.getCoursePpt(),
            unit.getUnitCover(),
            unit.getCourseVideo(),
            unit.getCourseWork(),
            unit.getCourseWorkAnswer(),
            unit.getCourseCase(),
            unit.getCoursePlan()
        ));
        cleanupObjectiveHomeworkAssets("courseUnit", unit.getId());
    }

    public void cleanupObjectiveHomeworkAssets(String sourceType, String sourceId) {
        if (StringUtils.isBlank(sourceType) || StringUtils.isBlank(sourceId)) {
            return;
        }
        List<Map<String, Object>> homeworkRows = jdbcTemplate.queryForList(
            "select id, source_markdown from teaching_objective_homework where source_type=? and source_id=? limit 1",
            sourceType, sourceId);
        if (homeworkRows.isEmpty()) {
            return;
        }
        Map<String, Object> homework = homeworkRows.get(0);
        String homeworkId = String.valueOf(homework.get("id"));
        Set<String> refs = new LinkedHashSet<>();
        addTextRefs(refs, stringValue(homework.get("source_markdown")));

        List<Map<String, Object>> questionRows = jdbcTemplate.queryForList(
            "select stem_text, stem_images, analysis_text, analysis_images from teaching_objective_question where homework_id=?",
            homeworkId);
        for (Map<String, Object> row : questionRows) {
            addTextRefs(refs, stringValue(row.get("stem_text")));
            addDelimitedRefs(refs, stringValue(row.get("stem_images")));
            addTextRefs(refs, stringValue(row.get("analysis_text")));
            addDelimitedRefs(refs, stringValue(row.get("analysis_images")));
        }

        List<Map<String, Object>> optionRows = jdbcTemplate.queryForList(
            "select qo.option_text, qo.option_image from teaching_objective_question_option qo inner join teaching_objective_question q on q.id = qo.question_id where q.homework_id=?",
            homeworkId);
        for (Map<String, Object> row : optionRows) {
            addTextRefs(refs, stringValue(row.get("option_text")));
            addDelimitedRefs(refs, stringValue(row.get("option_image")));
        }

        deleteStoredFiles(refs);
    }

    private void deleteStoredFiles(Collection<String> refs) {
        if (refs == null || refs.isEmpty()) {
            return;
        }
        for (String ref : refs) {
            if (StringUtils.isBlank(ref)) {
                continue;
            }
            for (String item : ref.split(",")) {
                deleteStoredFile(item);
            }
        }
    }

    private void deleteStoredFile(String ref) {
        String normalized = normalizeStoredPath(ref);
        if (StringUtils.isBlank(normalized)) {
            return;
        }

        SysFile sysFile = sysFileService.getOne(new QueryWrapper<SysFile>().eq("file_path", normalized).last("limit 1"));
        if (sysFile != null) {
            sysFileService.deleteWithFile(sysFile.getId());
            return;
        }

        String storageType = detectStorageType(ref, normalized);
        if (CommonConstant.UPLOAD_TYPE_QINIU.equals(storageType)) {
            qiniuUtil.deleteFileByKey(normalized);
            return;
        }
        if (CommonConstant.UPLOAD_TYPE_LOCAL.equals(storageType)) {
            File savedFile = new File(uploadPath + File.separator + normalized.replace("/", File.separator));
            if (savedFile.exists()) {
                savedFile.delete();
            }
        }
    }

    private void addDelimitedRefs(Set<String> refs, String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        for (String item : value.split(",")) {
            String trimmed = item == null ? "" : item.trim();
            if (StringUtils.isNotBlank(trimmed)) {
                refs.add(trimmed);
            }
        }
    }

    private void addTextRefs(Set<String> refs, String text) {
        if (StringUtils.isBlank(text)) {
            return;
        }
        Matcher markdownMatcher = MARKDOWN_IMAGE_PATTERN.matcher(text);
        while (markdownMatcher.find()) {
            String raw = cleanMarkdownRef(markdownMatcher.group(1));
            if (StringUtils.isNotBlank(raw)) {
                refs.add(raw);
            }
        }
        Matcher htmlMatcher = HTML_IMAGE_PATTERN.matcher(text);
        while (htmlMatcher.find()) {
            String raw = trimDecorators(htmlMatcher.group(1));
            if (StringUtils.isNotBlank(raw)) {
                refs.add(raw);
            }
        }
    }

    private String cleanMarkdownRef(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String cleaned = trimDecorators(raw);
        int titleStart = cleaned.indexOf(" \"");
        if (titleStart > -1) {
            cleaned = cleaned.substring(0, titleStart).trim();
        }
        return trimDecorators(cleaned);
    }

    private String trimDecorators(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.startsWith("<") && cleaned.endsWith(">") && cleaned.length() > 1) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        cleaned = cleaned.replace("&amp;", "&");
        int queryIndex = cleaned.indexOf('?');
        if (queryIndex > -1) {
            cleaned = cleaned.substring(0, queryIndex);
        }
        int hashIndex = cleaned.indexOf('#');
        if (hashIndex > -1) {
            cleaned = cleaned.substring(0, hashIndex);
        }
        return cleaned;
    }

    private String normalizeStoredPath(String ref) {
        String value = trimDecorators(ref);
        if (StringUtils.isBlank(value) || value.startsWith("data:")) {
            return null;
        }

        String qiniuDomain = trimSlash(QiniuConfig.domain);
        if (StringUtils.isNotBlank(qiniuDomain)) {
            String stripped = stripDomain(value, qiniuDomain);
            if (stripped != null) {
                return trimLeadingSlash(stripped);
            }
        }

        String staticBase = trimSlash(staticDomain);
        if (StringUtils.isNotBlank(staticBase)) {
            String stripped = stripDomain(value, staticBase);
            if (stripped != null) {
                return trimLeadingSlash(stripped);
            }
        }

        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("//")) {
            return null;
        }
        return trimLeadingSlash(value);
    }

    private String detectStorageType(String original, String normalized) {
        String value = trimDecorators(original);
        String qiniuDomain = trimSlash(QiniuConfig.domain);
        if (StringUtils.isNotBlank(qiniuDomain) && value != null && stripDomain(value, qiniuDomain) != null) {
            return CommonConstant.UPLOAD_TYPE_QINIU;
        }
        String staticBase = trimSlash(staticDomain);
        if (StringUtils.isNotBlank(staticBase) && value != null && stripDomain(value, staticBase) != null) {
            return CommonConstant.UPLOAD_TYPE_LOCAL;
        }
        return StringUtils.isNotBlank(normalized) ? CommonConstant.UPLOAD_TYPE_LOCAL : null;
    }

    private String stripDomain(String value, String domain) {
        if (StringUtils.isBlank(value) || StringUtils.isBlank(domain)) {
            return null;
        }
        String normalizedValue = value.trim();
        String normalizedDomain = trimSlash(domain);
        String hostOnly = trimLeadingDoubleSlash(stripHttpScheme(normalizedDomain));
        String[] prefixes = new String[]{
            normalizedDomain + "/",
            "https:" + normalizedDomain + "/",
            "http:" + normalizedDomain + "/",
            "//" + hostOnly + "/",
            "https://" + hostOnly + "/",
            "http://" + hostOnly + "/"
        };
        for (String prefix : prefixes) {
            if (normalizedValue.startsWith(prefix)) {
                return normalizedValue.substring(prefix.length());
            }
        }
        return null;
    }

    private String trimSlash(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String trimLeadingSlash(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }

    private String stripHttpScheme(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("https://")) {
            return trimmed.substring(8);
        }
        if (trimmed.startsWith("http://")) {
            return trimmed.substring(7);
        }
        return trimmed;
    }

    private String trimLeadingDoubleSlash(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        while (trimmed.startsWith("//")) {
            trimmed = trimmed.substring(2);
        }
        return trimmed;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
