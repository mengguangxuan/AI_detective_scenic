package services.games.utils

import io.circe.Json
import io.circe.syntax.*
import services.games.objects.InterviewContext
import services.models.objects.ChatCompletionInput

private[games] object FocusedInterviewPrompt:
  private val rules = """
    |本轮使用焦点协议，以下输出格式替代前述两字段／三字段格式。仍只调用一次模型。
    |activeFocus是本人上一轮保存的对话焦点、未答问题及其来源序号，只是对话记录，不是证据或客观真相。pendingQuestion非空时，先判断玩家是在回答、表示不知道、澄清所指，还是明确换题；已回答或明确换题就不再机械追问旧问题。不要因收到回答就自动相信或改口。
    |焦点不确定时允许询问。若“为什么”等短句能对应多个意思，先用一句自然反问澄清具体所指，等待玩家下一轮回答，不先猜一个意思并展开解释。比如你刚说完“牧师叫我去请医生”，玩家问为什么，可以问“你是问他为什么让我去请医生吗？”，不可突然解释更早的声音来源。这个例子仅说明承接方式，不能成为每个人都经历过的故事。
    |澄清要简短、符合本人语气，指出具体不明之处；不能每轮都追问。玩家已经指出具体事项后就接着回答，不再次要求他选同一件事。不用玩家选项按钮、不输出心理分析。澄清期间不主动附带新证据、新供述或暗示答案。
    |focus.topic从本轮最终reply逐字摘取一段具体话题，1—160字。focus.pendingQuestion：若本轮确实向玩家发问、还在等待回答，逐字摘取reply中的完整问题，1—500字；没有待答问题则为null，不沿用已经回答的旧问题。focus.clarification：本轮是否在为理解玩家的指代／话题而澄清；true时pendingQuestion必须非空，disclosureIds和statementUpdates必须都为空。一般为保护目的试探警方态度不等于clarification。
    |focus只是本次实际发言的引用，不得用来存储未说出口的内心、秘密或新事实。topic与pendingQuestion都必须是reply中的连续原文，不能另写摘要。服务端会校验引用，澄清不登记证据或改变供述。
    |严格输出四字段JSON：{"reply":"本人本轮实际发言","disclosureIds":[],"statementUpdates":[],"focus":{"topic":"reply中具体话题的原文","pendingQuestion":null,"clarification":false}}。不要缺字段、增加字段或输出Markdown；原披露白名单、说法更新与证据权限继续生效。
    |""".stripMargin

  private[games] def input(context: InterviewContext, user: Json): ChatCompletionInput =
    val original=DialogueContinuityPrompt.groundedInput(context.copy(responseVersion=3),user)
    val current=io.circe.parser.parse(original.user).toOption.get.deepMerge(Json.obj(
      "activeFocus" -> context.activeFocus.asJson,
      "focusInstruction" -> "先结合activeFocus和最近问答确定本轮所指；不确定就只澄清具体歧义，等待玩家回答。按四字段焦点协议记录这次实际发言。".asJson))
    original.copy(system=original.system+rules,user=current.noSpaces)
