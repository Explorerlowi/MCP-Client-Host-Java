package com.mcp.host.mapper;

import com.mcp.host.domain.Agent;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Agent数据访问层接口
 * 
 * @author cs
 */
@Mapper
public interface AgentMapper {
    
    /**
     * 根据主键查询Agent
     * 
     * @param id Agent主键
     * @return Agent对象
     */
    Agent selectAgentById(Long id);
    
    /**
     * 查询Agent列表
     * 
     * @param agent Agent查询条件
     * @return Agent列表
     */
    List<Agent> selectAgentList(Agent agent);
    
    /**
     * 查询所有启用聊天功能的Agent列表
     * 
     * @return 启用聊天功能的Agent列表
     */
    List<Agent> selectEnabledAgentList();
    
    /**
     * 新增Agent
     * 
     * @param agent Agent对象
     * @return 影响行数
     */
    int insertAgent(Agent agent);
    
    /**
     * 修改Agent
     * 
     * @param agent Agent对象
     * @return 影响行数
     */
    int updateAgent(Agent agent);
    
    /**
     * 删除Agent
     * 
     * @param id Agent主键
     * @return 影响行数
     */
    int deleteAgentById(Long id);
    
    /**
     * 批量删除Agent
     * 
     * @param ids Agent主键数组
     * @return 影响行数
     */
    int deleteAgentByIds(Long[] ids);
} 