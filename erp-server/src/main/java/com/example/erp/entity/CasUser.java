package com.example.erp.entity;

import lombok.Getter;
import lombok.Setter;
import tk.mybatis.mapper.annotation.KeySql;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * CAS 用户（复用 cas_sso.t_user，作为 ERP 统一用户来源）。
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