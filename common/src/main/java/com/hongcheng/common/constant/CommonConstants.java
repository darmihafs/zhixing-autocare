package com.hongcheng.common.constant;

/**
 * 跨服务通用常量。
 */
public final class CommonConstants {

    /** 认证请求头 */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /** Token 前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 网关鉴权通过后向下透传的用户ID头（下游服务只信任网关转发的该头） */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 网关鉴权通过后向下透传的用户名头 */
    public static final String HEADER_USERNAME = "X-Username";

    /** 网关鉴权通过后向下透传的用户角色头（下游管理端接口据此判 ADMIN） */
    public static final String HEADER_USER_ROLE = "X-User-Role";

    /** JWT claim：用户名 */
    public static final String CLAIM_USERNAME = "username";

    /** JWT claim：角色 */
    public static final String CLAIM_ROLE = "role";

    /** JWT claim：模拟发起者（原管理员）用户ID，仅模拟 Token 携带 */
    public static final String CLAIM_IMPERSONATOR_ID = "impersonatorId";

    /** JWT claim：模拟发起者（原管理员）用户名，用于审计与横幅展示 */
    public static final String CLAIM_IMPERSONATED_BY = "impersonatedBy";

    /** 网关透传的模拟发起者ID头（下游审计用；客户端伪造值会被网关剥离） */
    public static final String HEADER_IMPERSONATOR_ID = "X-Impersonator-Id";

    /** 角色：普通用户 */
    public static final String ROLE_USER = "USER";

    /** 角色：管理员 */
    public static final String ROLE_ADMIN = "ADMIN";

    /** 服务间内部调用凭证头（/internal/** 接口校验，网关不路由该前缀） */
    public static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    /** 管理端凭证头（门店管理、商家回复等后台写接口校验） */
    public static final String HEADER_ADMIN_TOKEN = "X-Admin-Token";

    /** 评价点赞事件主题：user-service 生产，review-service 消费 */
    public static final String TOPIC_REVIEW_LIKE = "HC_REVIEW_LIKE";

    private CommonConstants() {
    }
}