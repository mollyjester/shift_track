package com.slikharev.shifttrack.model

import java.time.DayOfWeek

data class CountryConfig(
    val code: String,
    val name: String,
    val weekendDays: Set<DayOfWeek>,
    val currencySymbol: String,
)

object Countries {
    private val SAT_SUN = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    private val FRI_SAT = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
    private val SUN_ONLY = setOf(DayOfWeek.SUNDAY)

    val all: List<CountryConfig> = listOf(
        // Europe
        CountryConfig("AL", "Albania", SAT_SUN, "L"),
        CountryConfig("AD", "Andorra", SAT_SUN, "€"),
        CountryConfig("AT", "Austria", SAT_SUN, "€"),
        CountryConfig("BY", "Belarus", SAT_SUN, "Br"),
        CountryConfig("BE", "Belgium", SAT_SUN, "€"),
        CountryConfig("BA", "Bosnia and Herzegovina", SAT_SUN, "KM"),
        CountryConfig("BG", "Bulgaria", SAT_SUN, "лв"),
        CountryConfig("HR", "Croatia", SAT_SUN, "€"),
        CountryConfig("CY", "Cyprus", SAT_SUN, "€"),
        CountryConfig("CZ", "Czechia", SAT_SUN, "Kč"),
        CountryConfig("DK", "Denmark", SAT_SUN, "kr"),
        CountryConfig("EE", "Estonia", SAT_SUN, "€"),
        CountryConfig("FI", "Finland", SAT_SUN, "€"),
        CountryConfig("FR", "France", SAT_SUN, "€"),
        CountryConfig("DE", "Germany", SAT_SUN, "€"),
        CountryConfig("GR", "Greece", SAT_SUN, "€"),
        CountryConfig("HU", "Hungary", SAT_SUN, "Ft"),
        CountryConfig("IS", "Iceland", SAT_SUN, "kr"),
        CountryConfig("IE", "Ireland", SAT_SUN, "€"),
        CountryConfig("IT", "Italy", SAT_SUN, "€"),
        CountryConfig("LV", "Latvia", SAT_SUN, "€"),
        CountryConfig("LI", "Liechtenstein", SAT_SUN, "CHF"),
        CountryConfig("LT", "Lithuania", SAT_SUN, "€"),
        CountryConfig("LU", "Luxembourg", SAT_SUN, "€"),
        CountryConfig("MT", "Malta", SAT_SUN, "€"),
        CountryConfig("MD", "Moldova", SAT_SUN, "L"),
        CountryConfig("MC", "Monaco", SAT_SUN, "€"),
        CountryConfig("ME", "Montenegro", SAT_SUN, "€"),
        CountryConfig("NL", "Netherlands", SAT_SUN, "€"),
        CountryConfig("MK", "North Macedonia", SAT_SUN, "ден"),
        CountryConfig("NO", "Norway", SAT_SUN, "kr"),
        CountryConfig("PL", "Poland", SAT_SUN, "zł"),
        CountryConfig("PT", "Portugal", SAT_SUN, "€"),
        CountryConfig("RO", "Romania", SAT_SUN, "lei"),
        CountryConfig("RU", "Russia", SAT_SUN, "₽"),
        CountryConfig("SM", "San Marino", SAT_SUN, "€"),
        CountryConfig("RS", "Serbia", SAT_SUN, "din"),
        CountryConfig("SK", "Slovakia", SAT_SUN, "€"),
        CountryConfig("SI", "Slovenia", SAT_SUN, "€"),
        CountryConfig("ES", "Spain", SAT_SUN, "€"),
        CountryConfig("SE", "Sweden", SAT_SUN, "kr"),
        CountryConfig("CH", "Switzerland", SAT_SUN, "CHF"),
        CountryConfig("UA", "Ukraine", SAT_SUN, "₴"),
        CountryConfig("GB", "United Kingdom", SAT_SUN, "£"),

        // Middle East & North Africa (Fri+Sat weekend)
        CountryConfig("DZ", "Algeria", FRI_SAT, "د.ج"),
        CountryConfig("BH", "Bahrain", FRI_SAT, "BD"),
        CountryConfig("EG", "Egypt", FRI_SAT, "E£"),
        CountryConfig("IQ", "Iraq", FRI_SAT, "ع.د"),
        CountryConfig("JO", "Jordan", FRI_SAT, "JD"),
        CountryConfig("KW", "Kuwait", FRI_SAT, "KD"),
        CountryConfig("LB", "Lebanon", SAT_SUN, "L£"),
        CountryConfig("LY", "Libya", FRI_SAT, "LD"),
        CountryConfig("MA", "Morocco", SAT_SUN, "MAD"),
        CountryConfig("OM", "Oman", FRI_SAT, "ر.ع."),
        CountryConfig("PS", "Palestine", FRI_SAT, "₪"),
        CountryConfig("QA", "Qatar", FRI_SAT, "QR"),
        CountryConfig("SA", "Saudi Arabia", FRI_SAT, "SAR"),
        CountryConfig("SY", "Syria", FRI_SAT, "S£"),
        CountryConfig("TN", "Tunisia", SAT_SUN, "DT"),
        CountryConfig("AE", "United Arab Emirates", FRI_SAT, "AED"),
        CountryConfig("YE", "Yemen", FRI_SAT, "YR"),

        // Asia & Pacific
        CountryConfig("AU", "Australia", SAT_SUN, "$"),
        CountryConfig("BD", "Bangladesh", FRI_SAT, "৳"),
        CountryConfig("CN", "China", SAT_SUN, "¥"),
        CountryConfig("HK", "Hong Kong", SAT_SUN, "HK$"),
        CountryConfig("IN", "India", SAT_SUN, "₹"),
        CountryConfig("ID", "Indonesia", SAT_SUN, "Rp"),
        CountryConfig("IL", "Israel", FRI_SAT, "₪"),
        CountryConfig("JP", "Japan", SAT_SUN, "¥"),
        CountryConfig("KZ", "Kazakhstan", SAT_SUN, "₸"),
        CountryConfig("MY", "Malaysia", SAT_SUN, "RM"),
        CountryConfig("NP", "Nepal", SAT_SUN, "Rs"),
        CountryConfig("NZ", "New Zealand", SAT_SUN, "$"),
        CountryConfig("PK", "Pakistan", FRI_SAT, "Rs"),
        CountryConfig("PH", "Philippines", SAT_SUN, "₱"),
        CountryConfig("SG", "Singapore", SAT_SUN, "S$"),
        CountryConfig("KR", "South Korea", SAT_SUN, "₩"),
        CountryConfig("LK", "Sri Lanka", SAT_SUN, "Rs"),
        CountryConfig("TW", "Taiwan", SAT_SUN, "NT$"),
        CountryConfig("TH", "Thailand", SAT_SUN, "฿"),
        CountryConfig("TR", "Türkiye", SAT_SUN, "₺"),
        CountryConfig("VN", "Vietnam", SAT_SUN, "₫"),

        // Americas
        CountryConfig("AR", "Argentina", SAT_SUN, "$"),
        CountryConfig("BR", "Brazil", SAT_SUN, "R$"),
        CountryConfig("CA", "Canada", SAT_SUN, "$"),
        CountryConfig("CL", "Chile", SAT_SUN, "$"),
        CountryConfig("CO", "Colombia", SAT_SUN, "$"),
        CountryConfig("CR", "Costa Rica", SAT_SUN, "₡"),
        CountryConfig("CU", "Cuba", SAT_SUN, "$"),
        CountryConfig("DO", "Dominican Republic", SAT_SUN, "RD$"),
        CountryConfig("EC", "Ecuador", SAT_SUN, "$"),
        CountryConfig("GT", "Guatemala", SAT_SUN, "Q"),
        CountryConfig("MX", "Mexico", SAT_SUN, "$"),
        CountryConfig("PA", "Panama", SAT_SUN, "B/."),
        CountryConfig("PE", "Peru", SAT_SUN, "S/."),
        CountryConfig("PR", "Puerto Rico", SAT_SUN, "$"),
        CountryConfig("US", "United States", SAT_SUN, "$"),
        CountryConfig("UY", "Uruguay", SAT_SUN, "$"),
        CountryConfig("VE", "Venezuela", SAT_SUN, "Bs"),

        // Africa
        CountryConfig("AO", "Angola", SAT_SUN, "Kz"),
        CountryConfig("ET", "Ethiopia", SUN_ONLY, "Br"),
        CountryConfig("GH", "Ghana", SAT_SUN, "GH₵"),
        CountryConfig("KE", "Kenya", SAT_SUN, "KSh"),
        CountryConfig("NG", "Nigeria", SAT_SUN, "₦"),
        CountryConfig("ZA", "South Africa", SAT_SUN, "R"),
        CountryConfig("TZ", "Tanzania", SAT_SUN, "TSh"),
        CountryConfig("UG", "Uganda", SAT_SUN, "USh"),
    )

    private val byCode: Map<String, CountryConfig> = all.associateBy { it.code }

    fun findByCode(code: String): CountryConfig? = byCode[code]

    fun lastWeekendDay(weekendDays: Set<DayOfWeek>): DayOfWeek {
        // Return the day with the highest ISO value (Monday=1 .. Sunday=7)
        return weekendDays.maxByOrNull { it.value } ?: DayOfWeek.SUNDAY
    }
}
