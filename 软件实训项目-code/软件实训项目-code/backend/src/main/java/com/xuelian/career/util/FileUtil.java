package com.xuelian.career.util;

import com.xuelian.career.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 文件工具类 - 负责文件格式校验、大小校验与存储
 * 支持 PDF (.pdf) 和 Word (.docx) 简历文件上传，限制 5MB
 */
@Slf4j
@Component
public class FileUtil {

    /** 允许的文件 MIME 类型 */
    private static final List<String> ALLOWED_TYPES = Arrays.asList(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    /** 允许的文件扩展名 */
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("pdf", "docx");

    /** 最大文件大小（5MB） */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    /**
     * 保存上传文件到本地存储
     * @param file 上传文件
     * @param subDir 子目录（如 resumes）
     * @return 文件相对路径
     */
    public String saveFile(MultipartFile file, String subDir) {
        // 格式校验
        validateFile(file);

        try {
            // 创建存储目录
            Path dirPath = Paths.get(uploadPath, subDir);
            Files.createDirectories(dirPath);

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String newFilename = UUID.randomUUID().toString() + "." + extension;

            // 保存文件
            Path targetPath = dirPath.resolve(newFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("文件保存成功: {}", targetPath);
            return subDir + "/" + newFilename;

        } catch (IOException e) {
            log.error("文件保存失败", e);
            throw new BusinessException(500, "文件保存失败，请稍后重试");
        }
    }

    /**
     * 校验文件格式和大小
     * @param file 上传文件
     */
    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }

        // 大小校验
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小不能超过 5MB");
        }

        // 格式校验（MIME 类型）
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException("仅支持 PDF 和 DOCX 格式的文件");
        }

        // 扩展名校验
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BusinessException("文件名不能为空");
        }
        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("仅支持 .pdf 和 .docx 格式的文件");
        }
    }

    /**
     * 删除文件
     * @param filePath 文件相对路径
     */
    public void deleteFile(String filePath) {
        try {
            Path path = Paths.get(uploadPath, filePath);
            Files.deleteIfExists(path);
            log.info("文件已删除: {}", path);
        } catch (IOException e) {
            log.warn("文件删除失败: {}", filePath);
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1);
        }
        return "";
    }
}
