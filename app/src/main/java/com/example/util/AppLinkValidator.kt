package com.example.util

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class DownloadDestinationType(val label: String) {
    GOOGLE_PLAY("Google Play Store"),
    FIREBASE_APP_DISTRIBUTION("Firebase App Distribution"),
    HTTPS_WEB_HOST("Verified HTTPS Web Page"),
    UNCONFIGURED("Not Configured"),
    INVALID("Invalid or Temporary URL")
}

data class UrlValidationResult(
    val isValid: Boolean,
    val normalizedUrl: String,
    val destinationType: DownloadDestinationType,
    val errorMessage: String? = null
)

object AppLinkValidator {

    private val INVITE_CODE_REGEX = Regex("^[A-Za-z0-9_-]{4,64}$")
    private val HOST_REGEX = Regex("^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun sanitizeInviteCode(raw: String): String? {
        val trimmed = raw.trim()
        return if (INVITE_CODE_REGEX.matches(trimmed)) trimmed else null
    }

    fun validatePublicDownloadUrl(rawUrl: String): UrlValidationResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return UrlValidationResult(
                isValid = false,
                normalizedUrl = "",
                destinationType = DownloadDestinationType.UNCONFIGURED,
                errorMessage = "No public download URL is configured yet. Enter a Google Play listing, Firebase App Distribution release link, or a hosted HTTPS download page."
            )
        }
        if (trimmed.any { it.isWhitespace() || it.isISOControl() }) {
            return UrlValidationResult(
                isValid = false,
                normalizedUrl = "",
                destinationType = DownloadDestinationType.INVALID,
                errorMessage = "Download URL must not contain spaces or control characters."
            )
        }
        if (!trimmed.startsWith("https://", ignoreCase = true)) {
            return UrlValidationResult(
                isValid = false,
                normalizedUrl = "",
                destinationType = DownloadDestinationType.INVALID,
                errorMessage = "Public app download URLs must start with https://."
            )
        }

        val parsed = runCatching { URI(trimmed) }.getOrNull()
        val host = parsed?.host?.trim()?.lowercase().orEmpty()
        if (parsed == null || host.isEmpty() || !HOST_REGEX.matches(host) ||
            host == "localhost" || host.startsWith("127.") || host.startsWith("10.")
        ) {
            return UrlValidationResult(
                isValid = false,
                normalizedUrl = "",
                destinationType = DownloadDestinationType.INVALID,
                errorMessage = "Enter a valid public HTTPS domain (for example, play.google.com or your verified school domain)."
            )
        }

        if (isTemporaryPreviewHost(host)) {
            return UrlValidationResult(
                isValid = false,
                normalizedUrl = "",
                destinationType = DownloadDestinationType.INVALID,
                errorMessage = "Temporary preview URLs (*.run.app) do not serve an Android package. Use a Google Play, Firebase App Distribution, or hosted HTTPS download link."
            )
        }

        val normalized = "https://" + trimmed.substring("https://".length)
        val destinationType = when {
            host == "play.google.com" -> DownloadDestinationType.GOOGLE_PLAY
            host == "appdistribution.firebase.google.com" ||
                host == "appdistribution.firebase.dev" -> DownloadDestinationType.FIREBASE_APP_DISTRIBUTION
            else -> DownloadDestinationType.HTTPS_WEB_HOST
        }

        return UrlValidationResult(
            isValid = true,
            normalizedUrl = normalized,
            destinationType = destinationType,
            errorMessage = null
        )
    }

    fun buildShareableUrl(baseUrl: String, inviteCode: String = ""): UrlValidationResult {
        val baseCheck = validatePublicDownloadUrl(baseUrl)
        if (!baseCheck.isValid) return baseCheck

        val trimmedInvite = inviteCode.trim()
        if (trimmedInvite.isEmpty()) return baseCheck

        val safeInvite = sanitizeInviteCode(trimmedInvite)
            ?: return UrlValidationResult(
                isValid = false,
                normalizedUrl = "",
                destinationType = DownloadDestinationType.INVALID,
                errorMessage = "Invite codes must be 4–64 characters using only letters, numbers, '-' or '_'."
            )

        val uri = URI(baseCheck.normalizedUrl)
        val existingParams = uri.rawQuery
            ?.split("&")
            ?.filter { it.isNotBlank() && !it.startsWith("invite=", ignoreCase = true) }
            .orEmpty()
        val encodedInvite = URLEncoder.encode(safeInvite, StandardCharsets.UTF_8.name())
        val finalQuery = (existingParams + "invite=$encodedInvite").joinToString("&")

        val rebuilt = URI(
            uri.scheme,
            uri.rawAuthority,
            uri.rawPath,
            finalQuery,
            uri.rawFragment
        ).toASCIIString()

        return UrlValidationResult(
            isValid = true,
            normalizedUrl = rebuilt,
            destinationType = baseCheck.destinationType,
            errorMessage = null
        )
    }

    fun isTemporaryPreviewHost(host: String): Boolean {
        val lower = host.lowercase()
        return lower.contains("ais-pre-") || (lower.endsWith(".run.app") && lower.contains("-"))
    }

    fun resolvePreferredDownloadUrl(
        primaryUrl: String,
        playStoreUrl: String,
        appStoreUrl: String,
        apkDownloadUrl: String,
        inviteCode: String = ""
    ): UrlValidationResult {
        val candidates = listOf(primaryUrl, playStoreUrl, apkDownloadUrl, appStoreUrl)
        for (candidate in candidates) {
            if (candidate.isNotBlank()) {
                val checked = buildShareableUrl(candidate, inviteCode)
                if (checked.isValid) return checked
            }
        }
        return buildShareableUrl(primaryUrl, inviteCode)
    }

    fun buildWhatsAppShareUri(shareUrl: String, invitationHeadline: String): String {
        val text = buildString {
            append(invitationHeadline.trim())
            if (shareUrl.isNotBlank()) {
                append("\n\n")
                append(shareUrl.trim())
            }
        }
        val encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.name())
        return "https://wa.me/?text=$encoded"
    }

    fun isDirectApkOrReleaseUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        if (!lower.startsWith("https://")) return false
        return lower.contains(".apk") ||
            lower.contains("github.com/") && lower.contains("/releases") ||
            lower.contains("firebasestorage.googleapis.com") ||
            lower.contains("appdistribution.firebase.")
    }

    fun installMethodHint(result: UrlValidationResult): String = when (result.destinationType) {
        DownloadDestinationType.GOOGLE_PLAY ->
            "Opens Google Play Store on Android phones, tablets, and Chromebooks."
        DownloadDestinationType.FIREBASE_APP_DISTRIBUTION ->
            "No Google Play Store needed: recipients open this Firebase link in their browser, sign in, and install directly."
        DownloadDestinationType.HTTPS_WEB_HOST ->
            if (isDirectApkOrReleaseUrl(result.normalizedUrl)) {
                "Direct APK install (No Play Store required): download the APK, allow browser installs if prompted, and tap Install."
            } else {
                "Hosted HTTPS web page (No Play Store required): opens on phones, tablets, and computers to download or launch SafeTrack."
            }
        DownloadDestinationType.UNCONFIGURED ->
            "You can distribute without Google Play Store using Firebase App Distribution, a GitHub Releases / school HTTPS link to SafeTrack.apk, or the Share APK File button on an installed device."
        DownloadDestinationType.INVALID ->
            result.errorMessage ?: "Enter a valid public https:// URL."
    }
}
