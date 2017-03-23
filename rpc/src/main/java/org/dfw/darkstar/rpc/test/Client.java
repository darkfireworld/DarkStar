package org.dfw.darkstar.rpc.test;

import org.dfw.darkstar.rpc.Rpc;

/**
 * Created by Administrator on 2017/3/22.
 */
public class Client {
    static public void main(String[] args) throws Exception {
        final HelloService helloService = (HelloService) Rpc.refer(HelloService.class, "127.0.0.1", 8081);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; ++i) {
            helloService.say("DARK_STAR");
        }
        long endTime = System.currentTimeMillis();
        System.out.println(endTime - startTime);
    }
}
