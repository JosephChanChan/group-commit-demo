package com.joseph.sharding.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @author Joseph
 * @since 2022/2/23 11:39 PM
 */
@Data
@TableName("order_info")
public class OrderInfo {

    /*
          `id` bigint(32) NOT NULL,
  `order_no` varchar(32) NOT NULL COMMENT '订单号',
  `order_amount` decimal(8,2) NOT NULL COMMENT '订单金额',
  `merchant_id` bigint(32) NOT NULL COMMENT '商户ID',
  `user_id` bigint(32) NOT NULL COMMENT '用户ID',
  `address_id` bigint(32) NOT NULL COMMENT '收货地址ID',
     */

    /**
     * 因为shardingjdbc的煞笔策略，根据insert语句是否包含要生成的主键名决定是否调用keyGenerator生成主键值
     * 所以让mybatis plus不生成带id列名的insert语句，在shardingjdbc处生成雪花算法id，改写sql插入
     * 但是这样写有风险？
     * update? select 的where会不会受影响?
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal orderAmount;
    private Long merchantId;
    private Long addressId;
}
