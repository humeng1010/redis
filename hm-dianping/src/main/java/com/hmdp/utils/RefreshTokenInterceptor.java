package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    //如果这个类不属于SpringBoot管理,那么需要通过构造器注入的方式,从注册拦截器地方获取到StringRedisTemplate这个对象
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //TODO 获取请求头中的token
//        HttpSession session = request.getSession();
        String token = request.getHeader("authorization");
        log.debug("前端请求头中的token信息: {}",token);
        //判断token是否为空白 null 空字符串："" 空格、全角空格、制表符、换行符，等不可见字符
        if (StrUtil.isBlank(token)){
            return true;
        }
        //TODO 基于token获取redis中的用户
//        Object user = session.getAttribute("user");
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash()
                .entries(RedisConstants.LOGIN_USER_KEY + token);
        //判断用户是否存在
        if (userMap.isEmpty()){
            return true;
        }
        //TODO 将查询到的Hash数据转为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        //存在,保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);
        //TODO 刷新token的有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
