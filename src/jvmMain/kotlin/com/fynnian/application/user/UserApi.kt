package com.fynnian.application.user

import com.fynnian.application.common.URLS
import com.fynnian.application.common.checkRequestIds
import com.fynnian.application.common.getUUIDParam
import com.fynnian.application.common.user.User
import com.fynnian.application.config.DI
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userApi(dependencies: DI) {
  val idParam = "id"

  route(URLS.API_USERS_BY_ID) {
    // get user by id
    get {
      val id = call.getUUIDParam(idParam)

      dependencies.userRepository
        .getUserById(id)
        .also { call.respond(it) }
    }
    // create / update user by id
    put {
      val id = call.getUUIDParam(idParam)
      val request = call.receive<User>()
      call.checkRequestIds(id, request.id)

      dependencies.userRepository
        .upsertUser(request)
        .also { call.respond(it) }
    }
    // delete user
    delete {
      val id = call.getUUIDParam(idParam)

      dependencies.userRepository
        .deleteUser(id)
        .also { call.response.status(HttpStatusCode.OK) }

    }
  }
}

