package com.hongcheng.gateway.config;

import com.hongcheng.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 网关异常日志脱敏处理器。
 * <p>
 * 默认 AbstractErrorWebExceptionHandler 会把完整请求 URI（含 /ws/order?token=...）
 * 与异常堆栈（reactor checkpoint 同样带 URI）原样写进日志，造成 JWT 落盘。
 * 这里以更高优先级（order=-1，先于默认的 0）接管错误渲染：日志只记录
 * 脱敏后的 URI 与异常摘要，不再打印含 checkpoint 的完整堆栈；
 * 响应体保持统一 Result 结构。
 */
@Slf4j
@Component
@Order(-1)
public class SanitizedErrorWebExceptionHandler extends AbstractErrorWebExceptionHandler {

    /** 查询参数中的敏感值：token/authorization/password/secret */
    private static final Pattern SENSITIVE_PARAM =
            Pattern.compile("(?i)(token|authorization|password|secret)=[^&\\s\"']+");
    /** 裸 JWT（三段 base64url），兜底覆盖非查询参数位置出现的 token */
    private static final Pattern RAW_JWT =
            Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");

    public SanitizedErrorWebExceptionHandler(ErrorAttributes errorAttributes,
                                             WebProperties webProperties,
                                             ServerCodecConfigurer serverCodecConfigurer,
                                             ApplicationContext applicationContext) {
        super(errorAttributes, webProperties.getResources(), applicationContext);
        setMessageWriters(serverCodecConfigurer.getWriters());
        setMessageReaders(serverCodecConfigurer.getReaders());
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }

    private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        Map<String, Object> attrs = getErrorAttributes(request, ErrorAttributeOptions.defaults());
        int status = attrs.get("status") instanceof Integer value ? value : 500;
        String message = status == HttpStatus.NOT_FOUND.value() ? "资源不存在" : "系统繁忙，请稍后重试";
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.fail(status, message));
    }

    @Override
    protected void logError(ServerRequest request, ServerResponse response, Throwable throwable) {
        String uri = sanitize(request.exchange().getRequest().getURI().toString());
        String reason = sanitize(String.valueOf(throwable));
        int status = response.statusCode() == null ? 500 : response.statusCode().value();
        if (status >= 500) {
            log.error("网关处理异常 status={}, uri={}, reason={}", status, uri, reason);
        } else {
            log.warn("网关请求异常 status={}, uri={}, reason={}", status, uri, reason);
        }
    }

    private String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String masked = SENSITIVE_PARAM.matcher(text).replaceAll("$1=***");
        return RAW_JWT.matcher(masked).replaceAll("***");
    }
}