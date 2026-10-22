package com.example.erp.mapper;

import com.example.erp.entity.ErpMenu;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

public interface ErpMenuMapper extends Mapper<ErpMenu> {

    @Select("""
            SELECT id,
                   parent_id    AS parentId,
                   menu_name    AS menuName,
                   menu_code    AS menuCode,
                   path,
                   icon,
                   sort_order   AS sortOrder,
                   channel_code AS channelCode,
                   status,
                   created_at   AS createdAt
            FROM erp_menu
            WHERE status = 1
              AND (channel_code IS NULL OR channel_code = #{channelCode})
            ORDER BY sort_order, id
            """)
    List<ErpMenu> selectVisibleByChannel(@Param("channelCode") String channelCode);
}