package com.dataloom.checklist.domain.phone

/**
 * A country (or territory) a phone number can belong to (CL-380).
 *
 * [nationalDigits] is the length range of a national number without the trunk prefix (fixed line and
 * mobile numbers). [trunkZero] says people there write a leading "0" at home (India "098450 12345"),
 * which is not part of the international number and is dropped when the number is stored.
 */
data class PhoneCountry(
    /** ISO 3166-1 alpha-2 code, such as "IN". Stored in the profile; names come from the platform locale data. */
    val iso: String,
    val dialCode: Int,
    val nationalDigits: IntRange,
    val trunkZero: Boolean,
    /** The country a shared dial code means when a pasted "+1" or "+7" number names no country. */
    val mainForDialCode: Boolean,
) {
    val dialText: String get() = "+$dialCode"

    /** The most digits the phone field accepts while typing: one more when a trunk "0" may lead. */
    val maxTypedDigits: Int get() = nationalDigits.last + if (trunkZero) 1 else 0

    /** The flag as an emoji: the ISO letters as regional indicator symbols. */
    val flag: String
        get() = iso.uppercase().joinToString("") { letter ->
            Character.toChars(REGIONAL_INDICATOR_A + (letter - 'A')).concatToString()
        }

    private companion object {
        const val REGIONAL_INDICATOR_A = 0x1F1E6
    }
}

/**
 * Every country with its dial code and number lengths, fully offline. Generated from the Google
 * libphonenumber metadata (Apache 2.0): region, dial code, shortest and longest national number,
 * "0" when a trunk zero is used, "1" for the main country of a shared dial code.
 */
object PhoneCountries {

    /** The first release is India only, so India is the default and the top of the list. */
    const val DEFAULT_ISO = "IN"

    val all: List<PhoneCountry> by lazy(LazyThreadSafetyMode.PUBLICATION) { DATA.split(';').map(::parse) }

    private val byIso: Map<String, PhoneCountry> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        all.associateBy { it.iso }
    }

    val default: PhoneCountry get() = byIso.getValue(DEFAULT_ISO)

    fun find(iso: String?): PhoneCountry? = iso?.let { byIso[it.uppercase()] }

    fun orDefault(iso: String?): PhoneCountry = find(iso) ?: default

    /**
     * The country for the dial code at the start of [digits] (the text after a "+") and the digits
     * after it. The longest code that exists wins; a shared code means its main country. Null when no
     * dial code matches.
     */
    fun splitDialCode(digits: String): Pair<PhoneCountry, String>? =
        (MAX_DIAL_CODE_DIGITS downTo 1).asSequence()
            .filter { it <= digits.length }
            .mapNotNull { length -> forDialCode(digits.substring(0, length))?.let { it to digits.substring(length) } }
            .firstOrNull()

    private fun forDialCode(code: String): PhoneCountry? {
        val matches = all.filter { it.dialText == "+$code" }
        return matches.firstOrNull { it.mainForDialCode } ?: matches.firstOrNull()
    }

    /** One entry: "IN:91:10:10:0:1" is region, dial code, min and max digits, trunk "0", main country. */
    private fun parse(entry: String): PhoneCountry {
        val field = entry.split(':').iterator()
        return PhoneCountry(
            iso = field.next(),
            dialCode = field.next().toInt(),
            nationalDigits = field.next().toInt()..field.next().toInt(),
            trunkZero = field.next() == "0",
            mainForDialCode = field.next() == "1",
        )
    }

    private const val MAX_DIAL_CODE_DIGITS = 3

    private const val DATA =
        "AC:247:5:5::1;AD:376:6:9::1;AE:971:8:9:0:1;AF:93:9:9:0:1;AG:1:10:10::;AI:1:10:10::;" +
        "AL:355:8:9:0:1;AM:374:8:8:0:1;AO:244:9:9::1;AR:54:10:11:0:1;AS:1:10:10::;AT:43:4:13:0:1;" +
        "AU:61:9:9:0:1;AW:297:7:7::1;AX:358:6:10:0:;AZ:994:9:9:0:1;BA:387:8:9:0:1;BB:1:10:10::;" +
        "BD:880:6:10:0:1;BE:32:8:9:0:1;BF:226:8:8::1;BG:359:6:9:0:1;BH:973:8:8::1;BI:257:8:8::1;" +
        "BJ:229:10:10::1;BL:590:9:9:0:;BM:1:10:10::;BN:673:7:7::1;BO:591:8:8:0:1;BQ:599:7:7::;" +
        "BR:55:10:11:0:1;BS:1:10:10::;BT:975:7:8::1;BW:267:7:8::1;BY:375:9:9::1;BZ:501:7:7::1;" +
        "CA:1:10:10::;CC:61:9:9:0:;CD:243:7:10:0:1;CF:236:8:8::1;CG:242:9:9::1;CH:41:9:9:0:1;" +
        "CI:225:10:10::1;CK:682:5:5::1;CL:56:9:9::1;CM:237:9:9::1;CN:86:7:11:0:1;CO:57:8:10:0:1;" +
        "CR:506:8:8::1;CU:53:6:10:0:1;CV:238:7:7::1;CW:599:7:8::1;CX:61:9:9:0:;CY:357:8:8::1;" +
        "CZ:420:9:9::1;DE:49:5:15:0:1;DJ:253:8:8::1;DK:45:8:8::1;DM:1:10:10::;DO:1:10:10::;" +
        "DZ:213:8:9:0:1;EC:593:8:9:0:1;EE:372:7:8::1;EG:20:8:10:0:1;EH:212:9:9:0:;ER:291:7:7:0:1;" +
        "ES:34:9:9::1;ET:251:9:9:0:1;FI:358:5:10:0:1;FJ:679:7:7::1;FK:500:5:5::1;FM:691:7:7::1;" +
        "FO:298:6:6::1;FR:33:9:9:0:1;GA:241:7:8::1;GB:44:9:10:0:1;GD:1:10:10::;GE:995:9:9:0:1;" +
        "GF:594:9:9:0:1;GG:44:10:10:0:;GH:233:9:9:0:1;GI:350:8:8::1;GL:299:6:6::1;GM:220:7:9::1;" +
        "GN:224:8:9::1;GP:590:9:9:0:1;GQ:240:9:9::1;GR:30:10:10::1;GT:502:8:8::1;GU:1:10:10::;" +
        "GW:245:9:9::1;GY:592:7:7::1;HK:852:8:8::1;HN:504:8:8::1;HR:385:8:9:0:1;HT:509:8:8::1;" +
        "HU:36:8:9::1;ID:62:7:12:0:1;IE:353:7:10:0:1;IL:972:8:12:0:1;IM:44:10:10:0:;IN:91:10:10:0:1;" +
        "IO:246:7:7::1;IQ:964:8:10:0:1;IR:98:6:10:0:1;IS:354:7:9::1;IT:39:6:12::1;JE:44:10:10:0:;" +
        "JM:1:10:10::;JO:962:8:9:0:1;JP:81:9:10:0:1;KE:254:7:9:0:1;KG:996:9:9:0:1;KH:855:8:9:0:1;" +
        "KI:686:5:8:0:1;KM:269:7:7::1;KN:1:10:10::;KP:850:8:10:0:1;KR:82:5:10:0:1;KW:965:8:8::1;" +
        "KY:1:10:10::;KZ:7:10:10::;LA:856:8:10:0:1;LB:961:7:8:0:1;LC:1:10:10::;LI:423:7:9:0:1;" +
        "LK:94:9:9:0:1;LR:231:7:9:0:1;LS:266:8:8::1;LT:370:8:8:0:1;LU:352:4:11::1;LV:371:8:8::1;" +
        "LY:218:9:9:0:1;MA:212:9:9:0:1;MC:377:8:9:0:1;MD:373:8:8:0:1;ME:382:8:8:0:1;MF:590:9:9:0:;" +
        "MG:261:9:9:0:1;MH:692:7:7::1;MK:389:8:8:0:1;ML:223:8:8::1;MM:95:6:10:0:1;MN:976:8:10:0:1;" +
        "MO:853:8:8::1;MP:1:10:10::;MQ:596:9:9:0:1;MR:222:8:8::1;MS:1:10:10::;MT:356:8:8::1;" +
        "MU:230:7:8::1;MV:960:7:7::1;MW:265:7:9:0:1;MX:52:10:10::1;MY:60:8:10:0:1;MZ:258:8:9::1;" +
        "NA:264:8:9:0:1;NC:687:6:6::1;NE:227:8:8::1;NF:672:6:6::1;NG:234:10:10:0:1;NI:505:8:8::1;" +
        "NL:31:9:11:0:1;NO:47:8:8::1;NP:977:8:10:0:1;NR:674:7:7::1;NU:683:4:7::1;NZ:64:8:10:0:1;" +
        "OM:968:8:8::1;PA:507:7:8::1;PE:51:8:9:0:1;PF:689:8:8::1;PG:675:7:8::1;PH:63:6:10:0:1;" +
        "PK:92:9:10:0:1;PL:48:7:9::1;PM:508:6:9:0:1;PR:1:10:10::;PS:970:8:9:0:1;PT:351:9:9::1;" +
        "PW:680:7:7::1;PY:595:7:9:0:1;QA:974:8:8::1;RE:262:9:9:0:1;RO:40:6:9:0:1;RS:381:7:12:0:1;" +
        "RU:7:10:10::1;RW:250:8:9:0:1;SA:966:9:9:0:1;SB:677:5:7::1;SC:248:7:7::1;SD:249:9:9:0:1;" +
        "SE:46:7:9:0:1;SG:65:8:8::1;SH:290:4:5::1;SI:386:8:8:0:1;SJ:47:8:8::;SK:421:6:9:0:1;" +
        "SL:232:8:8:0:1;SM:378:8:10::1;SN:221:9:9::1;SO:252:6:9:0:1;SR:597:6:7::1;SS:211:9:9:0:1;" +
        "ST:239:7:7::1;SV:503:8:8::1;SX:1:10:10::;SY:963:8:9:0:1;SZ:268:8:8::1;TA:290:4:4::;TC:1:10:10::;" +
        "TD:235:8:8::1;TG:228:8:8::1;TH:66:8:9:0:1;TJ:992:9:9::1;TK:690:4:7::1;TL:670:7:8::1;" +
        "TM:993:8:8::1;TN:216:8:8::1;TO:676:5:7::1;TR:90:10:10:0:1;TT:1:10:10::;TV:688:5:7::1;" +
        "TW:886:8:9:0:1;TZ:255:9:9:0:1;UA:380:9:9:0:1;UG:256:9:9:0:1;US:1:10:10::1;UY:598:8:8:0:1;" +
        "UZ:998:9:9::1;VA:39:6:11::;VC:1:10:10::;VE:58:10:10:0:1;VG:1:10:10::;VI:1:10:10::;" +
        "VN:84:9:10:0:1;VU:678:5:7::1;WF:681:6:6::1;WS:685:5:10::1;XK:383:8:12:0:1;YE:967:7:9:0:1;" +
        "YT:262:9:9:0:;ZA:27:5:9:0:1;ZM:260:9:9:0:1;ZW:263:7:9:0:1"
}
