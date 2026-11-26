package com.hongcheng.common.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一把 Long / long 序列化为字符串。
 * <p>
 * 雪花 ID 超出 JavaScript Number.MAX_SAFE_INTEGER，直接以数字返回会在前端丢失精度，
 * 导致点赞、评价等按 ID 回传时指向错误记录。序列化为字符串后由后端按 Long 反序列化。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        };
    }
}
