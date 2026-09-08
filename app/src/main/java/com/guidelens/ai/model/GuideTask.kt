package com.guidelens.ai.model

enum class ActionType {
    CLICK_TARGET,
    SCROLL_DOWN,
    INPUT_TEXT,
    VERIFY_STATE,
    OPEN_APP
}

data class GuideStep(
    val stepId: Int,
    val titleUz: String,
    val instructionUz: String,
    val actionType: ActionType,
    val targetKeywords: List<String>,
    val appPackage: String? = null,
    val isCompleted: Boolean = false,
    // AUTO mode: text to type automatically for search/input steps. Null = user must type manually.
    val textToType: String? = null
)

data class GuideTask(
    val id: String,
    val titleUz: String,
    val descriptionUz: String,
    val category: String,
    val iconResName: String,
    val steps: List<GuideStep>
) {
    companion object {
        val PREDEFINED_TASKS = listOf(

            GuideTask(
                id = "task_dark_mode",
                titleUz = "Dark Mode Yoqish",
                descriptionUz = "Tungi rejimni yoqish — ko'z uchun qulay, batareyani tejaydi.",
                category = "Sozlamalar",
                iconResName = "dark_mode",
                steps = listOf(
                    GuideStep(1, "Sozlamalarni Oching", "⚙️ Sozlamalar ilovasini oching.", ActionType.OPEN_APP, listOf("settings", "sozlamalar"), "com.android.settings"),
                    GuideStep(2, "Ekran Bo'limiga Kiring", "🖥️ 'Ekran' (Display), 'Mavzu' yoki 'Wallpaper' ni bosing.", ActionType.CLICK_TARGET, listOf("ekran", "display", "mavzu", "theme", "wallpaper")),
                    GuideStep(3, "Dark Mode ni Yoqing", "🌙 'Qorong'u rejim' (Dark mode) tugmasini bosing va yoqing.", ActionType.CLICK_TARGET, listOf("qorong'u", "dark", "tungi", "dark mode", "dark theme"))
                )
            ),

            GuideTask(
                id = "task_telegram_install",
                titleUz = "Telegram O'rnatish",
                descriptionUz = "Play Store'dan Telegram yuklab, ro'yxatdan o'tish.",
                category = "Ilovalar",
                iconResName = "telegram",
                steps = listOf(
                    GuideStep(1, "Play Store", "🏪 Google Play Store ni oching.", ActionType.OPEN_APP, listOf("play store", "market", "Google Play"), "com.android.vending"),
                    GuideStep(2, "Qidiruv", "🔍 Ekran tepasidagi qidiruv (Search) maydoniga bosing.", ActionType.CLICK_TARGET, listOf("search", "qidiruv", "search for apps", "apps & games", "поиск")),
                    GuideStep(3, "Telegram Yozing", "⌨️ Qidiruv maydoniga 'Telegram' deb yozing va Enter bosing.", ActionType.INPUT_TEXT, listOf("telegram"), textToType = "Telegram"),
                    GuideStep(4, "Telegram Tanlang", "📱 Natijalar ichidan 'Telegram' ilovasini bosing.", ActionType.CLICK_TARGET, listOf("telegram")),
                    GuideStep(5, "O'rnatish", "📥 'O'rnatish' (Install) tugmasini bosing.", ActionType.CLICK_TARGET, listOf("install", "o'rnatish", "yuklab", "скачать")),
                    GuideStep(6, "Ochish", "▶️ O'rnatib bo'lgach 'Ochish' (Open) tugmasini bosing.", ActionType.CLICK_TARGET, listOf("ochish", "open", "открыть")),
                    GuideStep(7, "Boshlash", "🚀 'Start Messaging' tugmasini bosing.", ActionType.CLICK_TARGET, listOf("boshlash", "start messaging", "продолжить", "start")),
                    GuideStep(8, "Telefon Raqam", "📱 Telefon raqamingizni kiriting.", ActionType.INPUT_TEXT, listOf("phone number", "telefon", "номер"))
                )
            ),

            GuideTask(
                id = "task_whatsapp_install",
                titleUz = "WhatsApp O'rnatish",
                descriptionUz = "Play Store'dan WhatsApp yuklab, akkaunt yaratish.",
                category = "Ilovalar",
                iconResName = "chat",
                steps = listOf(
                    GuideStep(1, "Play Store", "🏪 Google Play Store ni oching.", ActionType.OPEN_APP, listOf("play store", "market", "Google Play"), "com.android.vending"),
                    GuideStep(2, "Qidiruv", "🔍 Ekran tepasidagi qidiruv (Search) maydoniga bosing.", ActionType.CLICK_TARGET, listOf("search", "qidiruv", "search for apps", "apps & games", "поиск")),
                    GuideStep(3, "WhatsApp Yozing", "⌨️ Qidiruv maydoniga 'WhatsApp' deb yozing va Enter bosing.", ActionType.INPUT_TEXT, listOf("whatsapp"), textToType = "WhatsApp"),
                    GuideStep(4, "WhatsApp Tanlang", "📱 Natijalar ichidan 'WhatsApp' ilovasini bosing.", ActionType.CLICK_TARGET, listOf("whatsapp")),
                    GuideStep(5, "O'rnatish", "📥 'O'rnatish' (Install) tugmasini bosing.", ActionType.CLICK_TARGET, listOf("install", "o'rnatish", "yuklab", "скачать")),
                    GuideStep(6, "Ochish", "▶️ 'Ochish' tugmasini bosing.", ActionType.OPEN_APP, listOf("whatsapp"), "com.whatsapp"),
                    GuideStep(7, "Rozi Bo'lish", "✅ 'Agree and Continue' ni bosing.", ActionType.CLICK_TARGET, listOf("agree", "continue", "rozi", "accept")),
                    GuideStep(8, "Telefon Raqam", "📱 Mamlakat kodi va raqamingizni kiriting.", ActionType.INPUT_TEXT, listOf("phone", "telefon", "number")),
                    GuideStep(9, "SMS Kod", "💬 SMS dan kelgan 6 xonali kodni kiriting.", ActionType.INPUT_TEXT, listOf("code", "kod", "verify"))
                )
            ),

            GuideTask(
                id = "task_wifi_reconnect",
                titleUz = "Wi-Fi Qayta Ulash",
                descriptionUz = "Wi-Fi ulanish muammosini bartaraf etish.",
                category = "Tarmoq",
                iconResName = "wifi",
                steps = listOf(
                    GuideStep(1, "Sozlamalar", "⚙️ Sozlamalarga kiring.", ActionType.OPEN_APP, listOf("settings"), "com.android.settings"),
                    GuideStep(2, "Wi-Fi", "📶 'Wi-Fi' yoki 'Tarmoq va internet' ni bosing.", ActionType.CLICK_TARGET, listOf("wi-fi", "wifi", "network", "tarmoq")),
                    GuideStep(3, "Qayta Yoqish", "🔄 Wi-Fi tugmasini o'chirib, qayta yoqing.", ActionType.CLICK_TARGET, listOf("wi-fi", "wifi", "on", "off"))
                )
            ),

            GuideTask(
                id = "task_bluetooth",
                titleUz = "Bluetooth Ulash",
                descriptionUz = "Bluetooth yoqib quloqchin yoki qurilmaga ulash.",
                category = "Tarmoq",
                iconResName = "bluetooth",
                steps = listOf(
                    GuideStep(1, "Sozlamalar", "⚙️ Sozlamalarga kiring.", ActionType.OPEN_APP, listOf("settings"), "com.android.settings"),
                    GuideStep(2, "Bluetooth", "📡 'Bluetooth' bo'limini bosing.", ActionType.CLICK_TARGET, listOf("bluetooth")),
                    GuideStep(3, "Yoqish", "🔵 Bluetooth tugmasini yoqing.", ActionType.CLICK_TARGET, listOf("on", "yoqish", "bluetooth", "enable")),
                    GuideStep(4, "Qurilma", "🎧 Ulamoqchi qurilmangizni bosing.", ActionType.CLICK_TARGET, listOf("pair", "ulash", "connect", "device", "available"))
                )
            ),

            GuideTask(
                id = "task_hotspot",
                titleUz = "Hotspot Yoqish",
                descriptionUz = "Telefondan boshqalarga internet ulashish.",
                category = "Tarmoq",
                iconResName = "wifi_tethering",
                steps = listOf(
                    GuideStep(1, "Sozlamalar", "⚙️ Sozlamalarga kiring.", ActionType.OPEN_APP, listOf("settings"), "com.android.settings"),
                    GuideStep(2, "Tarmoq", "📶 'Tarmoq va internet' yoki 'Ulanishlar' ni bosing.", ActionType.CLICK_TARGET, listOf("tarmoq", "network", "connections")),
                    GuideStep(3, "Hotspot", "📡 'Mobil hotspot' yoki 'Tethering' ni bosing.", ActionType.CLICK_TARGET, listOf("hotspot", "tethering", "modem", "portable")),
                    GuideStep(4, "Yoqish", "🔥 'Mobil hotspot' ni yoqing.", ActionType.CLICK_TARGET, listOf("mobile hotspot", "on", "yoqish", "enable"))
                )
            ),

            GuideTask(
                id = "task_font_size",
                titleUz = "Shrift O'lchamini Kattalashtirish",
                descriptionUz = "Ekrandagi matnni kattalashtirish.",
                category = "Sozlamalar",
                iconResName = "format_size",
                steps = listOf(
                    GuideStep(1, "Sozlamalar", "⚙️ Sozlamalarga kiring.", ActionType.OPEN_APP, listOf("settings"), "com.android.settings"),
                    GuideStep(2, "Ekran", "🖥️ 'Ekran' (Display) ni bosing.", ActionType.CLICK_TARGET, listOf("ekran", "display", "displey")),
                    GuideStep(3, "Shrift", "🔡 'Shrift o'lchami' (Font size) ni bosing.", ActionType.CLICK_TARGET, listOf("shrift", "font size", "text size", "matn o'lchami")),
                    GuideStep(4, "Kattalashtirish", "↔️ Slayderni o'ngga suring yoki 'Katta' ni tanlang.", ActionType.CLICK_TARGET, listOf("large", "katta", "huge", "bigger", "xl"))
                )
            ),

            GuideTask(
                id = "task_battery_saver",
                titleUz = "Quvvat Tejash Rejimi",
                descriptionUz = "Battery saver yoqib batareyani uzoqroq ishlating.",
                category = "Sozlamalar",
                iconResName = "battery_saver",
                steps = listOf(
                    GuideStep(1, "Sozlamalar", "⚙️ Sozlamalarga kiring.", ActionType.OPEN_APP, listOf("settings"), "com.android.settings"),
                    GuideStep(2, "Batareya", "🔋 'Batareya' (Battery) bo'limini bosing.", ActionType.CLICK_TARGET, listOf("batareya", "battery", "аккумулятор", "quvvat")),
                    GuideStep(3, "Tejash", "💡 'Quvvat tejash' (Battery saver) ni yoqing.", ActionType.CLICK_TARGET, listOf("tejash", "saver", "saving", "power saving"))
                )
            ),

            GuideTask(
                id = "task_screen_lock",
                titleUz = "Ekran Qulfi O'rnatish",
                descriptionUz = "Telefonni PIN yoki barmoq izi bilan himoyalash.",
                category = "Xavfsizlik",
                iconResName = "lock",
                steps = listOf(
                    GuideStep(1, "Sozlamalar", "⚙️ Sozlamalarga kiring.", ActionType.OPEN_APP, listOf("settings"), "com.android.settings"),
                    GuideStep(2, "Xavfsizlik", "🔒 'Xavfsizlik' (Security) bo'limini bosing.", ActionType.CLICK_TARGET, listOf("xavfsizlik", "security", "ekran qulfi", "lock", "безопасность")),
                    GuideStep(3, "Qulf Turi", "🔑 'Ekran qulfi' (Screen lock) ni tanlang.", ActionType.CLICK_TARGET, listOf("screen lock", "qulf", "pin", "pattern", "fingerprint")),
                    GuideStep(4, "PIN", "🔢 Yangi PIN kiriting va tasdiqlang.", ActionType.INPUT_TEXT, listOf("pin", "password", "parol", "confirm"))
                )
            ),

            GuideTask(
                id = "task_mygov_setup",
                titleUz = "MyGov Ilovasida Kirish",
                descriptionUz = "Yagona portal MyGov da xizmatlardan foydalanish.",
                category = "Davlat Xizmatlari",
                iconResName = "account_balance",
                steps = listOf(
                    GuideStep(1, "MyGov", "🏛️ MyGov ilovasini oching.", ActionType.OPEN_APP, listOf("mygov", "my.gov.uz")),
                    GuideStep(2, "Kirish", "🔑 'Kirish' (OneID) tugmasini bosing.", ActionType.CLICK_TARGET, listOf("kirish", "login", "oneid", "профил")),
                    GuideStep(3, "JShShIR", "🪪 JShShIR raqamingizni kiriting.", ActionType.INPUT_TEXT, listOf("jshshir", "pinfl", "passport", "inn"))
                )
            ),

            GuideTask(
                id = "task_soliq_app",
                titleUz = "Soliq Ilovasida Keshbek",
                descriptionUz = "Chek skanerlash va keshbek olish.",
                category = "Davlat Xizmatlari",
                iconResName = "receipt_long",
                steps = listOf(
                    GuideStep(1, "Soliq", "🧾 Soliq ilovasini oching.", ActionType.OPEN_APP, listOf("soliq")),
                    GuideStep(2, "Keshbek", "📷 'Keshbek' yoki kamera tugmasini bosing.", ActionType.CLICK_TARGET, listOf("keshbek", "skaner", "chek", "qr", "camera")),
                    GuideStep(3, "QR Kod", "🔍 Kamerani chek QR kodiga qarating.", ActionType.CLICK_TARGET, listOf("scan", "qr", "kod"))
                )
            ),

            GuideTask(
                id = "task_notifications",
                titleUz = "Bildirishnomalarni Sozlash",
                descriptionUz = "Keraksiz bildirishnomalarni o'chirish.",
                category = "Sozlamalar",
                iconResName = "notifications",
                steps = listOf(
                    GuideStep(1, "Sozlamalar", "⚙️ Sozlamalarga kiring.", ActionType.OPEN_APP, listOf("settings"), "com.android.settings"),
                    GuideStep(2, "Bildirishnomalar", "🔔 'Bildirishnomalar' (Notifications) ni bosing.", ActionType.CLICK_TARGET, listOf("bildirishnoma", "notification", "уведомления")),
                    GuideStep(3, "O'chirish", "🔕 Xalaqit beruvchi ilova yonidagi switch ni o'chiring.", ActionType.CLICK_TARGET, listOf("off", "o'chirish", "disable", "switch", "allowed"))
                )
            )
        )
    }
}
