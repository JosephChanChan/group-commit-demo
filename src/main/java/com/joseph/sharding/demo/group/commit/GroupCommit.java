package com.joseph.sharding.demo.group.commit;

import com.joseph.sharding.demo.entity.OrderInfo;
import com.joseph.sharding.demo.service.IOrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Joseph
 * @since 2024/12/23
 */
@Slf4j
public class GroupCommit {

    private final int groupSize ;

    private final long waitingMillis ;

    private final List<OrderInfo> dataList ;

    private volatile Thread leader ;

    private ReentrantLock lock = new ReentrantLock();

    private Condition leaderCondition = lock.newCondition();
    private Condition followerCondition = lock.newCondition();

    /**
     * 本组提交结果，真实场景这里是对象，包含异常信息等
     * 组内的其他线程应根据本组的提交结果决定其本地事务提交或回滚
     */
    private volatile Boolean commitResult ;

    private final IOrderInfoService orderInfoService;

    /**
     * 构建组提交
     *
     * @param groupSize 组大小。组越大批量写入效率收益越高，但会阻塞线程，要预留一定线程处理请求
     * @param waitingMillis 等待时间。等待越久聚合的数据越多，但会增加接口响应时间，应选折中的时间
     * @param orderInfoService 回调的批量写入方法，可以是Service或Dao对象
     */
    public GroupCommit(int groupSize, long waitingMillis, IOrderInfoService orderInfoService) {
        this.groupSize = groupSize;
        this.waitingMillis = waitingMillis;
        this.orderInfoService = orderInfoService;
        this.dataList = new ArrayList<>(groupSize);
    }


    public void lock() {
        lock.lock();
    }

    public void unlock() {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    public boolean isFull() {
        return dataList.size() == groupSize;
    }

    public boolean hasSubmitted() {
        return null != commitResult;
    }

    public Thread getLeader() {
        return leader;
    }

    public void setLeader() {
        this.leader = Thread.currentThread();
    }

    /**
     * 入组等待提交，等待X毫秒提交或第K个线程达到队列满，唤醒leader提交
     * 队列满后，要开新组给后续请求排队
     *
     * @param orderInfo
     */
    public boolean queue(OrderInfo orderInfo) {
        dataList.add(orderInfo);
        return isFull();
    }

    /**
     * 组提交方法。Service对象应该自实现回调的顺序写入方法
     */
    public void submit() {
        if (CollectionUtils.isEmpty(dataList)) {
            return;
        }
        try {
            log.info("dataList size={}", dataList.size());
            // 回调Service对象顺序写入数据，这些数据应作为整体提交给DB并拿到一个结果
            boolean res = orderInfoService.saveBatch(dataList);
            // 这里可以做成一个结果对象
            this.commitResult = res;
        }
        catch (Exception e) {
            log.error("submit error", e);
            // 记录异常到结果对象中
            this.commitResult = false;
        }
    }

    public Boolean getCommitResult() {
        return commitResult;
    }

    public void leaderPending() {
        Date deadline = new Date(System.currentTimeMillis() + waitingMillis);
        try {
            while (leaderCondition.awaitUntil(deadline)) {
            }
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void followerPending() {
        try {
            followerCondition.await();
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void wakeUpLeader() {
        leaderCondition.signal();
    }

    public void wakeUpFollowers() {
        followerCondition.signalAll();
    }


}
