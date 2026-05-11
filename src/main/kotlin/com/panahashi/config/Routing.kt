package com.panahashi.config

import com.panahashi.routes.productRoutes
import com.panahashi.routes.orderRoutes
import com.panahashi.routes.userRoutes
import com.panahashi.routes.healthRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        healthRoutes()
        route("/api/v1") {
            productRoutes()
            orderRoutes()
            userRoutes()
        }
    }
}
