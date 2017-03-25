package org.dfw.darkstar.rpc.test;

import org.dfw.darkstar.rpc.Rpc;

import java.util.Random;

/**
 * Created by Administrator on 2017/3/22.
 */
public class Service {
    static public void main(String[] args) throws Exception {
        Random random = new Random(System.currentTimeMillis());
        Rpc.export(MockService.class, new MockService() {
            public String say(String name) {
                return "HELLO " + name;
            }
        }, random.nextInt(1000) + 1000);
    }
}
