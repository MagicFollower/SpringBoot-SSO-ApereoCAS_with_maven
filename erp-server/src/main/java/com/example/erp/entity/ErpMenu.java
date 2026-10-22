package com.example.erp.entity;

import lombok.Getter;
import lombok.Setter;
import tk.mybatis.mapper.annotation.KeySql;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * ERP 菜单。
 */
@Getter
@Setter
@Table(name = "erp_menu")
public class ErpMenu {

    @Id
    @KeySql(useGeneratedKeys = true)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "menu_name")
    private String menuName;

    @Column(name = "menu_code")
    private String menuCode;

    private String path;

    private String icon;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "channel_code")
    private String channelCode;

    private Integer status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}