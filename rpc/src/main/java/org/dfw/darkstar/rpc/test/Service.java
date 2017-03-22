package org.dfw.darkstar.rpc.test;

import org.dfw.darkstar.rpc.Rpc;

/**
 * Created by Administrator on 2017/3/22.
 */
public class Service {
    static public void main(String[] args) throws Exception {
        Rpc.export(HelloService.class, new HelloService() {
            public String say(String name) {
                System.out.println("RECV：" + name);
                return "HELLO " + name;
            }
        }, 8081);
    }
}
