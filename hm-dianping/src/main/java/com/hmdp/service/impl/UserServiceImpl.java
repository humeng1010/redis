package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //如果不符合,返回错误信息
            return Result.fail("手机号格式有误");
        }
        //生成验证码 引入的工具类hutool
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到session
//        session.setAttribute("code",code);

        //TODO 保存到Redis中 以手机号作为key保证唯一性 验证码作为值 并设置过期时间
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        ops.set(RedisConstants.LOGIN_CODE_KEY +phone,
                code,
                RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码,模拟一个,由于正常业务需要调用第三方服务
        log.debug("发送验证码成功,验证码{}",code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //如果不符合,返回错误信息
            return Result.fail("手机号格式有误");
        }
        //提交手机号验证码,接收到封装到DTO中
        //校验验证码
//        Object cacheCode = session.getAttribute("code");
//        log.info("获取到session中的信息{}",cacheCode);
        //TODO 从Redis中获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        log.info("从Redis中获取的code: {}",cacheCode);

        if (!Objects.equals(cacheCode,loginForm.getCode())){
            return Result.fail("验证码错误,请重新输入");
        }
        //根据手机号查询用户
        User user = this.query().eq("phone", phone).one();
        if (Objects.isNull(user)){
            //用户不存在创建用户,保存到数据库
            user = createUserWithPhone(phone);
        }
        //TODO 7.保存用户信息到redis中
        //TODO 7.1随机生成token,作为登陆令牌
        String token = UUID.randomUUID().toString(true);
        //TODO 7.2将User对象转为Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        //这里需要做处理,id是Long类型的,而StringRedisTemplate接收的key都是String类型的,会报类型转换错误
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)//忽略null值
                        .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));//设置字段值
        log.debug("map:{}",userMap.toString());
        //TODO 7.3存储
        //保存用户到session中,注意需要取掉敏感信息
//        session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));
        HashOperations<String, Object, Object> ops = stringRedisTemplate.opsForHash();
        String tokenKey = LOGIN_USER_KEY + token;
        ops.putAll(tokenKey, userMap);

        //防止用户过多导致Redis中数据量过大
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);

        //TODO 8.返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX +RandomUtil.randomString(10));
        //保存用户
        this.save(user);
        return user;
    }
}
