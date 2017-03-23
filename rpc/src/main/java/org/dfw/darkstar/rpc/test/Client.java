package org.dfw.darkstar.rpc.test;

import org.dfw.darkstar.rpc.Rpc;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by Administrator on 2017/3/22.
 */
public class Client {
    static public void main(String[] args) throws Exception {
        final HelloService helloService = (HelloService) Rpc.refer(HelloService.class, "127.0.0.1", 8081);
        long startTime = System.currentTimeMillis();
        helloService.say("DARK_STAR");
        helloService.say("AA");
        long endTime = System.currentTimeMillis();
        System.out.println(endTime - startTime);
    }
}
