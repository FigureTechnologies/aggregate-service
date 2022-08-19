package com.tech.figure.aggregator.api.route.exception

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.tech.figure.aggregator.api.route.exception.OptionalResult.FAIL.FailSerializer

inline fun <reified ResponseType> NormalOpenAPIRoute.throwExceptions(
    responseType: ResponseType,
    crossinline block: NormalOpenAPIRoute.() -> Unit
) {

}

interface OptionalResult<out ResultType: Any>{
    val data: ResultType?

    @JsonSerialize(using = FailSerializer::class)
    object FAIL: OptionalResult<Nothing> {
        override val data = null

        class FailSerializer @JvmOverloads constructor(t: Class<FAIL>? = null): StdSerializer<FAIL>(t) {
            override fun serialize(value: FAIL, gen: JsonGenerator, provider: SerializerProvider) {
                gen.writeStartObject()
                gen.writeObjectField("data", null)
                gen.writeEndObject()
            }
        }
    }
}
