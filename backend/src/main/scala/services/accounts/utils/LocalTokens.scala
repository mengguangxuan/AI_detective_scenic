package services.accounts.utils
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
private[accounts] object LocalTokens:
  private[accounts] def hash(token: String): String =
    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)).map(b => f"${b & 0xff}%02x").mkString
  private[accounts] def valid(token: String): Boolean = token.matches("[a-f0-9]{64}")
