package com.example.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import tk.mybatis.mapper.annotation.KeySql;

import java.time.LocalDateTime;

/**
 * 用户（t_user），仅暴露管理台需要的字段。
 */
@Getter
@Setter
@Table(name = "t_user")
public class CasUser {

    @Id
    @KeySql(useGeneratedKeys = true)
    private Long id;

    private String username;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "dept_id")
    private String deptId;

    private Integer enabled;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
