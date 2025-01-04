package com.joseph.sharding.demo.group.commit;

import com.joseph.sharding.demo.entity.OrderInfo;
import com.joseph.sharding.demo.service.IOrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 组提交管理器
 *
 * @author Joseph
 * @since 2024/12/23
 */
@Slf4j
@Component
public class GroupManager {

    @Autowired
    private IOrderInfoService orderInfoService;

    /**
     * 组提交维护，和leader线程绑定
     */
    private static final ThreadLocal<GroupCommit> GROUP_KEY = new ThreadLocal<>();

    /**
     * 当前正在等待且未满的组
     */
    private volatile GroupCommit currentGroup ;

    /**
     * 组管理器Lock
     */
    private ReentrantLock lock = new ReentrantLock();


    /**
     * 入组等待提交。
     * 实现并行的组提交机制。leader线程会等待X毫秒或者第K个线程到达队列满，唤醒leader提交。
     * 当前组满后，新线程会作为leader开启新组等待，所以高并发下会有多个组同时满载顺序写入DB。兼顾了并发写入和聚合写入
     *
     * @param orderInfo
     * @return 组提交结果
     */
    public Boolean queueGroup(OrderInfo orderInfo) {
        if (null == orderInfo) {
            return false;
        }
        // 大部分线程挡在这里
        lock.lock();
        // 只有当前线程和leader能操作当前组
        if (null == currentGroup || currentGroup.isFull() || currentGroup.hasSubmitted()) {
            currentGroup = new GroupCommit(200, 5, orderInfoService);
        }
        try {
            // 和leader抢锁
            currentGroup.lock();

            if (currentGroup.isFull() || currentGroup.hasSubmitted()) {
                // 释放锁，让组内follower线程尽快唤醒
                currentGroup.unlock();
                currentGroup = new GroupCommit(200, 5, orderInfoService);
                currentGroup.lock();
            }
            GROUP_KEY.set(currentGroup);
            lock.unlock();

            GroupCommit localGroup = GROUP_KEY.get();
            boolean isFull = localGroup.queue(orderInfo);
            if (null == localGroup.getLeader()) {
                localGroup.setLeader();
                // 极端情况组内容量只有1个就提交
                if (isFull) {
                    localGroup.submit();
                    return localGroup.getCommitResult();
                }
                localGroup.leaderPending();
                localGroup.submit();
                localGroup.wakeUpFollowers();
            }
            else {
                if (isFull) {
                    localGroup.wakeUpLeader();
                }
                localGroup.followerPending();
            }
            return localGroup.getCommitResult();
        }
        finally {
            GROUP_KEY.get().unlock();
            GROUP_KEY.remove();
        }
    }
}
