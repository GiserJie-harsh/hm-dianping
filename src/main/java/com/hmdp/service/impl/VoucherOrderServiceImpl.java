package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Autowired
    private SeckillVoucherServiceImpl seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;
    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("活动未开始！！");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("活动已结束！！");
        }
        if(voucher.getStock() < 1){
            return Result.fail("库存不足！！");
        }
        Long userId = UserHolder.getUser().getId();
//        synchronized (userId.toString().intern()){
//            return createVoucherOrder(voucherId);
//        }
        /*
        * createVoucherOrder(voucherId);相当于this.createVoucherOrder(voucherId);
        * 也就是使用的是当前的VoucherOrderServiceImpl对象，而事务生效的前提条件是用代理对象进行调用才可以
        * 所以我们这样写*/
        synchronized (userId.toString().intern()){
            //获取VoucherOrderServiceImpl（IVoucherOrderService）的代理对象，用代理对象去调用包含事务的方法，事务才能生效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }

    }

    @Transactional
    public  Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
//        synchronized (userId.toString().intern()) {
        //如果在这里加锁，那么由于先释放锁再提交事务的顺序，当我们释放完锁到事务还未提交这一段时间再有
        //新的线程访问此处依然会造成多线程安全问题（因为数据库还未更新）
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            //判断数据库是否存在当前用户订单
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }
            //更新数据库秒杀券的库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足！");
            }
            //创建用户订单并写入数据库
            VoucherOrder order = new VoucherOrder();
            long id = redisIdWorker.nextId("order");
            UserDTO user = UserHolder.getUser();
            order.setVoucherId(voucherId);
            order.setId(id);
            order.setUserId(user.getId());
            save(order);
            //返回订单id
            return Result.ok(id);
//        }
    }
}
