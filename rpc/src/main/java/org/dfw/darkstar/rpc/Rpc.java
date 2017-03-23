package org.dfw.darkstar.rpc;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.nustaq.serialization.FSTConfiguration;
import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
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
    static Logger logger = LoggerFactory.getLogger(Rpc.class);
    static FSTConfiguration fst = FSTConfiguration.createDefaultConfiguration();

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
                        ch.pipeline().addLast("IDLE", new IdleStateHandler(0, 0, 8));
                        ch.pipeline().addLast("FRAME_DECODE", new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                        ch.pipeline().addLast("FRAME_ENCODE", new LengthFieldPrepender(4, 0));
                        ch.pipeline().addLast("FST_DECODE", new ByteToMessageDecoder() {
                            @Override
                            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
                                FSTObjectInput is = fst.getObjectInput(new ByteBufInputStream(in));
                                Object result = is.readObject();
                                out.add(result);
                            }
                        });
                        ch.pipeline().addLast("FST_ENCODE", new MessageToByteEncoder<Serializable>() {
                            @Override
                            protected void encode(ChannelHandlerContext ctx, Serializable msg, ByteBuf out) throws Exception {
                                ByteBufOutputStream bout = new ByteBufOutputStream(out);
                                FSTObjectOutput oout = fst.getObjectOutput(bout);
                                oout.writeObject(msg);
                                oout.flush();
                            }
                        });
                        ch.pipeline().addLast("TIMEOUT", new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                if (evt instanceof IdleStateEvent) {
                                    IdleStateEvent e = (IdleStateEvent) evt;
                                    if (e.state() == IdleState.ALL_IDLE) {
                                        logger.error("RPC CONNECT CLOSE，BECAUSE CONNECT IS IDLE TIMEOUT");
                                        ctx.close();
                                    }
                                }
                            }
                        });
                        ch.pipeline().addLast("BIZ", new SimpleChannelInboundHandler<Object>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                                if (!(msg instanceof RpcRequest)) {
                                    return;
                                }
                                RpcRequest rpcRequest = (RpcRequest) msg;
                                RpcResponse rpcResponse;
                                switch (rpcRequest.getType()) {
                                    case RpcRequest.PING: {
                                        logger.info("RPC PONG");
                                        // ping
                                        rpcResponse = new RpcResponse(rpcRequest.getRequestId(), RpcResponse.PONG, null, null);
                                    }
                                    break;
                                    case RpcRequest.RPC_REQUEST: {
                                        // rpc
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
                                        rpcResponse = new RpcResponse(rpcRequest.getRequestId(), 2, ret, exp);
                                    }
                                    break;
                                    default: {
                                        rpcResponse = new RpcResponse(rpcRequest.getRequestId(), RpcResponse.RPC_RESPONSE, null, null);
                                    }
                                }
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
        final AtomicLong ids = new AtomicLong(1);
        final Map<Long, RpcResponseFuture> pendingMap = new ConcurrentHashMap<Long, RpcResponseFuture>(1024);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap()
                .channel(NioSocketChannel.class)
                .group(workerGroup)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast("IDLE", new IdleStateHandler(8, 4, 0));
                        ch.pipeline().addLast("FRAME_DECODE", new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                        ch.pipeline().addLast("FRAME_ENCODE", new LengthFieldPrepender(4, 0));
                        ch.pipeline().addLast("FST_DECODE", new ByteToMessageDecoder() {
                            @Override
                            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
                                FSTObjectInput is = fst.getObjectInput(new ByteBufInputStream(in));
                                Object result = is.readObject();
                                out.add(result);
                            }
                        });
                        ch.pipeline().addLast("FST_ENCODE", new MessageToByteEncoder<Serializable>() {
                            @Override
                            protected void encode(ChannelHandlerContext ctx, Serializable msg, ByteBuf out) throws Exception {
                                ByteBufOutputStream bout = new ByteBufOutputStream(out);
                                FSTObjectOutput oout = fst.getObjectOutput(bout);
                                oout.writeObject(msg);
                                oout.flush();
                            }
                        });
                        ch.pipeline().addLast("TIMEOUT", new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                if (evt instanceof IdleStateEvent) {
                                    IdleStateEvent e = (IdleStateEvent) evt;
                                    if (e.state() == IdleState.READER_IDLE) {
                                        logger.error("RPC CONNECT CLOSE，BECAUSE CONNECT IS READ TIMEOUT");
                                        ctx.close();
                                    } else if (e.state() == IdleState.WRITER_IDLE) {
                                        logger.error("RPC PING");
                                        ctx.writeAndFlush(new RpcRequest(ids.incrementAndGet(), RpcRequest.PING, null, null, null));
                                    }
                                }
                            }
                        });
                        ch.pipeline().addLast("BIZ", new SimpleChannelInboundHandler<Object>() {
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

        return Proxy.newProxyInstance(cls.getClassLoader(),
                new Class[]{cls}, new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        RpcRequest rpcRequest = new RpcRequest(ids.getAndIncrement(), RpcRequest.RPC_REQUEST, cls.getName(), method.getName(), args);
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
