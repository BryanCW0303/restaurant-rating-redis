package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOW_KEY;


@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService  userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // get userId
        Long userId = UserHolder.getUser().getId();
        //
        if (isFollow) {
            // if follows, save data into tb_follow
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                // save follow_user_id into set of user_id
                String key = FOLLOW_KEY + userId;
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            // if not follow, remove data from tb_follow
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));
            if (isSuccess) {
                // remove follow_user_id from set of user_id
                String key = FOLLOW_KEY + userId;
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        // get userId
        Long userId = UserHolder.getUser().getId();
        // check whether follow or not
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count);
    }

    @Override
    public Result commonFollows(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        String key1 = FOLLOW_KEY + userId;
        String key2 = FOLLOW_KEY + followUserId;
        // get the intersection of their follow list
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok();
        }
        // map into long
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // map into userDTO class
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
