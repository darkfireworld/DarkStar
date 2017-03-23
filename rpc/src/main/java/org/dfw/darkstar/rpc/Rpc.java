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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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
        // rpc response future
        class RpcResponseFuture implements Future<RpcResponse> {
            final static int TODO = 0;
            final static int DONE = 1;
            final static int CANCEL = 2;
            CountDownLatch countDownLatch = new CountDownLatch(1);
            volatile AtomicInteger state = new AtomicInteger(TODO);
            volatile RpcResponse rpcResponse = null;

            public boolean cancel(boolean mayInterruptIfRunning) {
                boolean ok = state.compareAndSet(TODO, CANCEL);
                if (ok) {
                    countDownLatch.countDown();
                }
                return ok;
            }

            public boolean isCancelled() {
                return state.get() == CANCEL;
            }

            public boolean isDone() {
                return state.get() == DONE;
            }

            public RpcResponse get() throws InterruptedException, ExecutionException {
                countDownLatch.await();
                return rpcResponse;
            }

            public RpcResponse get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                countDownLatch.await(timeout, unit);
                return rpcResponse;
            }

            public boolean done(RpcResponse rpcResponse) {
                boolean ok = state.compareAndSet(TODO, DONE);
                if (ok) {
                    this.rpcResponse = rpcResponse;
                    countDownLatch.countDown();
                }
                return ok;
            }
        }
        final Map<Long, RpcResponseFuture> pendingMap = new ConcurrentHashMap<Long, RpcResponseFuture>(1024);
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
                                RpcResponseFuture rpcResponseFuture = pendingMap.get(rpcResponse.getRequestId());
                                if (rpcResponseFuture != null) {
                                    rpcResponseFuture.done(rpcResponse);
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
                        RpcResponseFuture rpcResponseFuture = new RpcResponseFuture();
                        // add to pending Map
                        pendingMap.put(rpcRequest.getRequestId(), rpcResponseFuture);
                        channel.writeAndFlush(rpcRequest);
                        RpcResponse rpcResponse = rpcResponseFuture.get();
                        // remove from pending Map
                        pendingMap.remove(rpcRequest.getRequestId());
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
