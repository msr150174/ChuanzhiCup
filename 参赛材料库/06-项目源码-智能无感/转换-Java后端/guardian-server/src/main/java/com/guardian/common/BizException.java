package com.guardian.common;

import lombok.Getter;

/**
 * 业务异常
 *
 * 抛出后由 GlobalExceptionHandler 统一转成 ApiResponse
 */
@Getter
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(String message) {
        super(message);
        this.code = 400;
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    // ---------------- 常用构造 ----------------

    public static BizException badRequest(String msg) {
        return new BizException(400, msg);
    }

    public static BizException unauthorized(String msg) {
        return new BizException(401, msg == null ? "请先登录" : msg);
    }

    public static BizException forbidden(String msg) {
        return new BizException(403, msg == null ? "无权限" : msg);
    }

    public static BizException notFound(String msg) {
        return new BizException(404, msg == null ? "资源不存在" : msg);
    }

    public static BizException serverError(String msg) {
        return new BizException(500, msg == null ? "服务异常" : msg);
    }
}
