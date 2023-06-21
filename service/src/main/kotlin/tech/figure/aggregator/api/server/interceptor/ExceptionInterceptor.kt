package tech.figure.aggregator.api.server.interceptor

import io.grpc.ForwardingServerCall
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCall.Listener
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import io.grpc.StatusException
import org.slf4j.LoggerFactory

class ExceptionInterceptor : ServerInterceptor {
    override fun <ReqT : Any, RespT : Any> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): Listener<ReqT> = next.startCall(ExceptionTranslatingServerCall(call), headers)

}

class ClientException(status: Status, description: String) : StatusException(status.withDescription(description))

/**
 * When closing a gRPC call, extract any error status information to top-level fields. Also
 * log the cause of errors.
 */
private class ExceptionTranslatingServerCall<ReqT, RespT>(
    delegate: ServerCall<ReqT, RespT>
) : ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(delegate) {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun close(status: Status, trailers: Metadata) {
        if (status.isOk) {
            return super.close(status, trailers)
        }
        val cause = status.cause
        var newStatus = status

        log.warn("Error handling grpc call", cause)
        if (status.code == Status.Code.UNKNOWN) {
            newStatus = when (cause) {
                is IllegalArgumentException -> constructResponse(Status.INVALID_ARGUMENT)
                is IllegalStateException -> constructResponse(Status.FAILED_PRECONDITION)
                is ClientException -> cause.status
                else -> constructResponse(Status.UNKNOWN)
            }
        }
        super.close(newStatus, trailers)
    }

    fun constructResponse(status: Status, description: String? = null, cause: Throwable? = null): Status =
        status.withDescription(description ?: "An unknown error has occurred").withCause(cause)
}
