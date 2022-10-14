package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //1.获取当前登陆的用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        //2.根据isFollow判断是关注还是取关
        //isFollow为true为关注
        String key = "follows:"+userId;

        if (isFollow){
            //关注,新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = this.save(follow);
            if (isSuccess){
                //如果关注成功那么把信息存放到redis的set集合中 实现共通关注功能 sadd userId followerUserId
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else {
            //取关,删除 delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean isSuccess = this.remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if (isSuccess) {
                //把关注的用户id从redis集合中移除
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        //1.获取当前登陆的用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        //2.查询是否关注 select count(*) from tb_follow where user_id = ? and follow_user_id = ?
        Integer count = this.query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    /**
     * 共通关注
     * @param id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        //1.获取当前登陆用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        String key = "follows:"+userId;
        //2.求交集
        String key2 = "follows:"+id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect==null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //3.解析id集合
        List<Long> ids = intersect.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        //4.查询用户
        List<User> users = userService.listByIds(ids);
        List<UserDTO> userDTO = users.stream()
                .map(u -> BeanUtil.copyProperties(u, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTO);
    }
}
