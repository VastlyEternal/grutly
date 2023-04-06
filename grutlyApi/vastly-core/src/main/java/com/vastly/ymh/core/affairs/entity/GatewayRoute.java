package com.vastly.ymh.core.affairs.entity;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.Date;

@Data
@Getter
@Setter
@AllArgsConstructor //会生成一个包含所有变量
@NoArgsConstructor//生成一个无参数的构造方法
public class GatewayRoute implements Serializable {

    /**
     * 程序序列化ID
     */
    private static final long serialVersionUID = 7498483649539900004L;

    /**
     * 服务ID
     */
    private String id;

    /**
     * 租户
     */
    private String uid;

    /**
     * 目录名称
     */

    private String routeName;


    public String getRouteName() {
        if (!StringUtils.isEmpty(routeName)) {
            return routeName.replaceAll(" ", "");
        }
        return routeName;
    }

    /**
     * 版本
     */

    private String version;

    /**
     * 创建时间
     */

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createDate;

    public Date getCreateDate() {
        return createDate == null ? null : (Date) createDate.clone();
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate == null ? null : (Date) createDate.clone();
    }


    /**
     * '请求方式 http、socket、webService',
     */
    private String protocol;

    /**
     * 服务资源地址 http://127.0.0.1
     */
    private String host;

    /**
     * 用户 swagger 地址
     */
    private String path;

    private String filters;//

    /**
     * 过滤器StripPrefix，作用是去掉请求路径的最前面n个部分截取掉。StripPrefix=1就代表截取路径的个数为1，
     * 比如前端过来请求/test/good/1/view，匹配成功后，路由到后端的请求路径就会变成http://localhost:8888/good/1/view。'
     */
    private int stripPrefix;

    private String resolverKey;

    /**
     * '每秒最大访问次数，令牌桶算法的容量，当值为0时，不限流',
     */
    private int burstCapacity;
    /**
     * '令牌桶算法的填充速率，访问频率，1个/s',
     */
    private int replenishRate;
    /**
     * '令牌桶算法的每个请求消耗的token数，1个/次',
     */
    private int requestedTokens;
    /**
     * 超时时间，单位秒，默认：30s
     */
    private int timeout;

    public int getTimeout() {
        return timeout == 0 ? 30 : timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout == 0 ? 30 : timeout;
    }
    /**
     * 顺序
     */
    private int  order;

    private int retry;
    /**
     * 备注
     */
    private String remarks;
    /**
     * 服务资源状态，0-正常；1-非正常
     */
    private int isValid;


    private String swaggerJson;


    private String swaggerUri;

    /**
     * 接口生成方式，0-SwaggerUri；1-SwaggerJson; 2-手动
     */
    private int openApiType;

}