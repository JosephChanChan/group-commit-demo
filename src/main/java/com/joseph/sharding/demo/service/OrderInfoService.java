package com.joseph.sharding.demo.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.joseph.sharding.demo.dao.IOrderInfoDao;
import com.joseph.sharding.demo.entity.OrderInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author Joseph
 * @since 2022/2/24 11:11 AM
 */
@Service
@Slf4j
public class OrderInfoService extends ServiceImpl<IOrderInfoDao, OrderInfo> implements IOrderInfoService {

    @Autowired
    private IOrderInfoDao orderInfoDao;

    @Transactional
    /*@ShardingTransactionType(TransactionType.XA)*/
    public void distributedTransaction() {
        OrderInfo a = new OrderInfo();
        a.setUserId(8L);
        a.setOrderNo("9");
        a.setAddressId(123L);
        a.setMerchantId(321L);
        a.setOrderAmount(BigDecimal.ONE);
        save(a);

        OrderInfo b = new OrderInfo();
        b.setUserId(27L);
        b.setOrderNo("12");
        b.setAddressId(123L);
        b.setMerchantId(321L);
        b.setOrderAmount(BigDecimal.ONE);
        save(b);
    }

    @Override
    public void joinSelect() {
        List<Object> objects = orderInfoDao.joinSelect();
        System.out.println(objects);
    }


}
