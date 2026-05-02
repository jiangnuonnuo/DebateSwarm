package com.dasi.trigger.interceptor;

import com.alibaba.fastjson2.JSON;
import com.dasi.domain.util.jwt.UserContext;
import com.dasi.domain.user.model.vo.UserVO;
import com.dasi.domain.util.jwt.IJwtUtil;
import com.dasi.types.result.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Set;

import static com.dasi.domain.admin.model.enumeration.UserRole.ADMIN;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Set<String> WHITE_LIST = Set.of(
            "/miniagent/api/v1/user/auth/login",
            "/miniagent/api/v1/user/auth/register",
            "/miniagent/api/v1/ws/"
    );

    private static final Set<String> DEV_BYPASS_PREFIXES = Set.of(
            "/miniagent/api/v1/xhs/publish/"
    );

    @Resource
    private IJwtUtil jwtUtil;

    @Resource
    private UserContext userContext;

    @Value("${miniagent.auth.dev-bypass.enabled:false}")
    private boolean devBypassEnabled;

    @Value("${miniagent.auth.dev-bypass.user-id:1}")
    private Long devBypassUserId;

    @Value("${miniagent.auth.dev-bypass.user-name:dev-xhs-tester}")
    private String devBypassUserName;

    @Value("${miniagent.auth.dev-bypass.user-role:user}")
    private String devBypassUserRole;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();

        // 步骤 1：浏览器预检请求直接放行，避免 CORS 场景被鉴权逻辑误伤。
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        // 步骤 2：登录/注册等公开接口继续走白名单，不要求携带 token。
        if (WHITE_LIST.stream().anyMatch(uri::startsWith)) {
            return true;
        }

        // 步骤 3：开发环境临时开放小红书发布链路，并注入固定测试用户，便于接口联调。
        if (allowDevBypass(uri)) {
            userContext.set(UserContext.UserInfo.builder()
                    .userId(devBypassUserId)
                    .userName(devBypassUserName)
                    .userRole(devBypassUserRole)
                    .build());
            log.warn("【鉴权拦截】开发测试绕过已启用：uri={}, userId={}, userName={}",
                    uri, devBypassUserId, devBypassUserName);
            return true;
        }

        // 步骤 4：其余接口仍然要求 Bearer Token，保持原有登录校验口径不变。
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(response, "未登录或登录已过期");
        }

        String token = authHeader.substring(7);
        UserVO userVO;

        try {
            userVO = jwtUtil.parseToken(token);
        } catch (Exception e) {
            log.error("【Token 校验】失败", e);
            return unauthorized(response, "登录状态无效，请重新登录");
        }

        userContext.set(UserContext.UserInfo.builder()
                .userId(userVO.getUserId())
                .userName(userVO.getUserName())
                .userRole(userVO.getUserRole())
                .build());

        if (uri.startsWith("/miniagent/api/v1/admin") && (userVO.getUserRole() == null || !ADMIN.getRole().equalsIgnoreCase(userVO.getUserRole()))) {
            return forbidden(response, "无权限访问该资源");
        }

        return true;
    }

    private boolean allowDevBypass(String uri) {
        return devBypassEnabled && DEV_BYPASS_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        userContext.clear();
    }

    private boolean unauthorized(HttpServletResponse response, String message) throws IOException {
        Result<Void> body = Result.error(message);
        writeResponse(response, HttpStatus.UNAUTHORIZED.value(), body);
        return false;
    }

    private boolean forbidden(HttpServletResponse response, String message) throws IOException {
        Result<Void> body = Result.error(message);
        writeResponse(response, HttpStatus.FORBIDDEN.value(), body);
        return false;
    }

    private void writeResponse(HttpServletResponse response, int status, Result<Void> body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(body));
    }

}
