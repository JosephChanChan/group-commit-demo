# group-commit-demo
# 对于高并发写优化TPS的简单易用方案
> a good solution and easy for high concurrent write scenario

# 高并发写优化理论
对于高并发的读QPS优化手段较多，最经济简单的方式是上缓存。但是对于高并发写TPS该如何提升？业界常用的有分库分表、异步写入等技术手段。但是分库分表对于业务的改造十分巨大，涉及迁移数据的麻烦工作，不会作为常用的优化手段。异步写入到时经常在实际工作中使用，但是也不适合所有场景，特别对于带有事务的写入请求，带事务的写入请求通常是需要同步告知用户处理结果，所以不适用异步处理。

我们都知道批处理会比单条处理快很多，只需要发起一次网络请求，在网络层面节省了N次TCP连接获取和发送数据的步骤。实际我测试过，通过shark抓包，发现建立一条TCP连接可能需要耗费10ms~50ms左右。如果是跨洲际的TCP连接更久，可能耗费几百毫秒。单是节省的多次TCP连接就能节省不少时间，其次还有程序代码的循环执行时间。所以将多个写请求聚合成一个合适大小的批量写请求，一次性将数据发送给服务器进行批量写入是最高效的。

# 组提交的优化目标
组提交的***第一目的不是提高数据库写入的TPS，而是减缓高并发写入对数据库的冲击***。比如在我的压测例子中，一共70万的写入请求，如果在单条写入情况下共向数据库发起70万次写入，耗费70万条链接和数据库线程。如果用组提交设置组大小为20，等待时间1毫秒。一共向数据库发起3.5万次写入，请求次数和耗费的连接数、线程数都大大降低，理论上是降低到20倍左右的。
在降低了数据库写入压力后，其次目标才是提高写入TPS。按照我实践经验，只要参数设置合适，组提交的TPS会比单条写入TPS高20%~70%左右。

# 后台技术架构
因为我们工作中大多数使用的是Tomcat容器，目前Tomcat的IO处理模型是Reactor+线程池的模式。  

<img src="https://github.com/user-attachments/assets/0683a10a-f8a5-46d6-987f-94b924db45e3" width="600"/>

在整个系统架构层面，组提交影响性能的有两个参数：组大小和等待时间。组大小就是在组内挂起等待的线程数，等待时间是Leader主动等待的毫秒数。组大小直接影响到剩余可工作的线程数，Tomcat线程数量默认200，通常我们根据业务场景和硬件资源调整，线程数量也就几百左右。***如果组太大同时等待时间太久！！直接把Tomcat所有线程都挂起了这时服务器就假死了，所以对组大小的设置建议通过压测来确定，按照下面的压测经验一般建议设置为Tomcat线程数量的1/20~1/4。需要确保有足够线程可以服务其它请求。*** 

通过压测经验得知，尽量让请求RT降低才会得到更高TPS。压测时在保证TPS不降低情况下，将组大小、等待时间降低，同时观察每一次组提交时实际的组内数据条数，让实际提交的数据条数尽量接近组大小。我试过20/1ms和200/5ms的组大小设置。

使用组提交组件后，宏观系统架构层面K8S Pod和Tomcat线程池以及本地事务之间的关系。可以看到我们的组提交组件主要是减缓高并发的单条数据写入对数据库的冲击。组和组之间不相互依赖，不相互阻塞，每个组都是独立的Leader进行领导，等待组内成员到齐后或等待时间到达后主动发起数据库批量顺序写入。这样能最大化组提交并发性能，同时保护数据库减少写入请求量。

![组提交架构图](https://github.com/user-attachments/assets/cedf1065-8f13-47f6-8fc0-c5f35c37c04a)


# 组件架构
我们基于以上的理论分析，可以得出如果我们在高并发写入的时候能够模仿MySQL的组提交，实现一个主动等待和被动唤醒提交的组提交机制，将多个写入请求合成一个请求发送给MySQL就能提高写入性能。

总结MySQL的组提交机制原理：

- 第一个到达的线程开启新组作为本组Leader领导本组的数据提交
- Leader等待指定X毫秒时间，时间到后主动发起提交
- 第K个线程到达，若发现本组负载满了唤醒Leader进行本组提交
- 组与组之间互不阻塞，单位时间内可能有多个组并发提交

基于以上原理，我设计了两个类：GroupManager组管理器、GroupCommit组提交对象。GroupManager负责接收外部线程提交的数据，然后放到当前组里。并且实现整个组提交的流程。GroupCommit是一个组的具象化对象，提供一个组的入队，提交数据，挂起等待，唤醒Leader等基础方法，给GroupManager调用以实现组提交机制。

为了避免高并发时多线程竞相进入组内，导致组错乱，使用了两把锁解决。大部分线程都会被挡在第一关，每次只会放一个线程进到临界区尝试入组。入组之前要先获得当前组的锁，为什么要第二把锁？因为Leader会主动醒来提交本组的数据队列，所以提交时要确保所有资源都是排他的，需要组内锁来保证。入组的线程抢到组内锁之后就代表可以安全入组，此时有三种情况：

- 如果此时入组前发现组已经满了就开一个新组自己当Leader并唤醒当前组的Leader让它赶快提交
- 如果入组后发现组满了，唤醒当前组Leader让它赶快提交，自己则挂起等待提交后唤醒
- 入组后发现还未满，挂起自己等待唤醒

线程在获取到组内锁后都会立即释放GroupManager的锁，目的是让后续线程如果发现当前组满了，就立即开新组提交，提高效率。

![组提交组件图](https://github.com/user-attachments/assets/89f45484-26e5-44bd-9865-743bb1a1e36d)

# 注意事项
## 组提交不需要开启事务

Spring的@Transactional注解工作原理是需要从连接池获取一个连接，开启事务。通常数据库连接池设置的最大连接数在20~500之间。***如果在事务方法内用组提交方式，受限于数据库连接数限制，会大大降低组提交的TPS！***
并且本地事务内提交的数据，一旦入了组，***通过Leader提交的组数据作为一个整体传递给MySQL，MySQL会将这一批数据作为原子数据自动开启事务***。

而这一次组提交成功之后，事务也已提交，组数据已经落盘。Follower的本地事务无法再对组内的提交数据回滚！

适用场景：本地消息表、流水日志等需要先写入数据，数据带有状态可供检查的。万一组提交后，本地事务要回滚，产生了脏数据，还可以通过数据的状态字段check是否需要进行下一步业务。

# Jmeter压测报告
## 环境介绍

- Mac OS M2 10核16G，SSD
- MySQL 8.0
- JDK8u221
- SpringBoot，Tomcat线程池400
- Druid数据库连接池 40连接数
- Jmeter 5.3，700线程并发，循环1000，共70万请求
- JVM参数设置

> -XX:-ClassUnloadingWithConcurrentMark -Xms4g -Xmx4g -Xmn3g -XX:G1HeapRegionSize=4m -XX:InitiatingHeapOccupancyPercent=30 -XX:MaxGCPauseMillis=200 -XX:MaxMetaspaceSize=268435456 -XX:MetaspaceSize=268435456 -XX:ParallelGCThreads=10 -XX:+ParallelRefProcEnabled -XX:-ReduceInitialCardMarks -XX:+UseCompressedClassPointers -XX:+UseCompressedOops -XX:+UseG1GC

MySQL没有经过调优都是默认的参数。MySQL和应用服务还有Jmeter都是在Mac上运行的。对比两种测试用例：1.使用组提交组件 2.单条数据写入。
```
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
```

经过反复实验以及调整组提交的组大小、等待时间参数，得出组大小200，等待时间5ms，得出的TPS是比较好的。TPS达到近8800。接口错误率几乎没有。每个业务场景不一样，组大小和等待时间需要自己通过压测决定。
![image](https://github.com/user-attachments/assets/3338617d-5c0d-44ea-bc6a-6c69f206e302)

单提交（每次请求提交一次）所有配置和环境一致的情况下。并发700，循环1000次，70万请求。TPS在5200。错误率0
![image](https://github.com/user-attachments/assets/01407289-b84a-46cb-b5cb-01cd806f60e7)

可以看出***组提交比单提交TPS高出68%左右，优化比较明显***。如果能针对组大小和等待时间继续调整优化，可能TPS会更高。RT上平均时间比但提交快了1倍，但是P99、P95、P90都比单提交要慢1倍。





