package tech.figure.aggregator.api.server.interceptor

import io.grpc.Context
import io.grpc.Context.Key
import io.grpc.Contexts
import io.grpc.Grpc
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCall.Listener
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import java.net.InetSocketAddress

val CONTEXT_REMOTE_ADDR: Key<String> = Context.key("remote-addr")
val CONTEXT_REMOTE_PORT: Key<Int> = Context.key("remote-port")
class RequestInterceptor : ServerInterceptor {
    override fun <ReqT : Any, RespT : Any> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): Listener<ReqT> {
        val addr = call.attributes[Grpc.TRANSPORT_ATTR_REMOTE_ADDR] as InetSocketAddress
        val host = addr.address.hostAddress
        val port = addr.port
        val ctx = Context.current().withValues(CONTEXT_REMOTE_ADDR, host, CONTEXT_REMOTE_PORT, port)
        return Contexts.interceptCall(ctx, call, headers, next)
    }
}
