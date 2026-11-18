package com.pandago.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pandago.auth.service.UserAvatarService;
import com.pandago.auth.vo.AvatarUploadVO;
import com.pandago.common.api.ResultCode;
import com.pandago.common.config.UploadProperties;
import com.pandago.common.exception.BusinessException;
import com.pandago.common.util.UploadedImageValidator;
import com.pandago.security.SecurityUtils;
import com.pandago.user.entity.User;
import com.pandago.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 头像上传实现。
 *
 * <p>安全要点：
 * <ol>
 *   <li>用户 ID 只从 SecurityContext 取，接口不接受 userId，不能改他人头像；</li>
 *   <li>Service 做真实校验：扩展名、Content-Type、魔数、大小，前端校验只是体验；</li>
 *   <li>存储文件名由 UUID 生成，不使用原始文件名，天然防止路径穿越和重名覆盖；</li>
 *   <li>数据库只保存 /uploads/avatars/xxx.webp 相对路径，不保存文件二进制或磁盘绝对路径；</li>
 *   <li>写库失败时删除刚落盘的文件，避免产生无人认领的孤儿文件。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAvatarServiceImpl implements UserAvatarService {

    private final UploadedImageValidator imageValidator;
    private final UploadProperties uploadProperties;
    private final UserMapper userMapper;

    @Override
    public AvatarUploadVO upload(MultipartFile file) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        User current = userMapper.selectById(userId);
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或已注销");
        }

        String extension = imageValidator.validate(file);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path avatarDir = uploadProperties.avatarDirPath().normalize();
        Path target = avatarDir.resolve(filename).normalize();
        // 防御性检查：生成的文件必须仍在头像目录内
        if (!target.startsWith(avatarDir)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "头像保存路径不合法");
        }

        try {
            Files.createDirectories(avatarDir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("头像文件保存失败, userId={}", userId, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "头像保存失败，请稍后重试");
        }

        String avatarUrl = "/uploads/avatars/" + filename;
        try {
            LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(User::getId, userId).set(User::getAvatar, avatarUrl);
            userMapper.update(null, wrapper);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                log.warn("清理失败头像文件失败: {}", target);
            }
            throw e;
        }

        log.info("用户 {} 更新头像: {}", userId, avatarUrl);
        return new AvatarUploadVO(avatarUrl);
    }
}
