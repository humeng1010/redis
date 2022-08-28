package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Autowired
    private LoginInterceptor loginInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns(
                        "/user/code",           //放行验证码请求
                        "/user/login",          //放行登陆请求
                        "/blog/hot",            //放行有关博客热点的请求
                        "/shop/**",             //放行有关店铺的所有请求
                        "/shop-type/**",         //放行有关店铺的所有请求
                        "/upload/**",           //方便测试,放行上传
                        "/voucher/**"           //方便测试,放行优惠券
                );
    }
}
