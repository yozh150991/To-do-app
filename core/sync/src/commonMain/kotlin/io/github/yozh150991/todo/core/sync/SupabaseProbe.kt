package io.github.yozh150991.todo.core.sync

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.minimalConfig
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Ф0 supabase-kt module matrix (spec §3.3.1): Auth, PostgREST (with RPC and RLS) and Realtime are
 * each checked on their own against a real Supabase stack, from Android, the JVM and the browser.
 * It works on the probe table of `supabase/migrations` only; the product schema and the sync engine
 * are phase Ф5a. Every check is recorded even when an earlier one fails.
 */
object SupabaseProbe {
    data class Check(val module: String, val name: String, val passed: Boolean, val details: String) {
        override fun toString(): String = "$module / $name: ${if (passed) "pass" else "FAIL"} — $details"
    }

    @Serializable
    private data class NewProbeRow(val content: String)

    @Serializable
    private data class ProbeRow(
        val id: String,
        val content: String,
        @SerialName("owner_id") val ownerId: String,
    )

    suspend fun run(url: String, anonKey: String, platform: String): List<Check> {
        val checks = mutableListOf<Check>()
        suspend fun check(module: String, name: String, block: suspend () -> Pair<Boolean, String>) {
            val result = runCatching { block() }
            checks += result.fold(
                onSuccess = { (passed, details) -> Check(module, name, passed, details) },
                onFailure = { e -> Check(module, name, false, "${e::class.simpleName}: ${e.message?.take(300)}") },
            )
        }

        val client = createSupabaseClient(supabaseUrl = url, supabaseKey = anonKey) {
            // Session in memory: the probe must not leave a stored login on the device or in the browser.
            install(Auth) { minimalConfig() }
            install(Postgrest)
            install(Realtime)
        }
        val run = Uuid.random().toString().take(8)
        val email = "f0-probe-$run@example.com"
        val password = "probe-$run-${Uuid.random()}"
        val content = "Ф0 probe $platform $run"
        var userId = ""

        check("Auth", "sign up with email and password (auto-confirmed locally)") {
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            userId = client.auth.currentUserOrNull()?.id.orEmpty()
            (userId.isNotEmpty()) to "user $userId"
        }
        check("Auth", "sign out and sign in again") {
            client.auth.signOut()
            val signedOut = client.auth.currentUserOrNull() == null
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val again = client.auth.currentUserOrNull()?.id
            (signedOut && again == userId) to "signed out: $signedOut, signed in as $again"
        }
        check("PostgREST", "insert returns the row with owner = auth.uid()") {
            val row = client.from("f0_probe").insert(NewProbeRow(content)) { select() }.decodeSingle<ProbeRow>()
            (row.ownerId == userId && row.content == content) to "row ${row.id} owner ${row.ownerId}"
        }
        check("PostgREST", "select with a filter") {
            val rows = client.from("f0_probe").select { filter { eq("content", content) } }.decodeList<ProbeRow>()
            (rows.size == 1) to "${rows.size} row(s)"
        }
        check("PostgREST", "RLS shows only own rows") {
            val rows = client.from("f0_probe").select().decodeList<ProbeRow>()
            val foreign = rows.count { it.ownerId != userId }
            (rows.isNotEmpty() && foreign == 0) to "${rows.size} visible, $foreign of other users"
        }
        check("PostgREST", "RPC f0_echo") {
            val reply = client.postgrest.rpc("f0_echo", buildJsonObject { put("message", "Привіт, świat") })
                .decodeAs<JsonObject>()
            val message = reply["message"]?.jsonPrimitive?.content
            val uid = reply["uid"]?.jsonPrimitive?.content
            (message == "Привіт, świat" && uid == userId) to "message '$message', uid $uid"
        }
        check("Realtime", "postgres_changes INSERT delivered over WebSocket") {
            coroutineScope {
                val marker = "$content realtime"
                val channel = client.channel("f0-probe-$run")
                val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "f0_probe" }
                val received = async {
                    inserts.first { it.record["content"]?.jsonPrimitive?.content == marker }
                }
                channel.subscribe(blockUntilSubscribed = true)
                // postgres_changes is registered a moment after "subscribed"; retry the insert a few times.
                var attempts = 0
                var action: PostgresAction.Insert? = null
                while (action == null && attempts < 3) {
                    attempts++
                    delay(1.seconds)
                    client.from("f0_probe").insert(NewProbeRow(marker))
                    action = withTimeoutOrNull(10.seconds) { received.await() }
                }
                received.cancel()
                client.realtime.removeChannel(channel)
                (action != null) to "event ${if (action != null) "received" else "not received"} after $attempts insert(s)"
            }
        }
        check("PostgREST", "anonymous user cannot read rows (RLS + grants)") {
            client.auth.signOut()
            val result = runCatching { client.from("f0_probe").select().decodeList<ProbeRow>() }
            val passed = result.isFailure || result.getOrNull().isNullOrEmpty()
            passed to (result.exceptionOrNull()?.let { "refused: ${it::class.simpleName}" } ?: "${result.getOrNull()?.size} rows")
        }
        client.close()
        return checks
    }
}
