package com.joseph.sharding.demo.controller;

import com.joseph.sharding.demo.entity.OrderInfo;
import com.joseph.sharding.demo.group.commit.GroupManager;
import com.joseph.sharding.demo.service.IOrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.NumberUtils;
import org.springframework.util.SimpleIdGenerator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @author Joseph
 * @since 2024/12/24
 */
@Slf4j
@RestController
@RequestMapping("orderInfo")
public class OrderInfoController {

    @Autowired
    private GroupManager groupManager;

    @Autowired
    private IOrderInfoService orderInfoService;


    @PostMapping("/submit")
    public Boolean submit() {
        long tid = Thread.currentThread().getId();
        log.info("threadId={}", tid);
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderNo(UUID.randomUUID().toString());
        orderInfo.setAddressId(123321123321123L);
        orderInfo.setMerchantId(123321123321123L);
        orderInfo.setUserId(123321123321123L);
        orderInfo.setOrderAmount(BigDecimal.valueOf(123123L));
        return groupManager.queueGroup(orderInfo);
    }

    @PostMapping("/submit2")
    public Boolean submit2() {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderNo(UUID.randomUUID().toString());
        orderInfo.setAddressId(123321123321123L);
        orderInfo.setMerchantId(123321123321123L);
        orderInfo.setUserId(123321123321123L);
        orderInfo.setOrderAmount(BigDecimal.valueOf(123123L));
        return orderInfoService.save(orderInfo);
    }
}
