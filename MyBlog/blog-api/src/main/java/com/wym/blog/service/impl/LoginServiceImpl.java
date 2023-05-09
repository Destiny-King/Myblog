package com.wym.blog.service.impl;

import com.alibaba.fastjson.JSON;
import com.wym.blog.dao.pojo.SysUser;
import com.wym.blog.service.LoginService;
import com.wym.blog.service.SysUserService;
import com.wym.blog.utils.JWTUtils;
import com.wym.blog.vo.ErrorCode;
import com.wym.blog.vo.Result;
import com.wym.blog.vo.params.LoginParam;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class LoginServiceImpl implements LoginService {

	@Autowired
	private SysUserService sysUserService;

	@Autowired
	private RedisTemplate<String, String> redisTemplate;

	private static final String salt = "wym!@#";

	@Override
	public Result login(LoginParam loginParam) {
		/**
		 * 1、检查参数是否合法
		 * 2、根据用户名和密码去user表中查询，是否存在
		 * 3、如果不存在，登陆失败
		 * 4、如果存在，使用jwt生成token，返回给前端
		 * 5、token放入redis当中，设置过期时间
		 */
		String account = loginParam.getAccount();
		String password = loginParam.getPassword();
		if (StringUtils.isBlank(account) || StringUtils.isBlank(password)) {
			return Result.fail(ErrorCode.PARAMS_ERROR.getCode(), ErrorCode.PARAMS_ERROR.getMsg());
		}
		password = DigestUtils.md5Hex(password + salt);
		SysUser sysUser = sysUserService.findUser(account, password);
		if (sysUser == null) {
			return Result.fail(ErrorCode.ACCOUNT_PWD_NOT_EXIST.getCode(), ErrorCode.ACCOUNT_PWD_NOT_EXIST.getMsg());
		}
		String token = JWTUtils.createToken(sysUser.getId());
		redisTemplate.opsForValue().set("TOKEN_" + token, JSON.toJSONString(sysUser), 1, TimeUnit.DAYS);
		return Result.success(token);
	}

	@Override
	public SysUser checkToken(String token) {
		if (StringUtils.isBlank(token)) {
			return null;
		}
		Map<String, Object> stringObjectMap = JWTUtils.checkToken(token);
		if (stringObjectMap == null) {
			return null;
		}
		String userJson = redisTemplate.opsForValue().get("TOKEN_" + token);
		if (StringUtils.isBlank(userJson)) {
			return null;
		}
		SysUser sysUser = JSON.parseObject(userJson, SysUser.class);
		return sysUser;
	}

	@Override
	public Result logout(String token) {
		redisTemplate.delete("TOKEN_" + token);
		return Result.success(null);
	}

	@Override
	public Result register(LoginParam loginParam) {
		/**
		 * 1、判断参数是否合法
		 * 2、判断账户是否存在
		 * 3、生成token，存入redis
		 * 4、加上事务
		 */
		String account = loginParam.getAccount();
		String password = loginParam.getPassword();
		String nickname = loginParam.getNickname();
		if (StringUtils.isBlank(account) || StringUtils.isBlank(password) || StringUtils.isBlank(nickname)) {
			return Result.fail(ErrorCode.PARAMS_ERROR.getCode(), ErrorCode.PARAMS_ERROR.getMsg());
		}
		SysUser sysUser = sysUserService.findUserByAccount(account);
		if (sysUser != null) {
			return Result.fail(ErrorCode.ACCOUNT_EXIST.getCode(), "账户已存在!");
		}
		sysUser = new SysUser();
		sysUser.setNickname(nickname);
		sysUser.setAccount(account);
		sysUser.setPassword(DigestUtils.md5Hex(password + salt));
		sysUser.setCreateDate(System.currentTimeMillis());
		sysUser.setLastLogin(System.currentTimeMillis());
		sysUser.setAvatar("/static/img/logo.b3a48c0.png");
		sysUser.setAdmin(1);
		sysUser.setDeleted(0);
		sysUser.setSalt("");
		sysUser.setStatus("");
		sysUser.setEmail("");
		this.sysUserService.save(sysUser);
		String token = JWTUtils.createToken(sysUser.getId());
		redisTemplate.opsForValue().set("TOKEN_" + token, JSON.toJSONString(sysUser), 1, TimeUnit.DAYS);
		return Result.success(token);
	}

	public static void main(String[] args) {
		String s = DigestUtils.md5Hex("admin" + salt);
		System.out.println(s);
	}
}
