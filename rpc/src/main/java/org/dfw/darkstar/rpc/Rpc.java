package org.dfw.darkstar.rpc;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolver;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rpc
 * <p>
 * http://www.cnblogs.com/metoy/p/4321311.html?utm_source=tuicool&utm_medium=referral
 */
public class Rpc {

    static public void export(final Class<?> cls, final Object inst, int port) throws Exception {
        if (!cls.isInterface()) {
            throw new RuntimeException("CLS NEED INSTANCE");
        }
        if (!cls.isAssignableFrom(inst.getClass())) {
            throw new RuntimeException("INST NEED SUB_CLASS FOR CLS");
        }
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .channel(NioServerSocketChannel.class)
                .group(bossGroup, workerGroup)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
//                        ch.pipeline().addLast("idle", new IdleStateHandler(0, 0, 16));
                        ch.pipeline().addLast("decode", new ObjectDecoder(Integer.MAX_VALUE, new ClassResolver() {
                            public Class<?> resolve(String className) throws ClassNotFoundException {
                                return Rpc.class.getClassLoader().loadClass(className);
                            }
                        }));
                        ch.pipeline().addLast("encode", new ObjectEncoder());
                        ch.pipeline().addLast("biz", new SimpleChannelInboundHandler<Object>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                                if (!(msg instanceof RpcRequest)) {
                                    return;
                                }
                                RpcRequest rpcRequest = (RpcRequest) msg;
                                Object ret = null;
                                Throwable exp = null;
                                try {
                                    if (!rpcRequest.getCls().equals(cls.getName())) {
                                        throw new RuntimeException("ERROR CLS NAME FIND CALL");
                                    }
                                    Class<?>[] parameterTypes = new Class[rpcRequest.getParam() != null ? rpcRequest.getParam().length : 0];
                                    if (rpcRequest.getParam() != null) {
                                        int i = -1;
                                        for (Object param : rpcRequest.getParam()) {
                                            i++;
                                            parameterTypes[i] = param.getClass();
                                        }
                                    }
                                    Method method = cls.getMethod(rpcRequest.getMethod(), parameterTypes);
                                    if (method == null) {
                                        throw new RuntimeException("CANT FOUND METHOD");
                                    }
                                    ret = method.invoke(inst, rpcRequest.getParam() != null ? rpcRequest.getParam() : new Object[]{});
                                } catch (Throwable throwable) {
                                    exp = throwable;
                                }
                                RpcResponse rpcResponse = new RpcResponse(rpcRequest.getRequestId(), ret, exp);
                                ctx.writeAndFlush(rpcResponse);
                            }
                        });
                    }
                });
        serverBootstrap.bind(port).sync().channel().closeFuture().sync();
    }

    static public Object refer(final Class<?> cls, String host, int port) throws Exception {
        if (!cls.isInterface()) {
            throw new RuntimeException("CLS NEED INSTANCE");
        }
        final Map<Long, Tuple3<RpcRequest, RpcResponse, CountDownLatch>> pendMap = new ConcurrentHashMap<Long, Tuple3<RpcRequest, RpcResponse, CountDownLatch>>(1024);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap()
                .channel(NioSocketChannel.class)
                .group(workerGroup)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast("decode", new ObjectDecoder(Integer.MAX_VALUE, new ClassResolver() {
                            public Class<?> resolve(String className) throws ClassNotFoundException {
                                return Rpc.class.getClassLoader().loadClass(className);
                            }
                        }));
                        ch.pipeline().addLast("encode", new ObjectEncoder());
                        ch.pipeline().addLast("biz", new SimpleChannelInboundHandler<Object>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                                if (!(msg instanceof RpcResponse)) {
                                    return;
                                }
                                RpcResponse rpcResponse = (RpcResponse) msg;
                                Tuple3<RpcRequest, RpcResponse, CountDownLatch> pending = pendMap.get(rpcResponse.getRequestId());
                                if (pending != null) {
                                    pending._2 = rpcResponse;
                                    pending._3.countDown();
                                }
                            }
                        });
                    }
                });
        final Channel channel = bootstrap.connect(host, port).sync().channel();
        final AtomicLong ids = new AtomicLong(1);
        return Proxy.newProxyInstance(cls.getClassLoader(),
                new Class[]{cls}, new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        RpcRequest rpcRequest = new RpcRequest(ids.getAndIncrement(), cls.getName(), method.getName(), args);
                        CountDownLatch countDownLatch = new CountDownLatch(1);
                        pendMap.put(rpcRequest.getRequestId(), new Tuple3<RpcRequest, RpcResponse, CountDownLatch>(rpcRequest, null, countDownLatch));
                        channel.writeAndFlush(rpcRequest);
                        countDownLatch.await();
                        Tuple3<RpcRequest, RpcResponse, CountDownLatch> pending = pendMap.get(rpcRequest.getRequestId());
                        pendMap.remove(rpcRequest.getRequestId());
                        if (pending == null) {
                            throw new RuntimeException("RpcResponse Is Null#1");
                        }
                        RpcResponse rpcResponse = pending._2;
                        if (rpcResponse == null) {
                            throw new RuntimeException("RpcResponse Is Null#2");
                        }
                        if (rpcResponse.getExp() != null) {
                            throw rpcResponse.getExp();
                        } else {
                            return rpcResponse.getRet();
                        }
                    }
                });
    }
}
