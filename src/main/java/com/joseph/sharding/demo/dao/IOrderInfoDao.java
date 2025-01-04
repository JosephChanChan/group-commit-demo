package com.joseph.sharding.demo.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.joseph.sharding.demo.entity.OrderInfo;

import java.util.List;

/**
 * @author Joseph
 * @since 2022/2/24 11:15 AM
 */
public interface IOrderInfoDao extends BaseMapper<OrderInfo> {

    List<Object> joinSelect();
}
