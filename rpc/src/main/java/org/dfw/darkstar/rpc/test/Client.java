package org.dfw.darkstar.rpc.test;

import org.dfw.darkstar.rpc.Rpc;

/**
 * Created by Administrator on 2017/3/22.
 */
public class Client {
    static public void main(String[] args) throws Exception {
        final MockService mockService = (MockService) Rpc.refer(MockService.class);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000 * 10; ++i) {
            mockService.say("DARK_STAR");
        }
        long endTime = System.currentTimeMillis();
        System.out.println(endTime - startTime);
    }
}
