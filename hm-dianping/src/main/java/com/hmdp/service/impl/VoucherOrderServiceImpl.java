package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;


    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //尚未开始
            return Result.fail("秒杀尚未开始");
        }
        //判断是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        //判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        //使用用户的id作为锁,由于toString方法底层还是调用了new String() 每次还是产生了一个新的对象 所以调用intern()方法返回在字符串常量池中已经存在的字符串,就是同一个字符串对象了
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            //获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //由于事务是整个方法的,当方法执行完毕的时候才会提交事务,而锁在这里只锁住了部分,在并发业务中还是有可能出现并发问题的,所以在这个地方加锁还是不合理的
        //使用用户的id作为锁,由于toString方法底层还是调用了new String() 每次还是产生了一个新的对象 所以调用intern()方法返回在字符串常量池中已经存在的字符串,就是同一个字符串对象了
//        synchronized (userId.toString().intern()) {
            //一人一单
            //查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            //判断是否存在
            if (count>0){
                //用户已经购买过
                return Result.fail("不能重复抢购");
            }

            //扣减库存 使用乐观锁的CAS法,防止超卖
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")//set stock = stock - 1
    //                .eq("voucher_id", voucherId).eq("stock",voucher.getStock())//where voucher_id = #{voucherId} and stock = #{stock}
                    .eq("voucher_id", voucherId).gt("stock",0)//where voucher_id = #{voucherId} and stock > 0 //防止失败率过高
                    .update();
            if (!success){
                return Result.fail("库存不足");
            }

            //创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            //订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            //用户id
            voucherOrder.setUserId(userId);
            //代金券id
            voucherOrder.setVoucherId(voucherId);

            //保存优惠券订单
            save(voucherOrder);

            //返回订单id
            return Result.ok(orderId);
//        }
    }
}
