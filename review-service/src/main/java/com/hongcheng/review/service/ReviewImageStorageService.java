package com.hongcheng.review.service;

import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.review.dto.ReviewImageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 评价晒图本地存储：校验真实图片格式与大小，使用随机文件名落盘。
 * <p>
 * 文件目录通过 hc.upload.review-dir 配置，默认为运行目录下 uploads/review；
 * 对外地址统一为 /api/review/uploads/yyyyMMdd/{uuid}.{ext}，经网关静态映射读取。
 */
@Slf4j
@Service
public class ReviewImageStorageService {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final String URL_PREFIX = "/api/review/uploads/";
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Path root;

    public ReviewImageStorageService(
            @Value("${hc.upload.review-dir:./uploads/review}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public ReviewImageVO store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "单张图片不能超过 5MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片读取失败，请重试");
        }
        String extension = detectExtension(bytes);
        if (extension == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持 JPG、PNG、WEBP、GIF 图片");
        }
        String dateDir = LocalDate.now().format(DATE_DIR);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path targetDir = root.resolve(dateDir).normalize();
        Path target = targetDir.resolve(filename).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "非法的文件路径");
        }
        try {
            Files.createDirectories(targetDir);
            Files.copy(new java.io.ByteArrayInputStream(bytes), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("评价图片保存失败 dir={}, filename={}", targetDir, filename, e);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "图片保存失败，请稍后重试");
        }
        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : filename;
        log.info("评价图片上传成功 filename={}, size={}, originalName={}", filename, bytes.length, originalName);
        return new ReviewImageVO(URL_PREFIX + dateDir + "/" + filename, originalName, bytes.length);
    }

    /**
     * 通过文件头识别真实图片类型，避免只改扩展名绕过校验。
     */
    private String detectExtension(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return "png";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        if (bytes.length >= 6) {
            String head = new String(bytes, 0, 6, java.nio.charset.StandardCharsets.US_ASCII)
                    .toUpperCase(Locale.ROOT);
            if ("GIF87A".equals(head) || "GIF89A".equals(head)) {
                return "gif";
            }
        }
        return null;
    }
}
