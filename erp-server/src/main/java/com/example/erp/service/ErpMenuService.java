package com.example.erp.service;

import com.example.erp.dto.MenuNode;
import com.example.erp.entity.ErpMenu;
import com.example.erp.mapper.ErpMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单查询服务：按 channelCode 过滤并组装成树。
 */
@Service
@RequiredArgsConstructor
public class ErpMenuService {

    private final ErpMenuMapper erpMenuMapper;

    public List<MenuNode> menuTree(final String channelCode) {
        final List<ErpMenu> menus = erpMenuMapper.selectVisibleByChannel(channelCode);
        final Map<Long, MenuNode> nodes = new LinkedHashMap<>();
        for (final ErpMenu menu : menus) {
            nodes.put(menu.getId(), new MenuNode(menu));
        }
        final List<MenuNode> roots = new ArrayList<>();
        for (final MenuNode node : nodes.values()) {
            final Long parentId = node.getParentId();
            if (parentId == null || parentId == 0L || !nodes.containsKey(parentId)) {
                roots.add(node);
            } else {
                nodes.get(parentId).getChildren().add(node);
            }
        }
        return roots;
    }
}