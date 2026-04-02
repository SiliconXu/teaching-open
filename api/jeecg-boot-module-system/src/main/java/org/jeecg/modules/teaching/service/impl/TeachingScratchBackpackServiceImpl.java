package org.jeecg.modules.teaching.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.constant.CacheConstant;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.util.MinioUtil;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.common.util.UUIDGenerator;
import org.jeecg.common.util.oss.OssBootUtil;
import org.jeecg.config.QiniuConfig;
import org.jeecg.modules.common.util.QiniuUtil;
import org.jeecg.modules.teaching.entity.TeachingScratchBackpack;
import org.jeecg.modules.teaching.mapper.TeachingScratchBackpackMapper;
import org.jeecg.modules.teaching.model.ScratchBackpackModel;
import org.jeecg.modules.teaching.service.ITeachingScratchBackpackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class TeachingScratchBackpackServiceImpl extends ServiceImpl<TeachingScratchBackpackMapper, TeachingScratchBackpack>
        implements ITeachingScratchBackpackService {

    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private QiniuUtil qiniuUtil;

    @Value("${jeecg.uploadType}")
    private String uploadType;
    @Value("${jeecg.path.upload}")
    private String uploadPath;
    @Value("${jeecg.path.staticDomain}")
    private String staticDomain;

    @Override
    public List<ScratchBackpackModel> getBackpackList(String userId, Integer offset, Integer limit) {
        migrateLegacyBackpack(userId);
        QueryWrapper<TeachingScratchBackpack> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByAsc("create_time");
        List<TeachingScratchBackpack> records = this.list(wrapper);
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        int safeOffset = Math.max(offset == null ? 0 : offset, 0);
        int safeLimit = Math.max(limit == null ? 20 : limit, 1);
        if (safeOffset >= records.size()) {
            return Collections.emptyList();
        }

        int end = Math.min(records.size(), safeOffset + safeLimit);
        List<ScratchBackpackModel> result = new ArrayList<>(end - safeOffset);
        for (TeachingScratchBackpack record : records.subList(safeOffset, end)) {
            result.add(toModel(record));
        }
        return result;
    }

    @Override
    public ScratchBackpackModel saveBackpack(String userId, JSONObject json) {
        String body = json.getString("body");
        String thumbnail = json.getString("thumbnail");
        String type = json.getString("type");
        String mime = json.getString("mime");
        String name = json.getString("name");

        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("用户未登录");
        }
        if (!StringUtils.hasText(body) || !StringUtils.hasText(thumbnail)) {
            throw new IllegalArgumentException("背包内容不完整");
        }

        String id = UUIDGenerator.generate();
        StoredFile bodyFile = null;
        StoredFile thumbnailFile = null;
        try {
            bodyFile = storeFile(id, false, body, mime, type);
            thumbnailFile = storeFile(id, true, thumbnail, "image/jpeg", type);

            Date now = new Date();
            TeachingScratchBackpack entity = new TeachingScratchBackpack();
            entity.setId(id);
            entity.setUserId(userId);
            entity.setName(name);
            entity.setMime(mime);
            entity.setType(type);
            entity.setBodyPath(bodyFile.path);
            entity.setThumbnailPath(thumbnailFile.path);
            entity.setStorageType(bodyFile.storageType);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            entity.setDelFlag(CommonConstant.DEL_FLAG_0);
            this.save(entity);
            return toModel(entity);
        } catch (Exception e) {
            if (bodyFile != null) {
                deleteStoredFile(bodyFile.storageType, bodyFile.path);
            }
            if (thumbnailFile != null) {
                deleteStoredFile(thumbnailFile.storageType, thumbnailFile.path);
            }
            throw new RuntimeException("保存背包失败", e);
        }
    }

    @Override
    public void deleteBackpack(String userId, String id) {
        migrateLegacyBackpack(userId);
        QueryWrapper<TeachingScratchBackpack> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).eq("user_id", userId).last("limit 1");
        TeachingScratchBackpack record = this.getOne(wrapper, false);
        if (record == null) {
            return;
        }
        deleteStoredFile(record.getStorageType(), record.getBodyPath());
        deleteStoredFile(record.getStorageType(), record.getThumbnailPath());
        this.removeById(record.getId());
    }

    private void migrateLegacyBackpack(String userId) {
        String cacheKey = CacheConstant.SCRATCH_BACKPACK_LIST + userId;
        if (redisUtil.lGetListSize(cacheKey) <= 0) {
            return;
        }
        List<Object> legacyItems = redisUtil.lGet(cacheKey, 0, -1);
        if (legacyItems == null || legacyItems.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        int index = 0;
        for (Object item : legacyItems) {
            ScratchBackpackModel legacy = toLegacyModel(item);
            if (legacy == null || !StringUtils.hasText(legacy.getId()) || this.getById(legacy.getId()) != null) {
                continue;
            }

            String storageType = detectStorageType(legacy.getBody(), legacy.getThumbnail());
            TeachingScratchBackpack entity = new TeachingScratchBackpack();
            entity.setId(legacy.getId());
            entity.setUserId(userId);
            entity.setName(legacy.getName());
            entity.setMime(legacy.getMime());
            entity.setType(legacy.getType());
            entity.setBodyPath(normalizeStoredPath(legacy.getBody(), storageType));
            entity.setThumbnailPath(normalizeStoredPath(legacy.getThumbnail(), storageType));
            entity.setStorageType(storageType);
            entity.setCreateTime(new Date(now + index));
            entity.setUpdateTime(new Date(now + index));
            entity.setDelFlag(CommonConstant.DEL_FLAG_0);
            this.save(entity);
            index++;
        }
    }

    private ScratchBackpackModel toLegacyModel(Object item) {
        if (item == null) {
            return null;
        }
        if (item instanceof ScratchBackpackModel) {
            return (ScratchBackpackModel) item;
        }
        return JSON.parseObject(JSON.toJSONString(item), ScratchBackpackModel.class);
    }

    private ScratchBackpackModel toModel(TeachingScratchBackpack entity) {
        ScratchBackpackModel model = new ScratchBackpackModel();
        model.setId(entity.getId());
        model.setName(entity.getName());
        model.setMime(entity.getMime());
        model.setType(entity.getType());
        model.setBody(buildAccessUrl(entity.getStorageType(), entity.getBodyPath()));
        model.setThumbnail(buildAccessUrl(entity.getStorageType(), entity.getThumbnailPath()));
        return model;
    }

    private StoredFile storeFile(String id, boolean thumbnail, String base64Value, String defaultMime, String type) throws Exception {
        String payload = stripBase64Prefix(base64Value);
        byte[] bytes = Base64.getMimeDecoder().decode(payload);
        String mime = resolveMime(base64Value, defaultMime);
        String ext = resolveExtension(mime, thumbnail, type);
        String relativePath = buildRelativePath(id, thumbnail, ext);
        String effectiveType = normalizeUploadType();

        if (CommonConstant.UPLOAD_TYPE_QINIU.equals(effectiveType)) {
            String key = qiniuUtil.uploadToQiniu(bytes, relativePath);
            if (!StringUtils.hasText(key)) {
                throw new IllegalStateException("七牛上传失败");
            }
            return new StoredFile(effectiveType, key);
        }
        if (CommonConstant.UPLOAD_TYPE_MINIO.equals(effectiveType)) {
            String url = MinioUtil.upload(new ByteArrayInputStream(bytes), relativePath);
            if (!StringUtils.hasText(url)) {
                throw new IllegalStateException("Minio上传失败");
            }
            return new StoredFile(effectiveType, url);
        }
        if (CommonConstant.UPLOAD_TYPE_OSS.equals(effectiveType)) {
            String url = OssBootUtil.upload(new ByteArrayInputStream(bytes), relativePath);
            if (!StringUtils.hasText(url)) {
                throw new IllegalStateException("OSS上传失败");
            }
            return new StoredFile(effectiveType, url);
        }

        File target = new File(uploadPath, relativePath.replace("/", File.separator));
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileCopyUtils.copy(bytes, target);
        return new StoredFile(CommonConstant.UPLOAD_TYPE_LOCAL, relativePath);
    }

    private void deleteStoredFile(String storageType, String path) {
        if (!StringUtils.hasText(path)) {
            return;
        }
        String effectiveType = StringUtils.hasText(storageType) ? storageType : detectStorageType(path);
        try {
            if (CommonConstant.UPLOAD_TYPE_QINIU.equals(effectiveType)) {
                qiniuUtil.deleteFileByKey(normalizeStoredPath(path, CommonConstant.UPLOAD_TYPE_QINIU));
                return;
            }
            if (CommonConstant.UPLOAD_TYPE_MINIO.equals(effectiveType)) {
                removeMinioFile(path);
                return;
            }
            if (CommonConstant.UPLOAD_TYPE_OSS.equals(effectiveType)) {
                if (path.startsWith("http")) {
                    OssBootUtil.deleteUrl(path);
                }
                return;
            }

            File file = new File(uploadPath, normalizeStoredPath(path, CommonConstant.UPLOAD_TYPE_LOCAL).replace("/", File.separator));
            if (file.exists() && !file.delete()) {
                log.warn("删除本地背包文件失败: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("删除背包文件失败: {}", path, e);
        }
    }

    private void removeMinioFile(String path) {
        String minioUrl = MinioUtil.getMinioUrl();
        if (!StringUtils.hasText(minioUrl) || !path.startsWith(minioUrl)) {
            return;
        }
        String fullPath = path.substring(minioUrl.length());
        if (fullPath.startsWith("/")) {
            fullPath = fullPath.substring(1);
        }
        int index = fullPath.indexOf('/');
        if (index <= 0 || index >= fullPath.length() - 1) {
            return;
        }
        String bucket = fullPath.substring(0, index);
        String objectName = fullPath.substring(index + 1);
        MinioUtil.removeObject(bucket, objectName);
    }

    private String buildRelativePath(String id, boolean thumbnail, String ext) {
        String suffix = thumbnail ? "_thumb" : "_body";
        return "scratch/backpack/" + id + suffix + ext;
    }

    private String buildAccessUrl(String storageType, String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        if (path.startsWith("http")) {
            return path;
        }
        if (CommonConstant.UPLOAD_TYPE_QINIU.equals(storageType)) {
            return trimSlash(QiniuConfig.domain) + "/" + trimLeadingSlash(path);
        }
        if (CommonConstant.UPLOAD_TYPE_LOCAL.equals(storageType)) {
            return trimSlash(staticDomain) + "/" + trimLeadingSlash(path);
        }
        return path;
    }

    private String normalizeStoredPath(String path, String storageType) {
        if (!StringUtils.hasText(path)) {
            return path;
        }
        if (CommonConstant.UPLOAD_TYPE_QINIU.equals(storageType)) {
            String domain = trimSlash(QiniuConfig.domain) + "/";
            return path.startsWith(domain) ? path.substring(domain.length()) : trimLeadingSlash(path);
        }
        if (CommonConstant.UPLOAD_TYPE_LOCAL.equals(storageType)) {
            String domain = trimSlash(staticDomain) + "/";
            return path.startsWith(domain) ? path.substring(domain.length()) : trimLeadingSlash(path);
        }
        return path;
    }

    private String detectStorageType(String... paths) {
        for (String path : paths) {
            if (!StringUtils.hasText(path)) {
                continue;
            }
            String qiniuDomain = trimSlash(QiniuConfig.domain);
            if (StringUtils.hasText(qiniuDomain) && path.startsWith(qiniuDomain + "/")) {
                return CommonConstant.UPLOAD_TYPE_QINIU;
            }
            String minioUrl = trimSlash(MinioUtil.getMinioUrl());
            if (StringUtils.hasText(minioUrl) && path.startsWith(minioUrl + "/")) {
                return CommonConstant.UPLOAD_TYPE_MINIO;
            }
            String ossDomain = trimSlash(OssBootUtil.getStaticDomain());
            if (StringUtils.hasText(ossDomain) && path.startsWith(ossDomain + "/")) {
                return CommonConstant.UPLOAD_TYPE_OSS;
            }
            if (!path.startsWith("http")) {
                return CommonConstant.UPLOAD_TYPE_LOCAL;
            }
        }
        return normalizeUploadType();
    }

    private String detectStorageType(String path) {
        return detectStorageType(new String[]{path});
    }

    private String normalizeUploadType() {
        String current = uploadType == null ? "" : uploadType.trim().toLowerCase(Locale.ROOT);
        if (CommonConstant.UPLOAD_TYPE_QINIU.equals(current)
                || CommonConstant.UPLOAD_TYPE_MINIO.equals(current)
                || CommonConstant.UPLOAD_TYPE_OSS.equals(current)) {
            return current;
        }
        return CommonConstant.UPLOAD_TYPE_LOCAL;
    }

    private String stripBase64Prefix(String value) {
        int commaIndex = value.indexOf(',');
        return commaIndex >= 0 ? value.substring(commaIndex + 1) : value;
    }

    private String resolveMime(String base64Value, String defaultMime) {
        if (StringUtils.hasText(base64Value) && base64Value.startsWith("data:")) {
            int semicolonIndex = base64Value.indexOf(';');
            if (semicolonIndex > 5) {
                return base64Value.substring(5, semicolonIndex);
            }
        }
        return defaultMime;
    }

    private String resolveExtension(String mime, boolean thumbnail, String type) {
        if (!StringUtils.hasText(mime)) {
            return thumbnail ? ".jpg" : defaultTypeExtension(type);
        }
        String mimeLower = mime.toLowerCase(Locale.ROOT);
        if (mimeLower.contains("png")) {
            return ".png";
        }
        if (mimeLower.contains("jpeg") || mimeLower.contains("jpg")) {
            return ".jpg";
        }
        if (mimeLower.contains("svg")) {
            return ".svg";
        }
        if (mimeLower.contains("gif")) {
            return ".gif";
        }
        if (mimeLower.contains("wav")) {
            return ".wav";
        }
        if (mimeLower.contains("mpeg") || mimeLower.contains("mp3")) {
            return ".mp3";
        }
        if (mimeLower.contains("ogg")) {
            return ".ogg";
        }
        if (mimeLower.contains("json")) {
            return ".json";
        }
        if (mimeLower.contains("zip")) {
            return ".zip";
        }
        if (mimeLower.contains("plain")) {
            return ".txt";
        }
        return thumbnail ? ".jpg" : defaultTypeExtension(type);
    }

    private String defaultTypeExtension(String type) {
        if (!StringUtils.hasText(type)) {
            return ".bin";
        }
        String lowerType = type.toLowerCase(Locale.ROOT);
        if (lowerType.contains("sound")) {
            return ".wav";
        }
        if (lowerType.contains("costume") || lowerType.contains("backdrop")) {
            return ".png";
        }
        if (lowerType.contains("sprite")) {
            return ".sprite3";
        }
        if (lowerType.contains("script")) {
            return ".json";
        }
        return ".bin";
    }

    private String trimSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String trimLeadingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private static class StoredFile {
        private final String storageType;
        private final String path;

        private StoredFile(String storageType, String path) {
            this.storageType = storageType;
            this.path = path;
        }
    }
}
