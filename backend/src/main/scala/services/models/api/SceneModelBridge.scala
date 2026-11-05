package services.models.api

import cats.effect.{IO,Resource}
import cats.effect.unsafe.IORuntime
import com.sun.net.httpserver.{HttpServer,HttpHandler,HttpExchange}
import io.circe.{Json,parser}
import io.circe.syntax.*
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import services.models.objects.{ChatCompletionInput,ModelRequestFailure,ModelFailureCode}

// Loopback worker capability, independent of the browser API and its JDBC pool.
object SceneModelBridge:
  def resource(client: ChatCompletionClient)(using IORuntime): Resource[IO,Json] =
    Resource.make(IO.blocking {
      val bytes=new Array[Byte](32);new java.security.SecureRandom().nextBytes(bytes)
      val token=java.util.HexFormat.of().formatHex(bytes)
      val server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),8)
      val executor=Executors.newFixedThreadPool(2)
      server.setExecutor(executor)
      server.createContext("/complete",new HttpHandler:
        def handle(exchange: HttpExchange): Unit =
          val action=for
            payload <- IO.blocking {
              require(exchange.getRequestURI.toString=="/complete" && exchange.getRequestMethod=="POST")
              require(exchange.getRequestHeaders.getFirst("X-Scene-Transport")==token)
              val data=exchange.getRequestBody.readNBytes(10*1024*1024+1)
              require(data.length<=10*1024*1024)
              val json=parser.parse(new String(data,java.nio.charset.StandardCharsets.UTF_8)).toOption.get
              val input=json.hcursor.downField("input").as[ChatCompletionInput].toOption.get
              val max=json.hcursor.get[Int]("maxTokens").toOption.get
              require(max>0 && max<=6000 && input.history.isEmpty && input.system.length<=100000 && input.user.length<=1000000)
              (input,max)
            }
            result <- client.completeScene(payload._1,payload._2)
            _ <- IO.blocking {
              val body=result.asJson.noSpaces.getBytes(java.nio.charset.StandardCharsets.UTF_8)
              exchange.getResponseHeaders.set("Content-Type","application/json")
              exchange.getResponseHeaders.set("Cache-Control","no-store")
              exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody.write(body)
            }
          yield ()
          action.handleErrorWith {
            case failure: ModelRequestFailure => IO.blocking {
              val body=Json.obj("error"->failure.asJson).noSpaces.getBytes(java.nio.charset.StandardCharsets.UTF_8)
              exchange.getResponseHeaders.set("Content-Type","application/json")
              exchange.getResponseHeaders.set("Cache-Control","no-store")
              exchange.sendResponseHeaders(if failure.code==ModelFailureCode.Timeout then 504 else 502,body.length)
              exchange.getResponseBody.write(body)
            }
            case _ => IO.blocking { exchange.sendResponseHeaders(503,-1);() }
          }.guarantee(IO.blocking(exchange.close())).unsafeRunSync()
      )
      server.start()
      (server,executor,Json.obj("port"->Json.fromInt(server.getAddress.getPort),"token"->Json.fromString(token)))
    }) { case (server,executor,_)=>IO.blocking { server.stop(0);executor.shutdownNow();() } }.map(_._3)
