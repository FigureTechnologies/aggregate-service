package io.provenance.aggregate.service.stream

typealias Action = (Array<out Any?>) -> Any?

interface ServiceMock {
    fun <T> respondWith(method: String, vararg args: Any?): T
}

class ServiceMocker private constructor(
    private val actions: Map<String, Action>
) : ServiceMock {

    data class Builder(
        private val buildActions: MutableMap<String, Action> = mutableMapOf(),
    ) {
        /**
         * Register an action to run for a given method with the arguments passed to the calling API method that is
         * being mocked
         */
        fun doFor(method: String, action: Action): Builder = apply {
            buildActions[method] = action
        }

        fun build(): ServiceMocker = ServiceMocker(buildActions)

        fun <T> build(clazz: Class<T>): T = clazz.getDeclaredConstructor(ServiceMock::class.java).newInstance(build())
    }

    override fun <T> respondWith(method: String, vararg args: Any?): T {
        val action = actions[method] ?: throw IllegalArgumentException("Bad method: $method")
        return action(args) as T
    }
}