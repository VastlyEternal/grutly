package com.vastly.hlht.handler;

import com.vastly.hlht.redis.RedisConfig;
import com.vastly.hlht.util.SnowflakeIdWorker;
import com.vastly.hlht.core.affairs.entity.GatewayRoute;
import com.vastly.hlht.hlht.config.dto.DataResult;
import com.vastly.hlht.mybatis.service.GatewayRouteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.*;

@Slf4j
@Component
@Order(1000)
//CommandLineRunner
public class GatewayServiceHandler implements ApplicationEventPublisherAware,CommandLineRunner {

    @Autowired
    private RedisRouteDefinitionRepository routeDefinitionWriter;

    private ApplicationEventPublisher publisher;

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.publisher = applicationEventPublisher;
    }

    //自己的获取数据dao
    @Autowired
    private GatewayRouteService GatewayRouteService;

    //@Resource(name="redisTemplate")
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private SnowflakeIdWorker snowflakeIdWorker;


    @Override
    public void run(String... args){
        this.loadRouteConfig();
    }


    /**
     * 需要拼接key的路由条件
     */
    private static String[] GEN_KEY_ROUTERS = new String[]{"Path", "Host", "Method", "After", "Before", "Between", "RemoteAddr"};

    @SuppressWarnings("unchecked")
    public DataResult loadRouteConfig() {
        log.info("====开始加载=====网关配置信息=========");
        DataResult results = new DataResult();
        //删除redis里面的路由配置信息
        redisTemplate.delete(RedisConfig.GATEWAY_ROUTES);

        //从数据库拿到基本路由配置
        DataResult result = GatewayRouteService.loadRouteConfig();
        if(result.isSuccess()) {
            List<GatewayRoute> gatewayRouteList = (List<GatewayRoute>) result.getData();
            gatewayRouteList.forEach(gatewayRoute -> {
                RouteDefinition definition=handleData(gatewayRoute);

                routeDefinitionWriter.save(Mono.just(definition)).subscribe();
            });

            this.publisher.publishEvent(new RefreshRoutesEvent(this));

            log.info("=======网关配置信息===加载完成======");
            results.setSuccess(true);
        }else {
            results.setSuccess(false);
            results.setMsg("查询配置为空");
        }
        return results;
    }

    public DataResult routeQuery(GatewayRoute gatewayRoute){
       return GatewayRouteService.routeQuery(gatewayRoute);
    }

    public DataResult saveRoute(GatewayRoute gatewayRoute){
        String routeId = String.valueOf(snowflakeIdWorker.nextId());
        gatewayRoute.setRouteId(routeId);
        DataResult result =  GatewayRouteService.addRouteConfig(gatewayRoute);
        if(result.isSuccess()){
            try {
                RouteDefinition definition=handleData(gatewayRoute);
                routeDefinitionWriter.save(Mono.just(definition)).subscribe();
                this.publisher.publishEvent(new RefreshRoutesEvent(this));
                return result;
            }catch (Exception e){
                GatewayRouteService.deleteRouteConfig(gatewayRoute);
                e.printStackTrace();
                return DataResult.error("路由新增失败！"+e.getMessage());
            }
        }else{
            return result;
        }
    }

    public DataResult updateRoute(GatewayRoute gatewayRoute) {
        DataResult result =  GatewayRouteService.updateRouteConfig(gatewayRoute);
        if(result.isSuccess()){
            try {
                RouteDefinition definition=handleData(gatewayRoute);
                this.routeDefinitionWriter.delete(Mono.just(definition.getId()));
                routeDefinitionWriter.save(Mono.just(definition)).subscribe();
                this.publisher.publishEvent(new RefreshRoutesEvent(this));
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                e.printStackTrace();
                return DataResult.error("路由修改失败！"+e.getMessage());
            }
        }else{
            return result;
        }
    }


    public DataResult deleteRoute(String routeId){
        GatewayRoute gatewayRoute = new GatewayRoute();
        gatewayRoute.setRouteId(routeId);
        DataResult result =  GatewayRouteService.deleteRouteConfig(gatewayRoute);
        if(result.isSuccess()){
            try {
                routeDefinitionWriter.delete(Mono.just(routeId)).subscribe();
                this.publisher.publishEvent(new RefreshRoutesEvent(this));
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                e.printStackTrace();
                return DataResult.error("路由删除失败！"+e.getMessage());
            }
        }else{
            return result;
        }
    }

    /**
     * 路由数据转换公共方法
     * @param gatewayRoute
     * @return
     */
    private RouteDefinition handleData(GatewayRoute gatewayRoute){
        RouteDefinition definition = new RouteDefinition();
        definition.setId(gatewayRoute.getRouteId());

        Map<String, String> predicateParams = new HashMap<>(8);
        List<PredicateDefinition> predicateDefinitionList = new ArrayList<>();

        List<FilterDefinition> filters = new ArrayList<FilterDefinition>();


        URI uri = null;
        String  protocol = gatewayRoute.getProtocol().toLowerCase();
        String uriipport = gatewayRoute.getProtocol()+"://"+gatewayRoute.getHost();
        String path = gatewayRoute.getPath();
        log.info(gatewayRoute.getUid()+"GatewayRoute 路由网关地址："+uriipport+path);
        if(protocol.startsWith("http")){
            //http地址
            uri = UriComponentsBuilder.fromHttpUrl(uriipport).build().toUri();
        }else{
            //注册中心
            uri = UriComponentsBuilder.fromUriString("lb://"+uriipport).build().toUri();
        }

        // 名称是固定的，spring gateway会根据名称找对应的PredicateFactory
        PredicateDefinition predicate = new PredicateDefinition();
        predicate.setName("Path");
        predicateParams.put("pattern",path);
        predicate.setArgs(predicateParams);
        predicateDefinitionList.add(predicate);





        //过滤器
        /**
         * 过滤器StripPrefix，作用是去掉请求路径的最前面n个部分截取掉。
         * StripPrefix=1就代表截取路径的个数为1，比如前端过来请求/test/good/1/view，匹配成功后，路由到后端的请求路径就会变成http://localhost:8888/good/1/view。
         */
        FilterDefinition StripPrefixfilterDefinition = new FilterDefinition();
        StripPrefixfilterDefinition.setName("StripPrefix");
        Map<String, String> StripPrefixfilterParams = new HashMap<>(8);
        StripPrefixfilterParams.put("_genkey_0", gatewayRoute.getStripPrefix()+"");
        StripPrefixfilterDefinition.setArgs(StripPrefixfilterParams);
        filters.add(StripPrefixfilterDefinition);




        // todo  根据uri 路径限流
        //如果 burstCapacity 最大访问量等于0，则不进行限流
        if(gatewayRoute.getBurstCapacity()>0){
            FilterDefinition CustomfilterDefinition = new FilterDefinition();
            //CustomfilterDefinition.setName("RequestRateLimiter");
            CustomfilterDefinition.setName("CustomRequestRateLimiter");
            Map<String, String> CustomfixfilterParams = new HashMap<>(8);
            //每秒最大访问次数，令牌桶算法的容量，当值为0时，不限流
            CustomfixfilterParams.put("redis-rate-limiter.burstCapacity", gatewayRoute.getBurstCapacity()+"");
            //令牌桶算法的填充速率，1个/s  访问频率
            CustomfixfilterParams.put("redis-rate-limiter.replenishRate", gatewayRoute.getReplenishRate()+"");
            //令牌桶算法的每个请求消耗的token数，1个/次
            CustomfixfilterParams.put("redis-rate-limiter.requestedTokens", gatewayRoute.getRequestedTokens()+"");
            CustomfixfilterParams.put("key-resolver", gatewayRoute.getResolverKey());
            CustomfilterDefinition.setArgs(CustomfixfilterParams);
            filters.add(CustomfilterDefinition);
        }

        //todo  熔断
        FilterDefinition HystrixfilterDefinition = new FilterDefinition();
        //HystrixfilterDefinition.setName("SpecialHystrix");
        HystrixfilterDefinition.setName("Hystrix");
        Map<String, String> HystrixfixfilterParams = new HashMap<String, String>();
        //HystrixfixfilterParams.put("name","fallback");
        HystrixfixfilterParams.put("name","fallbackcmd");
        HystrixfixfilterParams.put("fallbackUri","forward:/fallback");
        HystrixfilterDefinition.setArgs(HystrixfixfilterParams);
        filters.add(HystrixfilterDefinition);

        // todo 重试 Retry
        /***
         * retries：重试次数，默认值是 3 次
         * statuses：HTTP 的状态返回码，取值请参考：org.springframework.http.HttpStatus
         * methods：指定哪些方法的请求需要进行重试逻辑，默认值是 GET 方法，取值参考：org.springframework.http.HttpMethod
         * series：一些列的状态码配置，取值参考：org.springframework.http.HttpStatus.Series。
         * 符合的某段状态码才会进行重试逻辑，默认值是 SERVER_ERROR，值是 5，也就是 5XX(5 开头的状态码)，共有5 个值
         */
        if(gatewayRoute.getRetry()>0){
            FilterDefinition RetryfilterDefinition = new FilterDefinition();
            RetryfilterDefinition.setName("Retry");
            Map<String, String> RetryfixfilterParams = new HashMap<String, String>();
            RetryfixfilterParams.put("retries",gatewayRoute.getRetry()+"");
            RetryfixfilterParams.put("statuses","BAD_GATEWAY,SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT");
            RetryfilterDefinition.setArgs(RetryfixfilterParams);
            filters.add(RetryfilterDefinition);
        }



        //前端过滤器
        definition.setFilters(filters);






        definition.setPredicates(predicateDefinitionList);
        //definition.setFilters(Arrays.asList(filters));
        definition.setUri(uri);
        //definition.setOrder(Integer.parseInt(gatewayRoute.getOrder()));
        definition.setOrder(0);

        return definition;
    }
}

/**
 * filters
 *- name: RequestRateLimiter  # cloud-gateway默认的限流器
 *               args:
 *                 key-resolver: '#{@hostAddrKeyResolver}'  # 限流器可配置的KeyResolver
 *                 redis-rate-limiter.replenishRate: 1   # 令牌桶算法的填充速率，1个/s
 *                 redis-rate-limiter.requestedTokens: 1  # 令牌桶算法的每个请求消耗的token数，1个/次
 *                 redis-rate-limiter.burstCapacity: 1 # 令牌桶算法的容量，1个
 *
 *                 {
 * 	"StripPrefix":{
 * 		"_genkey_0":"0"
 *        },
 * 	"CustomRequestRateLimiter":{
 *         "redis-rate-limiter.replenishRate":1,
 *         "redis-rate-limiter.burstCapacity": 5,
 * 	"redis-rate-limiter.requestedTokens": 2,
 *         "key-resolver": "#{@UriKeyResolver}"
 *    }
 * }
 *
 * https://www.cnblogs.com/crazymakercircle/p/11704077.html
 *
 *
 */

//断言
//        if(StringUtils.isNotEmpty(gatewayRoute.getPredicates())){
//            JSONArray predicates  = JSONArray.parseArray(gatewayRoute.getPredicates());
//            for (Object map : predicates) {
//                JSONObject json = (JSONObject) map;
//                PredicateDefinition predicateDefinition = new PredicateDefinition();
//                //update-begin-author:zyf date:20220419 for:【VUEN-762】路由条件添加异常问题,原因是部分路由条件参数需要设置固定key
//                String name=json.getString("name");
//                predicateDefinition.setName(name);
//                //路由条件是否拼接Key
//                if(Arrays.asList(GEN_KEY_ROUTERS).contains(name)) {
//                    JSONArray jsonArray = json.getJSONArray("args");
//                    for (int j = 0; j < jsonArray.size(); j++) {
//                        predicateDefinition.addArg("_genkey" + j, jsonArray.get(j).toString());
//                    }
//                }else{
//                    JSONObject jsonObject = json.getJSONObject("args");
//                    if(jsonObject != null){
//                        for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
//                            Object valueObj=entry.getValue();
//                            if(valueObj != null) {
//                                predicateDefinition.addArg(entry.getKey(), valueObj.toString());
//                            }
//                        }
//                    }
//                }
//                //update-end-author:zyf date:20220419 for:【VUEN-762】路由条件添加异常问题,原因是部分路由条件参数需要设置固定key
//                predicateDefinitionList.add(predicateDefinition);
//            }
//        }