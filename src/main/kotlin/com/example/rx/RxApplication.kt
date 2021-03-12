package com.example.rx

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.reactive.asFlow
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.support.beans
import org.springframework.data.annotation.Id
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyAndAwait
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import reactor.core.publisher.Flux
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.stream.Stream

@SpringBootApplication
class RxApplication

fun main(args: Array<String>) {
	runApplication<RxApplication>(*args) {

		val context = beans {

			bean {
				val dbc = ref<DatabaseClient>()
				val repo = ref<CustomerRepository>()
				ApplicationRunner {
					val ddl =
						dbc.sql("create table customer(id serial primary key, name varchar(255) not null)")
							.fetch().rowsUpdated()
					val writes = Flux.just("A", "B", "C")
						.map { Customer(null, it) }
						.flatMap { repo.save(it) }
					ddl.thenMany(writes).thenMany(repo.findAll())
						.subscribe { println("got a new result: $it") }
				}
			}
			bean {
				SimpleUrlHandlerMapping(
					mapOf("/ws/greetings" to ref<WebSocketHandler>()),
					10
				)
			}
			bean {
				val om = ref<ObjectMapper>()
				WebSocketHandler { wss ->
					val messages =
						Flux.fromStream(Stream.generate { mapOf("message" to "Hello, world, @ ${Instant.now()}!") })
							.delayElements(Duration.ofSeconds(1))
							.map { om.writeValueAsString(it) }
							.map { wss.textMessage(it) }
					wss.send(messages)

				}
			}


			bean {

				coRouter {
					val repo = ref<CustomerRepository>()
					GET("/customers") {
						ServerResponse.ok().bodyAndAwait(repo.findAll().asFlow())
					}
				}
			}

		}
		addInitializers(context)
	}
}

data class Customer(@Id var id: Int? = null, val name: String)

interface CustomerRepository : ReactiveCrudRepository<Customer, Int>
