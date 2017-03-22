package org.dfw.darkstar.rpc.test;

import org.dfw.darkstar.rpc.Rpc;

/**
 * Created by Administrator on 2017/3/22.
 */
public class Client {
    static public void main(String[] args) throws Exception {
        HelloService helloService = (HelloService) Rpc.refer(HelloService.class, "127.0.0.1", 8081);
        System.out.println(helloService.say("DARK_STAR"));

    }
}
