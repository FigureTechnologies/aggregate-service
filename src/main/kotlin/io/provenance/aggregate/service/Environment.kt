package io.provenance.aggregate.service

enum class Environment {
    local {
        override fun isLocal(): Boolean = true
    },
    development {
        override fun isDevelopment(): Boolean = true
    },
    production {
        override fun isProduction() = true
    };

    open fun isLocal(): Boolean = false
    open fun isDevelopment(): Boolean = false
    open fun isProduction(): Boolean = false
}