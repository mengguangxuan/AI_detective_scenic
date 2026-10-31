package services.models

import io.circe.syntax.*
import java.nio.file.{Files,Paths}
import cats.effect.unsafe.implicits.global
import munit.FunSuite
import services.models.objects.ModelApiSettings
import services.models.utils.ModelSettingsLoader

class ModelSettingsSuite extends FunSuite:
  private val tokenHub=ModelApiSettings("https://tokenhub.tencentmaas.com/v1","dummy-file-key","deepseek-v4.1-flash",20,1024,true)
  private def resolve(settings: ModelApiSettings,values: Map[String,String])=ModelSettingsLoader.resolve(settings.asJson.noSpaces,values.get)

  test("TokenHub uses its own environment variable before the local file and never picks up a direct-provider key") {
    val variables=Map("TOKENHUB_API_KEY" -> " dummy-tokenhub-env ","DETECTIVE_MODEL_API_KEY" -> "dummy-old-provider")
    assertEquals(resolve(tokenHub,variables).apiKey,"dummy-tokenhub-env")
    assertEquals(resolve(tokenHub,Map("DETECTIVE_MODEL_API_KEY" -> "dummy-old-provider")).apiKey,"dummy-file-key")
    assertEquals(resolve(tokenHub,Map("TOKENHUB_API_KEY" -> " ")).apiKey,"dummy-file-key")
    intercept[IllegalArgumentException](resolve(tokenHub.copy(apiKey=""),Map("DETECTIVE_MODEL_API_KEY" -> "dummy-old-provider")))
  }

  test("documented TokenHub hosts keep key isolation while other compatible services retain the generic override") {
    for host <- Vector("tokenhub.tencentmaas.com","tokenhub.tencentmaas.cn","tokenhub-intl.tencentmaas.com","tokenhub-intl.tencentmaas.cn") do
      assertEquals(resolve(tokenHub.copy(baseUrl=s"https://$host/v1"),Map("TOKENHUB_API_KEY" -> "dummy-hub","DETECTIVE_MODEL_API_KEY" -> "dummy-direct")).apiKey,"dummy-hub")
    for host <- Vector("api.deepseek.com","tokenhub.tencentmaas.com.example.org") do
      assertEquals(resolve(tokenHub.copy(baseUrl=s"https://$host/v1"),Map("TOKENHUB_API_KEY" -> "dummy-hub","DETECTIVE_MODEL_API_KEY" -> "dummy-direct")).apiKey,"dummy-direct")
  }

  test("student template leaves the key empty and loads after a key is supplied; malformed settings do not reveal secrets") {
    val source=Files.readString(Paths.get("config/model-api.example.json"))
    val settings=ModelSettingsLoader.resolve(source,Map("TOKENHUB_API_KEY" -> "dummy-student-key").get)
    assertEquals(settings.baseUrl,"https://tokenhub.tencentmaas.com/v1")
    assertEquals(settings.model,"deepseek-v4.1-flash")
    assertEquals(settings.maxTokens,1024)
    intercept[IllegalArgumentException](ModelSettingsLoader.resolve(source,_ => None))
    assertEquals(io.circe.parser.parse(source).toOption.get.hcursor.get[String]("apiKey"),Right(""))
    for invalid <- Vector(tokenHub.copy(baseUrl="http://example.org/v1"),tokenHub.copy(apiKey="dummy-secret\nInjected"),tokenHub.copy(timeoutSeconds=0)) do
      val failure=intercept[IllegalArgumentException](resolve(invalid,Map.empty))
      assert(!failure.getMessage.contains("dummy-secret"))
  }
