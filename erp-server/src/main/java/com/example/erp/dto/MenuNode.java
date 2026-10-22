package com.example.erp.dto;

import com.example.erp.entity.ErpMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点。
 */
public class MenuNode {

    private final Long id;
    private final Long parentId;
    private final String menuName;
    private final String menuCode;
    private final String path;
    private final String icon;
    private final Integer sortOrder;
    private final String channelCode;
    private final List<MenuNode> children = new ArrayList<>();

    public MenuNode(final ErpMenu menu) {
        this.id = menu.getId();
        this.parentId = menu.getParentId();
        this.menuName = menu.getMenuName();
        this.menuCode = menu.getMenuCode();
        this.path = menu.getPath();
        this.icon = menu.getIcon();
        this.sortOrder = menu.getSortOrder();
        this.channelCode = menu.getChannelCode();
    }

    public Long getId() {
        return id;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getMenuName() {
        return menuName;
    }

    public String getMenuCode() {
        return menuCode;
    }

    public String getPath() {
        return path;
    }

    public String getIcon() {
        return icon;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public List<MenuNode> getChildren() {
        return children;
    }
}