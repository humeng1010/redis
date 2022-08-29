package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Autowired
    private LoginInterceptor loginInterceptor;
    @Autowired
    private RefreshTokenInterceptor refreshTokenInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //登陆拦截器
        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns(
                        "/user/code",           //放行验证码请求
                        "/user/login",          //放行登陆请求
                        "/blog/hot",            //放行有关博客热点的请求
                        "/shop/**",             //放行有关店铺的所有请求
                        "/shop-type/**",         //放行有关店铺类型的所有请求
                        "/upload/**",           //方便测试,放行上传
                        "/voucher/**"           //方便测试,放行优惠券
                ).order(1);//order 执行先后 值小先执行
        //token刷新拦截器
        registry.addInterceptor(refreshTokenInterceptor).addPathPatterns("/**").order(0);//拦截所有请求
    }
}
