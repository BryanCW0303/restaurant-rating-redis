package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public UserServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result sendCode(String phone, HttpSession session) {

        // check the phone number is valid
        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("Invalid phone number");
        }

        // generate random code
        String code = RandomUtil.randomNumbers(6);

        // save phone-code to redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("success, code: {}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // check the phone number is valid
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("Invalid phone number");
        }


        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("wrong code");
        }

        User user = query().eq("phone", phone).one();

        if(user == null) {
            user = createUserWithPhone(phone);
        }

        // generate token for key, and put hash value(token, userDTO) to redis
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

//        @SuppressWarnings("unchecked")
//        Map<String, String> userMap = (Map<String, String>) (Map<?, ?>) BeanUtil.beanToMap(
//                userDTO,
//                new HashMap<String, Object>(),
//                CopyOptions.create()
//                        .setIgnoreNullValue(true)
//                        .setFieldValueEditor((k, v) -> v == null ? null : v.toString())
//        );

        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));


        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    @Override
    public Result sign() {
        // retrieve user information
        Long userId = UserHolder.getUser().getId();
        // retrieve Date information
        LocalDateTime now = LocalDateTime.now();
        // create a key
        String yyyyMM = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = USER_SIGN_KEY + userId + yyyyMM;
        // retrieve date
        int dayOfMonth = now.getDayOfMonth();
        // implement attendance records using Redis Bitmap
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // retrieve user information
        Long userId = UserHolder.getUser().getId();
        // retrieve Date information
        LocalDateTime now = LocalDateTime.now();
        // create a key
        String yyyyMM = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = USER_SIGN_KEY + userId + yyyyMM;
        // retrieve date
        int dayOfMonth = now.getDayOfMonth();
        // query the attendance bitmap
        List<Long> bitField = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (bitField == null || bitField.isEmpty()) {
            return Result.ok();
        }
        Long num = bitField.get(0);
        if (num == null) {
            return Result.ok();
        }
        // calculate consecutive check-in days
        int cnt = 0;
        // iteration, perform bitwise AND with 1 to retrieve the last bit
        while (true) {
            if ((num & 1) == 0) {
                break;
            } else {
                cnt++;
            }
            num >>>= 1;
        }
        return Result.ok(cnt);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
