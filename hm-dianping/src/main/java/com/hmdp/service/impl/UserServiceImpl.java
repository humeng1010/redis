package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Objects;

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
        session.setAttribute("code",code);
        //三分钟失效
//        session.setMaxInactiveInterval(600);
        //发送验证码,模拟一个,由于正常业务需要调用第三方服务
        log.debug("发送验证码成功,验证码{}",code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            //如果不符合,返回错误信息
            return Result.fail("手机号格式有误");
        }
        //提交手机号验证码,接收到封装到DTO中
        //校验验证码
        Object cacheCode = session.getAttribute("code");
        log.info("获取到session中的信息{}",cacheCode);

        if (!Objects.equals(cacheCode,loginForm.getCode())){
            return Result.fail("验证码错误,请重新输入");
        }
        //根据手机号查询用户
        User user = this.query().eq("phone", loginForm.getPhone()).one();
        if (Objects.isNull(user)){
            //用户不存在创建用户,保存到数据库
            user = createUserWithPhone(loginForm.getPhone());
        }

        //保存用户到session中,注意需要取掉敏感信息
        /*spring提供的方法
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO,UserDTO.class);
        */
        session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));

        return Result.ok();
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
