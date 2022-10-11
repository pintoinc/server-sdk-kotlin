package io.livekit.server

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.spec.SecretKeySpec

@Suppress("MemberVisibilityCanBePrivate", "unused")
class AccessToken(
    private val apiKey: String,
    private val secret: String
) {
    private val videoGrants = mutableSetOf<VideoGrant>()

    /**
     * Amount of time in milliseconds before expiration
     *
     * Defaults to 6 hours.
     */
    var ttl: Long = TimeUnit.MILLISECONDS.convert(6, TimeUnit.HOURS)

    /**
     * Date specifying the time [before which this token is invalid](https://tools.ietf.org/html/draft-ietf-oauth-json-web-token-25#section-4.1.5).
     *
     * Defined in milliseconds since epoch time.
     *
     */
    var notBefore: Long? = null

    /**
     * Display name for the participant, available as `Participant.name`
     */
    var name: String? = null

    /**
     * Unique identity of the user, required for room join tokens
     */
    var identity: String? = null

    /**
     * Custom metadata to be passed to participants
     */
    var metadata: String? = null

    /**
     * For verifying integrity of message body
     */
    var sha256: String? = null

    /**
     * Add [VideoGrant] to this token.
     */
    fun addGrants(vararg grants: VideoGrant) {
        for (grant in grants) {
            videoGrants.add(grant)
        }
    }

    /**
     * Add [VideoGrant] to this token.
     */
    fun addGrants(grants: Iterable<VideoGrant>) {
        for (grant in grants) {
            videoGrants.add(grant)
        }
    }

    fun clearGrants() {
        videoGrants.clear()
    }

    fun toJwt(): String {
        return with(Jwts.builder()) {
            setIssuer(apiKey)
            setExpiration(Date(System.currentTimeMillis() + ttl))
            val nbf = notBefore
            if (nbf != null) {
                setNotBefore(Date(nbf))
            }

            val id = identity
            if (id != null) {
                setSubject(id)
                setId(id)
            } else {
                val hasRoomJoin = videoGrants.any { it is RoomJoin && it.value == true }
                if (hasRoomJoin) {
                    throw IllegalStateException("identity is required for join, but is not set.")
                }
            }
            val claimsMap = mutableMapOf<String, Any>()
            val videoGrantsMap = videoGrants.associate { grant -> grant.toPair() }

            name?.let { claimsMap["name"] = it }
            metadata?.let { claimsMap["metadata"] = it }
            sha256?.let { claimsMap["sha256"] = it }
            claimsMap["video"] = videoGrantsMap

            addClaims(claimsMap)
            signWith(
                SecretKeySpec(secret.toByteArray(), "HmacSHA256"),
                SignatureAlgorithm.HS256
            )

            // Build token
            compact()
        }
    }

}