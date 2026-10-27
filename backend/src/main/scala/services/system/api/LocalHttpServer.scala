package services.system.api
import cats.effect.{IO,Resource}
import cats.effect.unsafe.IORuntime
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import services.accounts.routes.AccountsRoutes
import services.games.routes.GamesRoutes
object LocalHttpServer:
  def resource(databaseUrl: String,port: Int)(using IORuntime): Resource[IO,HttpServer] =
    Resource.make(IO.blocking {
      val server = HttpServer.create(new InetSocketAddress("127.0.0.1",port),32)
      val executor = Executors.newFixedThreadPool(8)
      try
        server.setExecutor(executor)
        server.createContext("/api/",APIMessageRouter.handler(databaseUrl,AccountsRoutes.apiMessages ++ GamesRoutes.apiMessages,server.getAddress.getPort))
        server.start()
        (server,executor)
      catch
        case error: Throwable => server.stop(0); executor.shutdownNow(); throw error
    }) { case (server,executor) => IO.blocking { server.stop(1); executor.shutdownNow(); () } }.map(_._1)
