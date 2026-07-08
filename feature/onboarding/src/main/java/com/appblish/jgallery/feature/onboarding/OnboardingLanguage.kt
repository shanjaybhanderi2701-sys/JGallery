package com.appblish.jgallery.feature.onboarding

/**
 * Languages offered on the first onboarding screen (spec §9.1). "Default" follows the system locale;
 * the rest are explicit picks. [tag] is an IETF BCP-47 language tag ("" = system default) and is what
 * we persist. Actual string localisation is out of Wave-1 scope — this only records the preference.
 */
enum class OnboardingLanguage(val tag: String, val displayName: String, val endonym: String) {
    SystemDefault("", "Default", "System language"),
    English("en", "English", "English"),
    Spanish("es", "Spanish", "Español"),
    Hindi("hi", "Hindi", "हिन्दी"),
    Arabic("ar", "Arabic", "العربية"),
    French("fr", "French", "Français"),
    Portuguese("pt", "Portuguese", "Português"),
    German("de", "German", "Deutsch");

    companion object {
        fun fromTag(tag: String?): OnboardingLanguage =
            entries.firstOrNull { it.tag == tag } ?: SystemDefault
    }
}
