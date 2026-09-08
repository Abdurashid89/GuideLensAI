package com.guidelens.ai.ai

import com.guidelens.ai.model.ActionType
import com.guidelens.ai.model.GuideStep
import com.guidelens.ai.model.GuideTask

data class DiagnosisResult(
    val prompt: String,
    val problemTitleUz: String,
    val rootCauseUz: String,
    val recommendedPackage: String? = null,
    val task: GuideTask,
    val isAiGenerated: Boolean = false
)

class UniversalAIEngine(val geminiClient: GeminiVisionClient = GeminiVisionClient()) {

    suspend fun analyzeUserProblem(prompt: String): DiagnosisResult {
        val q = prompt.trim()
        val low = q.lowercase()

        return when {
            // ── Brauzer / Web ──────────────────────────────────────────────────────
            // Telegram web check MUST come before the general "telegram" check
            low.has("telegram") && low.has("web", "veb", "chrome", "brauzer", "browser",
                "kompyuter", "pk", "desktop", "qr kod", "qr scan", "skaner") ->
                telegramWebTask(q)

            low.has("chrome", "brauzer", "browser", "internet oching", "saytga kir",
                "veb sayt", "web sayt", "internetga kir") ->
                chromeTask(q)

            // ── Xotira ────────────────────────────────────────────────────────────
            low.has("xotira", "storage", "saqlash", "to'ldi", "to'lib", "joy yo'q", "memory full") ->
                storageTask(q)

            // ── Tarmoq ────────────────────────────────────────────────────────────
            low.has("wi-fi", "wifi", "internet ulanmayapti", "tarmoq muammo") ->
                wifiTask(q)

            low.has("hotspot", "modem", "internet ulash", "wi-fi ulash") ->
                hotspotTask(q)

            low.has("bluetooth", "quloqchin", "kolontka", "speaker") ->
                bluetoothTask(q)

            // ── Ilovalar ──────────────────────────────────────────────────────────
            low.has("instagram") && low.has("2fa", "xavfsizlik", "himoya", "security") ->
                instagram2faTask(q)

            low.has("instagram") && low.has("ro'yxat", "kirish", "login", "akkaunt", "register") ->
                instagramRegTask(q)

            low.has("instagram") ->
                playStoreInstallTask(q, "Instagram")

            low.has("telegram") ->
                telegramTask(q)

            low.has("whatsapp", "watsap") ->
                whatsappTask(q)

            low.has("tiktok", "tik tok") ->
                playStoreInstallTask(q, "TikTok")

            low.has("facebook", "fb.com", "feysbook") ->
                facebookTask(q)

            low.has("gmail", "pochta", "email", "google mail") ->
                gmailTask(q)

            low.has("youtube", "yt") && low.has("yukla", "saqla", "download", "video yukla") ->
                youtubeDlTask(q)

            low.has("youtube", "yt", "video ko'r", "video qara", "kino ko'r") ->
                youtubeTask(q)

            // ── To'lov / Bank ─────────────────────────────────────────────────────
            low.has("payme", "click", "uzum bank", "hamkorbank", "kapitalbank",
                "pul o'tkaz", "pul yubor", "to'lov") && !low.has("install", "o'rnat") ->
                paymentAppTask(q)

            // ── Davlat xizmatlari ─────────────────────────────────────────────────
            low.has("mygov", "my gov", "egov", "hukumat xizmat") ->
                mygovTask(q)

            low.has("soliq", "keshbek", "chek skan") ->
                soliqTask(q)

            // ── Telefon sozlamalari ───────────────────────────────────────────────
            low.has("bildirishnoma", "notification", "reklama bildirishnoma", "spam") ->
                notificationTask(q)

            low.has("shrift", "font", "yozuv katta", "matn katta", "kichik yozuv") ->
                fontSizeTask(q)

            low.has("til o'zgartir", "language", "язык", "til sozla") ->
                languageTask(q)

            low.has("dark mode", "tungi rejim", "mavzu", "theme", "qorong'u", "rang") ->
                darkModeTask(q)

            low.has("ekran o'chishi", "timeout", "ekran vaqti", "display timeout", "sleep") ->
                screenTimeoutTask(q)

            low.has("batareya", "battery", "quvvat tejash", "zaryad", "battery saver") ->
                batteryTask(q)

            low.has("kamera sozla", "camera sett", "surat sifat", "video sifat") ->
                cameraTask(q)

            low.has("parol", "pin", "ekran qulfi", "screen lock", "barmoq izi", "fingerprint") ->
                screenLockTask(q)

            // ── O'rnatish ─────────────────────────────────────────────────────────
            low.has("o'rnat", "yuklab", "install", "play store") &&
                    !low.has("telegram", "whatsapp", "instagram") ->
                playStoreTask(q)

            else -> geminiOrFallback(q)
        }
    }

    private fun String.has(vararg kws: String) = kws.any { this.contains(it) }

    private suspend fun geminiOrFallback(query: String): DiagnosisResult {
        if (geminiClient.hasApiKey()) {
            val aiTask = geminiClient.planTaskFromPrompt(query)
            if (aiTask != null) {
                return DiagnosisResult(
                    prompt = query,
                    problemTitleUz = aiTask.titleUz,
                    rootCauseUz = "🤖 AI tahlil: '$query' so'roviga mos yo'riqnoma tayyorlandi.",
                    recommendedPackage = aiTask.steps.firstOrNull()?.appPackage,
                    task = aiTask,
                    isAiGenerated = true
                )
            }
        }
        // If the query looks like an app install request, guide through Play Store
        val low = query.lowercase()
        if (low.has("o'rnat", "yuklab", "install", "скачать", "ilova", "app", "dastur")) {
            val appName = query.replace(Regex("(?i)o'rnat.*|yuklab.*|install.*|скачать.*|ilova.*|app.*|dastur.*|ber|menga|yordam"), "").trim()
            return playStoreInstallTask(query, appName.ifBlank { query })
        }
        // Unknown query: show a clear message without opening any app
        return DiagnosisResult(
            prompt = query,
            problemTitleUz = "Yordam: \"$query\"",
            rootCauseUz = "⚠️ Bu buyruq aniqlanmadi. Quyidagi tayyor vazifalardan foydalaning yoki muammoni batafsil yozing. Gemini API key qo'shing — AI yanada aqlli ishlaydi.",
            recommendedPackage = null,
            task = GuideTask(
                id = "dyn_custom_${System.currentTimeMillis()}",
                titleUz = "Yordam: $query",
                descriptionUz = query,
                category = "AI Yordamchi",
                iconResName = "smart_display",
                steps = listOf(
                    GuideStep(1, "Ko'rsatma",
                        "❓ Buyruq aniqlanmadi. Pastdagi tayyor vazifalardan birini tanlang yoki muammoni aniqroq yozing (masalan: 'Telegram o'rnat', 'Wi-Fi ulash', 'Dark mode yoq').",
                        ActionType.CLICK_TARGET, listOf("menu", "settings", "sozlamalar"))
                )
            )
        )
    }

    private fun playStoreInstallTask(q: String, appName: String) = DiagnosisResult(q,
        "Play Store orqali O'rnatish",
        "Google Play Store dan '$appName' ilovasini topib o'rnatish.",
        "com.android.vending",
        GuideTask("dyn_playstore_${System.currentTimeMillis()}", "Ilova O'rnatish", "Play Store orqali o'rnatish.", "Ilovalar", "get_app", listOf(
            GuideStep(1, "Play Store", "🏪 Google Play Store ni oching.", ActionType.OPEN_APP, listOf("play store", "market", "Google Play"), "com.android.vending"),
            GuideStep(2, "Qidiruv", "🔍 Ekran tepasidagi qidiruv (Search) maydoniga bosing.", ActionType.CLICK_TARGET, listOf("search", "qidiruv", "search for apps", "apps & games", "поиск")),
            GuideStep(3, "Nom Yozing", "⌨️ Qidiruv maydoniga '$appName' deb yozing.", ActionType.INPUT_TEXT, listOf(appName.lowercase(), "search", "input")),
            GuideStep(4, "Ilovani Tanlang", "📱 Natijalar ichidan kerakli ilovani bosing.", ActionType.CLICK_TARGET, listOf(appName.lowercase(), "app", "ilova")),
            GuideStep(5, "O'rnatish", "📥 'O'rnatish' (Install) tugmasini bosing.", ActionType.CLICK_TARGET, listOf("install", "o'rnatish", "yuklab", "скачать")),
            GuideStep(6, "Ochish", "▶️ O'rnatilgach 'Ochish' (Open) tugmasini bosing.", ActionType.CLICK_TARGET, listOf("ochish", "open", "открыть"))
        ))
    )

    // ── Task builders ──────────────────────────────────────────────────────

    private fun storageTask(q: String) = DiagnosisResult(q,
        "Xotira To'lib Qolishi",
        "SABABI: Kesh fayllar (Telegram/Instagram media) va ishlatilmaydigan ilovalar xotirani to'ldirmoqda.",
        "com.android.settings",
        GuideTask("dyn_storage", "Xotirani Tozalash", "Kesh va ortiqcha fayllarni o'chirish.", "Muammolar", "storage", listOf(
            GuideStep(1, "Sozlamalarni Oching", "⚙️ Sozlamalarga kiring.", ActionType.OPEN_APP, listOf("settings", "sozlamalar"), "com.android.settings"),
            GuideStep(2, "Xotira Bo'limi", "💾 'Xotira' (Storage/Memory) bo'limini bosing.", ActionType.CLICK_TARGET, listOf("xotira", "storage", "память", "накопитель", "saqlash")),
            GuideStep(3, "Tozalash", "🗑️ 'Tozalash' yoki 'Bo'shatish' tugmasini bosing.", ActionType.CLICK_TARGET, listOf("tozalash", "clean", "освободить", "bo'shatish", "free up"))
        ))
    )

    private fun wifiTask(q: String) = DiagnosisResult(q,
        "Wi-Fi Ulanish Muammosi",
        "SABABI: Wi-Fi keshi yoki IP manzili yangilanishi kerak.",
        "com.android.settings",
        GuideTask("dyn_wifi", "Wi-Fi Qayta Ulash", "Wi-Fi ni qayta yoqish.", "Tarmoq", "wifi", listOf(
            GuideStep(1, "Sozlamalar", "⚙️ Sozlamalarga kiring.", ActionType.OPEN_APP, listOf("settings"), "com.android.settings"),
            GuideStep(2, "Wi-Fi Bo'limi", "📶 'Wi-Fi' yoki 'Tarmoq va internet' ni bosing.", ActionType.CLICK_TARGET, listOf("wi-fi", "wifi", "network", "tarmoq", "сеть", "internet")),
            GuideStep(3, "Wi-Fi O'chirib Yoqing", "🔄 Wi-Fi tugmasini o'chirib, qayta yoqing.", ActionType.CLICK_TARGET, listOf("wi-fi", "wifi", "on", "off", "yoqilgan"))
        ))
    )

    private fun instagram2faTask(q: String) = DiagnosisResult(q,
        "Instagram 2FA Xavfsizlik",
        "SABABI: Akkauntingiz ikki bosqichli himoyasiz.",
        "com.instagram.android",
        GuideTask("dyn_insta_2fa", "Instagram 2FA", "Xavfsizlikni oshirish.", "Ilovalar", "security", listOf(
            GuideStep(1, "Instagram", "📸 Instagram ni oching.", ActionType.OPEN_APP, listOf("instagram"), "com.instagram.android"),
            GuideStep(2, "Profil", "👤 Pastki o'ng burchakdagi profil tugmasini bosing.", ActionType.CLICK_TARGET, listOf("profile", "profil", "account")),
            GuideStep(3, "Menyu", "☰ Yuqori o'ng burchakdagi 3 chiziqni bosing.", ActionType.CLICK_TARGET, listOf("menu", "burger", "≡")),
            GuideStep(4, "Sozlamalar", "⚙️ 'Sozlamalar va Maxfiylik' ni bosing.", ActionType.CLICK_TARGET, listOf("sozlamalar", "settings", "privacy")),
            GuideStep(5, "2FA", "🔐 'Xavfsizlik' → 'Ikki bosqichli tasdiqlash' ni yoqing.", ActionType.CLICK_TARGET, listOf("xavfsizlik", "security", "two-factor", "2fa"))
        ))
    )

    private fun instagramRegTask(q: String) = DiagnosisResult(q,
        "Instagram Ro'yxatdan O'tish",
        "Instagram da yangi akkaunt ochish.",
        "com.instagram.android",
        GuideTask("dyn_insta_reg", "Instagram Akkaunt", "Yangi akkaunt yaratish.", "Ilovalar", "account_circle", listOf(
            GuideStep(1, "Instagram", "📸 Instagram ni oching.", ActionType.OPEN_APP, listOf("instagram"), "com.instagram.android"),
            GuideStep(2, "Yangi Akkaunt", "✨ 'Yangi akkaunt yaratish' tugmasini bosing.", ActionType.CLICK_TARGET, listOf("yangi", "create", "sign up", "register", "ro'yxat")),
            GuideStep(3, "Ism Kiriting", "📝 Ismingiz va emailingizni kiriting.", ActionType.INPUT_TEXT, listOf("name", "email", "ism", "first name")),
            GuideStep(4, "Parol", "🔑 Parol yarating va 'Keyingi' ni bosing.", ActionType.CLICK_TARGET, listOf("password", "parol", "next", "keyingi")),
            GuideStep(5, "Tasdiqlash", "✅ Emailga kelgan kodni kiriting.", ActionType.INPUT_TEXT, listOf("code", "tasdiqlash", "confirm", "verify"))
        ))
    )

    private fun telegramTask(q: String) = DiagnosisResult(q,
        "Telegram O'rnatish va Kirish",
        "Play Store dan Telegram yuklab, ro'yxatdan o'tish.",
        "com.android.vending",
        GuideTask("dyn_telegram", "Telegram O'rnatish", "Telegram yuklab o'rnatish va login.", "Ilovalar", "telegram", listOf(
            GuideStep(1, "Play Store", "🏪 Google Play Store ni oching.", ActionType.OPEN_APP, listOf("play store", "market", "Google Play"), "com.android.vending"),
            GuideStep(2, "Qidiruv", "🔍 Ekran tepasidagi qidiruv (Search) maydoniga bosing.", ActionType.CLICK_TARGET, listOf("search", "qidiruv", "search for apps", "apps & games", "поиск")),
            GuideStep(3, "Telegram Yozing", "⌨️ Qidiruv maydoniga 'Telegram' deb yozing va Enter bosing.", ActionType.INPUT_TEXT, listOf("telegram"), textToType = "Telegram"),
            GuideStep(4, "Telegram Tanlang", "📱 Natijalar ichidan 'Telegram' ilovasini bosing.", ActionType.CLICK_TARGET, listOf("telegram")),
            GuideStep(5, "O'rnatish", "📥 'O'rnatish' (Install) tugmasini bosing.", ActionType.CLICK_TARGET, listOf("install", "o'rnatish", "yuklab", "скачать")),
            GuideStep(6, "Ochish", "▶️ O'rnatib bo'lgach 'Ochish' (Open) tugmasini bosing.", ActionType.CLICK_TARGET, listOf("ochish", "open", "открыть")),
            GuideStep(7, "Boshlash", "🚀 'Start Messaging' tugmasini bosing.", ActionType.CLICK_TARGET, listOf("boshlash", "start", "start messaging", "продолжить")),
            GuideStep(8, "Telefon Raqam", "📱 Telefon raqamingizni kiriting.", ActionType.INPUT_TEXT, listOf("phone", "telefon", "number", "номер")),
            GuideStep(9, "SMS Kod", "💬 Telefondan kelgan kodni kiriting.", ActionType.INPUT_TEXT, listOf("code", "kod", "sms", "verify"))
        ))
    )

    private fun whatsappTask(q: String) = DiagnosisResult(q,
        "WhatsApp O'rnatish va Kirish",
        "Play Store dan WhatsApp yuklab, ro'yxatdan o'tish.",
        "com.android.vending",
        GuideTask("dyn_whatsapp", "WhatsApp O'rnatish", "WhatsApp yuklab va login.", "Ilovalar", "chat", listOf(
            GuideStep(1, "Play Store", "🏪 Google Play Store ni oching.", ActionType.OPEN_APP, listOf("play store", "market", "Google Play"), "com.android.vending"),
            GuideStep(2, "Qidiruv", "🔍 Ekran tepasidagi qidiruv (Search) maydoniga bosing.", ActionType.CLICK_TARGET, listOf("search", "qidiruv", "search for apps", "apps & games", "поиск")),
            GuideStep(3, "WhatsApp Yozing", "⌨️ Qidiruv maydoniga 'WhatsApp' deb yozing va Enter bosing.", ActionType.INPUT_TEXT, listOf("whatsapp"), textToType = "WhatsApp"),
            GuideStep(4, "WhatsApp Tanlang", "📱 Natijalar ichidan 'WhatsApp' ilovasini bosing.", ActionType.CLICK_TARGET, listOf("whatsapp")),
            GuideStep(5, "O'rnatish", "📥 'O'rnatish' (Install) tugmasini bosing.", ActionType.CLICK_TARGET, listOf("install", "o'rnatish", "yuklab", "скачать")),
            GuideStep(6, "Ochish", "▶️ 'Ochish' (Open) tugmasini bosing.", ActionType.OPEN_APP, listOf("whatsapp"), "com.whatsapp"),
            GuideStep(7, "Rozi Bo'lish", "✅ 'Agree and Continue' ni bosing.", ActionType.CLICK_TARGET, listOf("agree", "continue", "rozi", "accept")),
            GuideStep(8, "Telefon Raqam", "📱 Mamlakat kodi va raqamingizni kiriting.", ActionType.INPUT_TEXT, listOf("phone", "telefon", "number", "+998")),
            GuideStep(9, "SMS Kod", "💬 SMS dan kelgan 6 xonali kodni kiriting.", ActionType.INPUT_TEXT, listOf("code", "kod", "6-digit", "verify"))
        ))
    )

    private fun notificationTask(q: String) = DiagnosisResult(q,
        "Bildirishnomalarni Sozlash",
        "SABABI: Ba'zi ilovalar ruxsatsiz reklama yubormoqda.",
        "com.android.settings",
        GuideTask("dyn_notif", "Bildirishnomalar", "Keraksiz bildirishnomalarni o'chirish.", "Sozlamalar", "notifications", listOf(
            GuideStep(1, "Sozlamalar", "⚙️ Sozlamalarga kiring.", ActionType.OPEN_APP, listOf("settings"), "com.android.settings"),
            GuideStep(2, "Bildirishnomalar", "🔔 'Bildirishnomalar' (Notifications) bo'limini bosing.", ActionType.CLICK_TARGET, listOf("bildirishnoma", "notification", "уведомления")),
            GuideStep(3, "Ilova Tanlang", "📋 Xalaqit beruvchi ilova nomini toping va bosing.", ActionType.CLICK_TARGET, listOf("app", "ilova", "applications")),
            GuideStep(4, "O'chirish", "🔕 Ilova yonidagi switch ni o'chiring.", ActionType.CLICK_TARGET, listOf("off", "o'chirish", "disable", "switch"))
        ))
    )

    private fun bluetoothTask(q: String) = DiagnosisResult(q,
        "Bluetooth Ulash",
        "Bluetooth yoqish va qurilmaga ulash.",
        "com.android.settings",
        GuideTask("dyn_bt", "Bluetooth Ulash", "Bluetooth qurilmaga ulash.", "Tarmoq", "bluetooth", listOf(
            GuideStep(1, "Sozlamalar", "⚙️ Sozlamalarga kiring.", ActionType.OPEN_APP, listOf("settings"), "com.android.settings"),
            GuideStep(2, "Bluetooth", "📡 'Bluetooth' bo'limini bosing.", ActionType.CLICK_TARGET, listOf("bluetooth", "блютус")),
            GuideStep(3, "Yoqish", "🔵 Bluetooth tugmasini yoqing.", ActionType.CLICK_TARGET, listOf("on", "yoqish", "bluetooth", "enable")),
            GuideStep(4, "Qurilma", "🎧 Ulamoqchi bo'lgan qurilmangiz nomini bosing.", ActionType.CLICK_TARGET, listOf("pair", "ulash", "connect", "device", "available"))
        ))
    )

    private fun hotspotTask(q: String) = DiagnosisResult(q,
        "Hotspot (Modem) Yoqish",
        "Telefonni Wi-Fi modemiga aylantirish.",
        "com.android.settings",
        GuideTask("dyn_hotspot", "Hotspot Yoqish", "Telefondan internet ulashish.", "Tarmoq", "wifi_tethering", listOf(
            GuideStep(1, "Sozlamalar", "⚙️ Sozlamalarga kiring.", ActionType.OPEN_APP, listOf("settings"), "com.android.settings"),
            GuideStep(2, "Tarmoq", "📶 'Tarmoq va internet' yoki 'Ulanishlar' ni bosing.", ActionType.CLICK_TARGET, listOf("tarmoq", "network", "connections", "ulanish")),
            GuideStep(3, "Hotspot", "📡 'Mobil hotspot' yoki 'Tethering' ni bosing.", ActionType.CLICK_TARGET, listOf("hotspot", "tethering", "modem", "portable wifi")),
            GuideStep(4, "Yoqish", "🔥 'Mobil hotspot' tugmasini yoqing.", ActionType.CLICK_TARGET, listOf("mobile hotspot", "on", "yoqish", "enable"))
        ))
    )

    private fun fontSizeTask(q: String) = DiagnosisResult(q,
        "Shrift O'lchamini O'zgartirish",
        "Ekrandagi matn hajmini kattalashtirish.",
        "com.android.settings",
        GuideTask("dyn_font", "Shrift O'lchami", "Matn hajmini o'zgartirish.", "Sozlamalar", "format_size", listOf(
            GuideStep(1, "Sozlamalar", "⚙️ Sozlamalarga kiring.", ActionType.OPEN_APP, listOf("settings"), "com.android.settings"),
            GuideStep(2, "Ekran", "🖥️ 'Ekran' (Display) ni bosing.", ActionType.CLICK_TARGET, listOf("ekran", "display", "displey", "экран")),
            GuideStep(3, "Shrift", "🔡 'Shrift o'lchami' (Font size) ni bosing.", ActionType.CLICK_TARGET, listOf("shrift", "font size", "text size", "размер шрифта", "matn o'lchami")),
            GuideStep(4, "Kattalashtirish", "↔️ Slayderni o'ngga suring yoki 'Katta' ni tanlang.", ActionType.CLICK_TARGET, listOf("large", "katta", "huge", "yirik", "bigger", "xl"))
        ))
    )

    private fun languageTask(q: String) = DiagnosisResult(q,
        "Telefon Tilini O'zgartirish",
        "Interfeys tilini o'zbek, rus yoki ingliz tiliga o'tkazish.",
        "com.android.settings",
        GuideTask("dyn_lang", "Til O'zgartirish", "Telefon tilini o'zgartirish.", "Sozlamalar", "language", listOf(
            GuideStep(1, "Sozlamalar", "⚙️ Sozlamalarga kiring.", ActionType.OPEN_APP, listOf("settings"), "com.android.settings"),
            GuideStep(2, "Umumiy", "🌍 'Umumiy boshqaruv' (General management) yoki 'Tizim' ni bosing.", ActionType.CLICK_TARGET, listOf("general", "umumiy", "system", "boshqaruv", "тизим")),
            GuideStep(3, "Til", "💬 'Til' (Language) bo'limini bosing.", ActionType.CLICK_TARGET, listOf("til", "language", "язык")),
            GuideStep(4, "Til Qo'shing", "➕ 'Til qo'shish' yoki kerakli tilni tanlang.", ActionType.CLICK_TARGET, listOf("add language", "til qo'shish", "o'zbek", "uzbek", "russian", "english"))
        ))
    )

    private fun darkModeTask(q: String) = DiagnosisResult(q,
        "Tungi Rejim / Mavzu O'zgartirish",
        "Telefonning ko'rinishini dark mode yoki mavzuga o'tkazish.",
        "com.android.settings",
        GuideTask("dyn_dark", "Tungi Rejim", "Dark mode yoki mavzu o'zgartirish.", "Sozlamalar", "dark_mode", listOf(
            GuideStep(1, "Sozlamalar", "⚙️ Sozlamalarga kiring.", ActionType.OPEN_APP, listOf("settings"), "com.android.settings"),
            GuideStep(2, "Ekran / Mavzu", "🖥️ 'Ekran' (Display) yoki 'Mavzu'/'Themes' ni bosing.", ActionType.CLICK_TARGET, listOf("ekran", "display", "mavzu", "theme", "wallpaper", "qorong")),
            GuideStep(3, "Dark Mode", "🌙 'Qorong'u rejim' (Dark mode) tugmasini yoqing.", ActionType.CLICK_TARGET, listOf("qorong'u", "dark", "tungi", "dark mode", "dark theme", "night"))
        ))
    )

    private fun screenTimeoutTask(q: String) = DiagnosisResult(q,
        "Ekran O'chish Vaqtini Sozlash",
        "Ekran necha daqiqadan keyin o'chishini belgilash.",
        "com.android.settings",
        GuideTask("dyn_timeout", "Ekran Vaqti", "Ekran o'chish vaqtini o'zgartirish.", "Sozlamalar", "timer", listOf(
            GuideStep(1, "Sozlamalar", "⚙️ Sozlamalarga kiring.", ActionType.OPEN_APP, listOf("settings"), "com.android.settings"),
            GuideStep(2, "Ekran", "🖥️ 'Ekran' (Display) ni bosing.", ActionType.CLICK_TARGET, listOf("ekran", "display", "displey")),
            GuideStep(3, "Ekran Vaqti", "⏱️ 'Ekran o'chish vaqti' (Screen timeout/Sleep) ni bosing.", ActionType.CLICK_TARGET, listOf("timeout", "sleep", "o'chish vaqti", "screen off", "autom")),
            GuideStep(4, "Vaqt Tanlang", "⌛ Kerakli vaqtni tanlang (5 daqiqa tavsiya).", ActionType.CLICK_TARGET, listOf("5 minutes", "5 min", "minutes", "daqiqa"))
        ))
    )

    private fun batteryTask(q: String) = DiagnosisResult(q,
        "Batareya va Quvvat Tejash",
        "Battery saver rejimini yoqish uchun.",
        "com.android.settings",
        GuideTask("dyn_battery", "Quvvat Tejash", "Battery saver rejimini yoqish.", "Sozlamalar", "battery_saver", listOf(
            GuideStep(1, "Sozlamalar", "⚙️ Sozlamalarga kiring.", ActionType.OPEN_APP, listOf("settings"), "com.android.settings"),
            GuideStep(2, "Batareya", "🔋 'Batareya' (Battery) bo'limini bosing.", ActionType.CLICK_TARGET, listOf("batareya", "battery", "аккумулятор", "quvvat")),
            GuideStep(3, "Tejash Rejimi", "💡 'Quvvat tejash' (Battery saver) ni bosing va yoqing.", ActionType.CLICK_TARGET, listOf("tejash", "saver", "saving", "power saving", "battery saver"))
        ))
    )

    private fun mygovTask(q: String) = DiagnosisResult(q,
        "MyGov Ilovasida Kirish",
        "Yagona portal MyGov da xizmatlardan foydalanish.",
        "uz.mygov.app",
        GuideTask("dyn_mygov", "MyGov Kirish", "MyGov da kirish va xizmatlar.", "Davlat Xizmatlari", "account_balance", listOf(
            GuideStep(1, "MyGov", "🏛️ MyGov ilovasini oching.", ActionType.OPEN_APP, listOf("mygov", "my.gov.uz")),
            GuideStep(2, "Kirish", "🔑 'Kirish' (OneID) tugmasini bosing.", ActionType.CLICK_TARGET, listOf("kirish", "login", "oneid", "кириш")),
            GuideStep(3, "JShShIR", "🪪 JShShIR (PINFL) raqamingizni kiriting.", ActionType.INPUT_TEXT, listOf("jshshir", "pinfl", "passport", "inn")),
            GuideStep(4, "Parol", "🔐 Parol yoki SMS kodini kiriting.", ActionType.INPUT_TEXT, listOf("parol", "password", "kod", "sms"))
        ))
    )

    private fun soliqTask(q: String) = DiagnosisResult(q,
        "Soliq Ilovasida Keshbek",
        "Chek skanerlash va keshbek olish.",
        "uz.soliq.app",
        GuideTask("dyn_soliq", "Soliq Keshbek", "Chek skanerlash va keshbek.", "Davlat Xizmatlari", "receipt_long", listOf(
            GuideStep(1, "Soliq", "🧾 Soliq ilovasini oching.", ActionType.OPEN_APP, listOf("soliq")),
            GuideStep(2, "Keshbek", "📷 'Keshbek' yoki kamera tugmasini bosing.", ActionType.CLICK_TARGET, listOf("keshbek", "skaner", "сканер", "chek", "qr", "camera")),
            GuideStep(3, "Skanerlash", "🔍 Kamerani chek QR kodiga qarating.", ActionType.CLICK_TARGET, listOf("scan", "qr", "kod"))
        ))
    )

    private fun cameraTask(q: String) = DiagnosisResult(q,
        "Kamera Sozlamalari",
        "Kamera sifati va video sozlamalarini o'zgartirish.",
        "com.android.camera2",
        GuideTask("dyn_camera", "Kamera Sozlash", "Kamera sozlamalarini o'zgartirish.", "Media", "photo_camera", listOf(
            GuideStep(1, "Kamera", "📷 Kamera ilovasini oching.", ActionType.OPEN_APP, listOf("camera", "kamera"), "com.android.camera2"),
            GuideStep(2, "Sozlamalar", "⚙️ Kamera ichidagi tishli g'ildirak belgisini bosing.", ActionType.CLICK_TARGET, listOf("settings", "sozlamalar", "gear", "⚙")),
            GuideStep(3, "Sifat", "🎯 'Sifat' (Resolution/Quality) ni bosing va tanlang.", ActionType.CLICK_TARGET, listOf("quality", "resolution", "sifat", "mp", "megapixel"))
        ))
    )

    private fun screenLockTask(q: String) = DiagnosisResult(q,
        "Ekran Qulfi va Parol",
        "PIN, naqsh yoki barmoq izi o'rnatish.",
        "com.android.settings",
        GuideTask("dyn_lock", "Ekran Qulfi", "PIN yoki barmoq izi o'rnatish.", "Xavfsizlik", "lock", listOf(
            GuideStep(1, "Sozlamalar", "⚙️ Sozlamalarga kiring.", ActionType.OPEN_APP, listOf("settings"), "com.android.settings"),
            GuideStep(2, "Xavfsizlik", "🔒 'Xavfsizlik' (Security) yoki 'Ekran qulfi' ni bosing.", ActionType.CLICK_TARGET, listOf("xavfsizlik", "security", "ekran qulfi", "lock", "безопасность")),
            GuideStep(3, "Qulf Turi", "🔑 'Ekran qulfi' (Screen lock) turini tanlang.", ActionType.CLICK_TARGET, listOf("screen lock", "qulf", "pin", "pattern", "fingerprint", "barmoq")),
            GuideStep(4, "PIN", "🔢 Yangi PIN raqamingizni kiriting va tasdiqlang.", ActionType.INPUT_TEXT, listOf("pin", "password", "parol", "confirm", "tasdiqlang"))
        ))
    )

    private fun youtubeDlTask(q: String) = DiagnosisResult(q,
        "YouTube Video Yuklab Olish",
        "YouTube video havolasini olib, Telegram bot orqali sifatli yuklab olish.",
        "com.google.android.youtube",
        GuideTask("dyn_ytdl", "YouTube Video Yuklab Olish", "Telegram bot orqali yuklab olish.", "Media", "download", listOf(
            GuideStep(1, "YouTube Oching", "▶️ YouTube ilovasini oching.", ActionType.OPEN_APP, listOf("youtube"), "com.google.android.youtube"),
            GuideStep(2, "Videoni Toping", "🔍 Yuklamoqchi bo'lgan videoni toping va oching.", ActionType.CLICK_TARGET, listOf("video", "watch", "play")),
            GuideStep(3, "Share Tugmasi", "📤 Share (Ulashish) tugmasini bosing — odatda pastki qismda.", ActionType.CLICK_TARGET, listOf("share", "ulashish", "поделиться")),
            GuideStep(4, "Havolani Nusxalash", "🔗 'Nusxalash' (Copy link) ni bosing.", ActionType.CLICK_TARGET, listOf("copy link", "havolani nusxala", "copy url", "link")),
            GuideStep(5, "Telegram Oching", "💬 Telegram ilovasini oching.", ActionType.OPEN_APP, listOf("telegram"), "org.telegram.messenger"),
            GuideStep(6, "Bot Qidiring", "🤖 Qidiruv maydoniga @SaveVideo yoki @YoutubeDL_bot yozing va oching.", ActionType.CLICK_TARGET, listOf("search", "qidiruv", "поиск")),
            GuideStep(7, "Havolani Yuboring", "📨 Nusxalangan YouTube havolasini paste qilib yuboring.", ActionType.CLICK_TARGET, listOf("send", "yuborish", "message", "paste")),
            GuideStep(8, "Yuklab Oling", "⬇️ Bot yuborgan faylni bosing va yuklab oling.", ActionType.CLICK_TARGET, listOf("download", "yuklab", "mp4", "file", "save"))
        ))
    )

    private fun telegramWebTask(q: String) = DiagnosisResult(q,
        "Telegram Web (Chrome orqali)",
        "Chrome brauzeri orqali web.telegram.org da Telegram ochish.",
        "com.android.chrome",
        GuideTask("dyn_tgweb", "Telegram Web Kirish", "Chrome orqali Telegram webga kirish.", "Internet", "language", listOf(
            GuideStep(1, "Chrome", "🌐 Google Chrome brauzerni oching.", ActionType.OPEN_APP, listOf("chrome", "browser"), "com.android.chrome"),
            GuideStep(2, "Manzil Satri", "🔗 Ekran TEPASIDAGI manzil satrini (URL bar) bosing.", ActionType.CLICK_TARGET, listOf("url", "address", "omnibox", "search", "manzil", "address bar", "search or type")),
            GuideStep(3, "Manzil Yozing", "⌨️ 'web.telegram.org' deb yozing va Enter bosing.", ActionType.INPUT_TEXT, listOf("web.telegram.org", "telegram", "url", "search"), textToType = "web.telegram.org"),
            GuideStep(4, "Telefon Raqam", "📱 'Log in by phone Number' ni bosing.", ActionType.CLICK_TARGET, listOf("log in", "phone number", "kirish", "login", "phone", "sign in")),
            GuideStep(5, "Raqam Kiriting", "📞 Telefon raqamingizni kiriting (+998XXXXXXXXX).", ActionType.INPUT_TEXT, listOf("phone", "number", "raqam", "номер", "telefon")),
            GuideStep(6, "Tasdiqlash", "✅ Telegramdan kelgan kodni kiriting.", ActionType.INPUT_TEXT, listOf("code", "kod", "confirm", "verify", "sms"))
        ))
    )

    private fun chromeTask(q: String) = DiagnosisResult(q,
        "Chrome Brauzerni Ochish",
        "Google Chrome orqali internet saytga kirish.",
        "com.android.chrome",
        GuideTask("dyn_chrome", "Chrome Brauzeri", "Internet saytga Chrome orqali kirish.", "Internet", "public", listOf(
            GuideStep(1, "Chrome", "🌐 Google Chrome ni oching.", ActionType.OPEN_APP, listOf("chrome", "browser", "brauzer"), "com.android.chrome"),
            GuideStep(2, "Manzil", "🔗 Ekran tepasidagi manzil satrini bosing.", ActionType.CLICK_TARGET, listOf("url", "address", "search", "omnibox", "manzil", "search or type", "address bar")),
            GuideStep(3, "Yozing", "⌨️ Sayt manzilini yoki qidiruv so'zini yozing va Enter bosing.", ActionType.INPUT_TEXT, listOf("url", "search", "address", "query", "google"))
        ))
    )

    private fun youtubeTask(q: String) = DiagnosisResult(q,
        "YouTube da Video Ko'rish",
        "YouTube ilovasini ochib video topish va ko'rish.",
        "com.google.android.youtube",
        GuideTask("dyn_yt_watch", "YouTube Ochish", "YouTube da video qidirish.", "Media", "play_circle", listOf(
            GuideStep(1, "YouTube", "▶️ YouTube ilovasini oching.", ActionType.OPEN_APP, listOf("youtube"), "com.google.android.youtube"),
            GuideStep(2, "Qidiruv", "🔍 Ekran tepasidagi qidiruv (lupa) belgisini bosing.", ActionType.CLICK_TARGET, listOf("search", "qidiruv", "поиск", "lupa", "magnify")),
            GuideStep(3, "Video Nomi", "⌨️ Ko'rmoqchi bo'lgan video nomini yozing.", ActionType.INPUT_TEXT, listOf("search", "input", "query", "video")),
            GuideStep(4, "Videoni Tanlang", "📺 Kerakli videoni bosing va tomosha qiling.", ActionType.CLICK_TARGET, listOf("video", "play", "watch", "ko'r"))
        ))
    )

    private fun facebookTask(q: String) = DiagnosisResult(q,
        "Facebook Ilovasi",
        "Facebook ni o'rnatish yoki kirish.",
        "com.android.vending",
        GuideTask("dyn_fb", "Facebook", "Facebook ni Play Store dan o'rnatish va kirish.", "Ilovalar", "group", listOf(
            GuideStep(1, "Play Store", "🏪 Google Play Store ni oching.", ActionType.OPEN_APP, listOf("play store", "market", "Google Play"), "com.android.vending"),
            GuideStep(2, "Qidiruv", "🔍 Ekran tepasidagi qidiruv maydoniga bosing.", ActionType.CLICK_TARGET, listOf("search", "qidiruv", "search for apps", "поиск")),
            GuideStep(3, "Facebook Yozing", "⌨️ 'Facebook' deb yozing va Enter bosing.", ActionType.INPUT_TEXT, listOf("facebook"), textToType = "Facebook"),
            GuideStep(4, "Facebook Tanlang", "📘 Natijalar ichidan 'Facebook' ilovasini bosing.", ActionType.CLICK_TARGET, listOf("facebook", "meta")),
            GuideStep(5, "O'rnatish", "📥 'O'rnatish' (Install) tugmasini bosing.", ActionType.CLICK_TARGET, listOf("install", "o'rnatish", "yuklab", "скачать")),
            GuideStep(6, "Ochish", "▶️ 'Ochish' (Open) tugmasini bosing.", ActionType.CLICK_TARGET, listOf("ochish", "open", "открыть")),
            GuideStep(7, "Kirish", "🔑 'Log In' yoki 'Kirish' ni bosing va ma'lumotlaringizni kiriting.", ActionType.CLICK_TARGET, listOf("log in", "kirish", "login", "sign in", "email", "phone"))
        ))
    )

    private fun gmailTask(q: String) = DiagnosisResult(q,
        "Gmail / Elektron pochta",
        "Gmail ilovasini ochib email o'qish yoki yuborish.",
        "com.google.android.gm",
        GuideTask("dyn_gmail", "Gmail Ochish", "Gmail da xabar o'qish yoki yuborish.", "Ilovalar", "mail", listOf(
            GuideStep(1, "Gmail", "📧 Gmail ilovasini oching.", ActionType.OPEN_APP, listOf("gmail", "mail", "email", "pochta"), "com.google.android.gm"),
            GuideStep(2, "Xabar Yozing", "✏️ Pastdagi yozuv tugmasini bosing (qalam belgisi).", ActionType.CLICK_TARGET, listOf("compose", "yozish", "new", "yangi", "create", "write")),
            GuideStep(3, "Qabul Qiluvchi", "👤 'To' (Kimga) maydoniga email manzilini kiriting.", ActionType.INPUT_TEXT, listOf("to", "kimga", "recipient", "email", "@")),
            GuideStep(4, "Yuborish", "📤 'Yuborish' (Send) tugmasini bosing.", ActionType.CLICK_TARGET, listOf("send", "yuborish", "отправить"))
        ))
    )

    private fun paymentAppTask(q: String) = DiagnosisResult(q,
        "To'lov Ilovasi",
        "Payme, Click yoki boshqa to'lov ilovasi orqali pul o'tkazish.",
        "com.android.vending",
        GuideTask("dyn_payment", "To'lov Ilovasi", "To'lov ilovasini ochib pul o'tkazish.", "Moliya", "payments", listOf(
            GuideStep(1, "To'lov Ilovasi", "💳 Payme, Click yoki bank ilovangizni oching.", ActionType.OPEN_APP, listOf("payme", "click", "bank", "to'lov")),
            GuideStep(2, "O'tkazma", "💸 'Pul o'tkazish' yoki 'Transfer' ni bosing.", ActionType.CLICK_TARGET, listOf("transfer", "o'tkazish", "send", "yuborish", "pul yuborish")),
            GuideStep(3, "Raqam", "📱 Qabul qiluvchining kartasi yoki telefon raqamini kiriting.", ActionType.INPUT_TEXT, listOf("card", "karta", "phone", "raqam", "number", "номер")),
            GuideStep(4, "Summa", "💰 O'tkazmoqchi bo'lgan summani kiriting.", ActionType.INPUT_TEXT, listOf("amount", "summa", "so'm", "сум")),
            GuideStep(5, "Tasdiqlash", "✅ 'Tasdiqlash' yoki 'Confirm' ni bosing.", ActionType.CLICK_TARGET, listOf("confirm", "tasdiqlash", "pay", "to'lash", "ok"))
        ))
    )

    private fun playStoreTask(q: String) = DiagnosisResult(q,
        "Play Store dan Ilova O'rnatish",
        "Google Play Store dan kerakli ilovani qidirish va o'rnatish.",
        "com.android.vending",
        GuideTask("dyn_playstore", "Ilova O'rnatish", "Play Store dan ilova yuklab olish.", "Ilovalar", "get_app", listOf(
            GuideStep(1, "Play Store", "🏪 Google Play Store ni oching.", ActionType.OPEN_APP, listOf("play store", "market", "play", "Google Play"), "com.android.vending"),
            GuideStep(2, "Qidiruv", "🔍 Ekran tepasidagi qidiruv (Search) maydoniga bosing.", ActionType.CLICK_TARGET, listOf("search", "qidiruv", "search for apps", "apps & games", "поиск")),
            GuideStep(3, "Nom Yozing", "⌨️ Ilova nomini yozing va Enter bosing.", ActionType.INPUT_TEXT, listOf("search", "input", "enter")),
            GuideStep(4, "Ilovani Tanlang", "📱 Natijalar ichidan kerakli ilovani bosing.", ActionType.CLICK_TARGET, listOf("app", "ilova", "result")),
            GuideStep(5, "O'rnatish", "📥 'O'rnatish' (Install) tugmasini bosing.", ActionType.CLICK_TARGET, listOf("install", "o'rnatish", "yuklab", "скачать")),
            GuideStep(6, "Ochish", "▶️ O'rnatilgach 'Ochish' (Open) tugmasini bosing.", ActionType.CLICK_TARGET, listOf("ochish", "open", "открыть"))
        ))
    )
}
