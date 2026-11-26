package com.hongcheng.review.service;

import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.review.dto.ReviewImageVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewImageStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("上传 JPG - 随机文件名落盘并返回公开地址")
    void store_whenJpeg_returnsPublicUrlAndSavesFile() {
        ReviewImageStorageService service = new ReviewImageStorageService(tempDir.toString());
        byte[] content = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
        MockMultipartFile file = new MockMultipartFile("file", "car.jpg", "image/jpeg", content);

        ReviewImageVO vo = service.store(file);

        assertThat(vo.getUrl()).startsWith("/api/review/uploads/").endsWith(".jpg");
        assertThat(vo.getName()).isEqualTo("car.jpg");
        assertThat(vo.getSize()).isEqualTo(content.length);
        assertThat(vo.getUrl()).doesNotContain("car");
        String relative = vo.getUrl().substring("/api/review/uploads/".length());
        assertThat(Files.exists(tempDir.resolve(relative))).isTrue();
    }

    @Test
    @DisplayName("上传 PNG - 依据文件头识别真实格式")
    void store_whenPng_detectsByMagicBytes() {
        ReviewImageStorageService service = new ReviewImageStorageService(tempDir.toString());
        byte[] content = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 9};
        MockMultipartFile file = new MockMultipartFile("file", "shot.txt", "text/plain", content);

        ReviewImageVO vo = service.store(file);

        assertThat(vo.getUrl()).endsWith(".png");
    }

    @Test
    @DisplayName("上传非图片 - 拒绝并提示支持格式")
    void store_whenNotImage_throwsBadRequest() {
        ReviewImageStorageService service = new ReviewImageStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅支持");
    }

    @Test
    @DisplayName("上传超过 5MB - 拒绝")
    void store_whenTooLarge_throwsBadRequest() {
        ReviewImageStorageService service = new ReviewImageStorageService(tempDir.toString());
        byte[] content = new byte[5 * 1024 * 1024 + 1];
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", content);

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    @DisplayName("空文件 - 拒绝")
    void store_whenEmpty_throwsBadRequest() {
        ReviewImageStorageService service = new ReviewImageStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择");
    }
}
