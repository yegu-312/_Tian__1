package com.example.fished.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fished.entity.SysUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 删除账号使用物理删除，避免逻辑删除后用户名仍被唯一索引占用。 */
    @Delete("DELETE FROM sys_user WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
