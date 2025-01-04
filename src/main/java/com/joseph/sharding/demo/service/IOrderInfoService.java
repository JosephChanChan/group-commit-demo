package com.joseph.sharding.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.joseph.sharding.demo.entity.OrderInfo;

/**
 * @author Joseph
 * @since 2022/2/24 11:12 AM
 */
public interface IOrderInfoService extends IService<OrderInfo> {

    void distributedTransaction();

    void joinSelect();

}
