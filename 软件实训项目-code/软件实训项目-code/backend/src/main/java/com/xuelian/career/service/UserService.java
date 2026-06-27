package com.xuelian.career.service;

import com.xuelian.career.entity.User;

/**
 * 用户服务接口 - 提供用户查询和基本操作
 */
public interface UserService {

    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 用户实体，不存在返回 null
     */
    User getByUsername(String username);

    /**
     * 创建新用户
     * @param user 用户实体
     * @return 创建后的用户实体
     */
    User createUser(User user);

    /**
     * 根据用户ID查询
     * @param userId 用户ID
     * @return 用户实体
     */
    User getById(Long userId);

    /**
     * 检查用户名是否已存在
     * @param username 用户名
     * @return true 存在 / false 不存在
     */
    boolean isUsernameExists(String username);
}
