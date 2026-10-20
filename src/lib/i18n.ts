"use client";

/* ------------------------------------------------------------------ */
/* callmony i18n — English · አማርኛ · Afaan Oromoo · العربية · ትግርኛ      */
/* ------------------------------------------------------------------ */

import { useApp } from "./store";

export type Lang = "en" | "am" | "om" | "ar" | "ti";

export const LANGUAGES: { code: Lang; label: string; native: string; rtl: boolean }[] = [
  { code: "en", label: "English", native: "English", rtl: false },
  { code: "am", label: "Amharic", native: "አማርኛ", rtl: false },
  { code: "om", label: "Afaan Oromo", native: "Afaan Oromoo", rtl: false },
  { code: "ar", label: "Arabic", native: "العربية", rtl: true },
  { code: "ti", label: "Tigrinya", native: "ትግርኛ", rtl: false },
];

export const isRTL = (lang: Lang | undefined): boolean => {
  const l = LANGUAGES.find((x) => x.code === lang);
  return !!l?.rtl;
};

/** English source strings — the keys every view may use. */
const EN: Record<string, string> = {
  /* nav + tabs */
  "nav.recents": "Recents",
  "nav.contacts": "Contacts",
  "nav.keypad": "Keypad",
  "nav.calculator": "Calculator",
  "nav.more": "More",

  /* tabs / sections */
  "tab.ledger": "Ledger",
  "tab.lists": "Lists",
  "tab.notes": "Notes",
  "tab.bank": "Bank",
  "tab.reminders": "Reminders",
  "tab.info": "Info",

  /* common actions */
  "act.save": "Save",
  "act.cancel": "Cancel",
  "act.delete": "Delete",
  "act.edit": "Edit",
  "act.add": "Add",
  "act.copy": "Copy",
  "act.copied": "Copied",
  "act.share": "Share",
  "act.search": "Search",
  "act.close": "Close",
  "act.back": "Back",
  "act.done": "Done",
  "act.remove": "Remove",
  "act.rename": "Rename",
  "act.call": "Call",
  "act.message": "Message",
  "act.sms": "SMS",
  "act.whatsapp": "WhatsApp",
  "act.telegram": "Telegram",
  "act.email": "Email",
  "act.more": "More",
  "act.newContact": "Add contact",
  "act.dial": "Dial",

  /* headers */
  "hdr.general": "General",
  "hdr.trash": "Trash",
  "hdr.settings": "Settings",
  "hdr.tagline": "Talk. Track. Settle up.",

  /* contact detail */
  "cd.theyOweYou": "They owe you",
  "cd.youOweThem": "You owe them",
  "cd.settledUp": "Settled up",
  "cd.favorites": "Favorites",
  "cd.addToFavorites": "Added to favorites",
  "cd.removedFromFavorites": "Removed from favorites",
  "cd.editContact": "Edit contact",
  "cd.chooseNumber": "Choose a number",
  "cd.chooseAccount": "Choose an account",
  "cd.default": "Default",
  "cd.primary": "Primary",
  "cd.noPhone": "No phone number on this contact",
  "cd.noEmail": "No email address on this contact",
  "cd.noTelegram": "No Telegram on this contact",

  /* ledger */
  "lg.credit": "Credit",
  "lg.debit": "Debit",
  "lg.income": "Income",
  "lg.expense": "Expense",
  "lg.net": "Net",
  "lg.balance": "Balance",
  "lg.paid": "Paid",
  "lg.pending": "Pending",
  "lg.remaining": "Remaining",
  "lg.payments": "payments",
  "lg.addPayment": "Add payment",
  "lg.paymentHistory": "Payment history",
  "lg.settleUp": "Settle up",
  "lg.group": "Group",
  "lg.groups": "Groups",
  "lg.newGroup": "New group",
  "lg.ungrouped": "Single entries",
  "lg.inGroup": "in this group",
  "lg.overpaidBy": "Overpaid by",
  "lg.openLists": "Open lists",
  "lg.countedInBalance": "counted in balance",

  /* lists */
  "ls.newList": "New list",
  "ls.total": "Total",
  "ls.allSettled": "All settled",
  "ls.items": "items",

  /* calculator */
  "calc.useValue": "Use this value",
  "calc.history": "History",
  "calc.noCalculations": "No calculations yet",

  /* keypad */
  "kp.enterNumber": "Enter a number",
  "kp.readyToConnect": "Ready to connect",

  /* reminders */
  "rm.overdue": "Overdue",
  "rm.today": "Today",
  "rm.upcoming": "Upcoming",
  "rm.done": "Done",
  "rm.snooze": "Snooze",

  /* settings */
  "st.appearance": "Appearance",
  "st.theme": "Theme",
  "st.light": "Light",
  "st.dark": "Dark",
  "st.auto": "Auto",
  "st.language": "Language",
  "st.hideBalances": "Hide balances",
  "st.backup": "Backup & restore",
  "st.restore": "Restore",
  "st.storage": "Storage",
  "st.about": "About",
  "st.notifications": "Notifications",
  "st.security": "Security",

  /* misc */
  "misc.searchPlaceholder": "Search",
  "misc.noResults": "No results",
  "misc.confirm": "Confirm",
  "misc.attach": "Attach",
  "misc.attached": "Attached",
};

/* Amharic — አማርኛ */
const AM: Record<string, string> = {
  "nav.recents": "የቅርብ ጊዜ",
  "nav.contacts": "እውቂያዎች",
  "nav.keypad": "የቁጥር ሰሌዳ",
  "nav.calculator": "ካልኩሌተር",
  "nav.more": "ተጨማሪ",

  "tab.ledger": "መዝገብ",
  "tab.lists": "ዝርዝሮች",
  "tab.notes": "ማስታወሻዎች",
  "tab.bank": "ባንክ",
  "tab.reminders": "አስታዋሾች",
  "tab.info": "መረጃ",

  "act.save": "አስቀምጥ",
  "act.cancel": "ተወግዥ",
  "act.delete": "ሰርዝ",
  "act.edit": "አርትዕ",
  "act.add": "ጨምር",
  "act.copy": "ቅዳ",
  "act.copied": "ተቀድቷል",
  "act.share": "አጋራ",
  "act.search": "ፍለጋ",
  "act.close": "ዝጋ",
  "act.back": "ተመለስ",
  "act.done": "ተጠናቀቀ",
  "act.remove": "አስወግድ",
  "act.rename": "እንደገና ሰይም",
  "act.call": "ደውል",
  "act.message": "መልዕክት",
  "act.sms": "ኤስኤምኤስ",
  "act.whatsapp": "ዋትስአፕ",
  "act.telegram": "ቴሌግራም",
  "act.email": "ኢሜይል",
  "act.more": "ተጨማሪ",
  "act.newContact": "እውቂያ ጨምር",
  "act.dial": "ሰርብ",

  "hdr.general": "አጠቃላይ",
  "hdr.trash": "ቆሻሻ",
  "hdr.settings": "ቅንብሮች",
  "hdr.tagline": "ደውል። መዝግብ። አስተካክል።",

  "cd.theyOweYou": "ይገባሃል",
  "cd.youOweThem": "ታገባዋለህ",
  "cd.settledUp": "ተስተካክሏል",
  "cd.favorites": "ተወዳጆች",
  "cd.addToFavorites": "ወደ ተወዳጆች ታክሏል",
  "cd.removedFromFavorites": "ከተወዳጆች ተወግዷል",
  "cd.editContact": "እውቂያ አርትዕ",
  "cd.chooseNumber": "ቁጥር ምረጥ",
  "cd.chooseAccount": "መለያ ምረጥ",
  "cd.default": "ነባሪ",
  "cd.primary": "ዋና",
  "cd.noPhone": "በዚህ እውቂያ ላይ ስልክ ቁጥር የለም",
  "cd.noEmail": "በዚህ እውቂያ ላይ ኢሜይል የለም",
  "cd.noTelegram": "በዚህ እውቂያ ላይ ቴሌግራም የለም",

  "lg.credit": "ክሬዲት",
  "lg.debit": "ዴቢት",
  "lg.income": "ገቢ",
  "lg.expense": "ወጪ",
  "lg.net": "የተጣራ",
  "lg.balance": "ቀሪ ሂሳብ",
  "lg.paid": "የተከፈለ",
  "lg.pending": "ያልተከፈለ",
  "lg.remaining": "ቀሪ",
  "lg.payments": "ክፍያዎች",
  "lg.addPayment": "ክፍያ ጨምር",
  "lg.paymentHistory": "የክፍያ ታሪክ",
  "lg.settleUp": "ሂሳብ አስተካክል",
  "lg.group": "ቡድን",
  "lg.groups": "ቡድኖች",
  "lg.newGroup": "አዲስ ቡድን",
  "lg.ungrouped": "ነጠላ መዝገቦች",
  "lg.inGroup": "በዚህ ቡድን ውስጥ",
  "lg.overpaidBy": "በዚህ መጠን የበዛ ተከፍሏል",
  "lg.openLists": "የተከፈቱ ዝርዝሮች",
  "lg.countedInBalance": "በሂሳብ ውስጥ ተጨምሯል",

  "ls.newList": "አዲስ ዝርዝር",
  "ls.total": "ጠቅላላ",
  "ls.allSettled": "ሁሉም ተከፍሏል",
  "ls.items": "እቃዎች",

  "calc.useValue": "ይህን ዋጋ ተጠቀም",
  "calc.history": "ታሪክ",
  "calc.noCalculations": "እስካሁን የማስሊያ የለም",

  "kp.enterNumber": "ቁጥር አስገባ",
  "kp.readyToConnect": "ለመገናኘት ዝግጁ",

  "rm.overdue": "ያለቀ ጊዜ",
  "rm.today": "ዛሬ",
  "rm.upcoming": "የሚመጣ",
  "rm.done": "ተጠናቋል",
  "rm.snooze": "አስቆም",

  "st.appearance": "መልክ",
  "st.theme": "ገጽታ",
  "st.light": "ብርሃናማ",
  "st.dark": "ጨለማ",
  "st.auto": "ራስ-ሰር",
  "st.language": "ቋንቋ",
  "st.hideBalances": "ሂሳብ ደብቅ",
  "st.backup": "መጠባበቂያ እና መልስ",
  "st.restore": "መልስ",
  "st.storage": "ማከማቻ",
  "st.about": "ስለ",
  "st.notifications": "ማሳወቂያዎች",
  "st.security": "ደህንነት",

  "misc.searchPlaceholder": "ፍለጋ",
  "misc.noResults": "ውጤት የለም",
  "misc.confirm": "አረጋግጥ",
  "misc.attach": "አያይዝ",
  "misc.attached": "ተያይዟል",
};

/* Afaan Oromo */
const OM: Record<string, string> = {
  "nav.recents": "Dhiyoo",
  "nav.contacts": "Quunnamtii",
  "nav.keypad": "Kiipiidii",
  "nav.calculator": "Herrega",
  "nav.more": "Dabalata",

  "tab.ledger": "Galmee",
  "tab.lists": "Tarreewwan",
  "tab.notes": "Yaadannoo",
  "tab.bank": "Baankii",
  "tab.reminders": "Yaadachisa",
  "tab.info": "Odeeffannoo",

  "act.save": "Ol kaa'i",
  "act.cancel": "Haqi",
  "act.delete": "Balleessi",
  "act.edit": "Gulaali",
  "act.add": "Dabalisi",
  "act.copy": "Baafadhu",
  "act.copied": "Baafame",
  "act.share": "Qoodi",
  "act.search": "Barbaadi",
  "act.close": "Cufi",
  "act.back": "Deebi'i",
  "act.done": "Xumuruu",
  "act.remove": "Haadhu",
  "act.rename": "Maqaa dogoggora",
  "act.call": "Waamii",
  "act.message": "Ergaa",
  "act.sms": "SMS",
  "act.whatsapp": "WhatsApp",
  "act.telegram": "Telegram",
  "act.email": "Imeelii",
  "act.more": "Dabalata",
  "act.newContact": "Quunnamtaa dabalisi",
  "act.dial": "Waami",

  "hdr.general": "Waliigalaa",
  "hdr.trash": "Madaqoo",
  "hdr.settings": "Qindaa'ina",
  "hdr.tagline": "Waami. Galmeessi. Wal qixeesi.",

  "cd.theyOweYou": "Sii qaba",
  "cd.youOweThem": "Isaa qabda",
  "cd.settledUp": "Wal qixa",
  "cd.favorites": "Jaallatamoota",
  "cd.addToFavorites": "Jaallatamoota dabalameera",
  "cd.removedFromFavorites": "Jaallatamootaa haadhamuus",
  "cd.editContact": "Quunnamtaa gulaali",
  "cd.chooseNumber": "Lakkoofsa filadhu",
  "cd.chooseAccount": "Herrega filadhu",
  "cd.default": "Durtii",
  "cd.primary": "Guguddaa",
  "cd.noPhone": "Lakkoofsa billeetti hin qabu",
  "cd.noEmail": "Imeelii hin qabu",
  "cd.noTelegram": "Telegram hin qabu",

  "lg.credit": "Kireedii",
  "lg.debit": "Deebii",
  "lg.income": "Galu",
  "lg.expense": "Baasii",
  "lg.net": "Madda",
  "lg.balance": "Hirree",
  "lg.paid": "Kaffalame",
  "lg.pending": "Hin kaffalamne",
  "lg.remaining": "Hirree",
  "lg.payments": "kaffalota",
  "lg.addPayment": "Kaffala dabalisi",
  "lg.paymentHistory": "Seenaa kaffalaa",
  "lg.settleUp": "Herrega wal qixeesi",
  "lg.group": "Garee",
  "lg.groups": "Gareewwan",
  "lg.newGroup": "Garee haaraa",
  "lg.ungrouped": "Galmee takkaa",
  "lg.inGroup": "garee kana keessatti",
  "lg.overpaidBy": "Caalaa kaffale",
  "lg.openLists": "Tarree baname",
  "lg.countedInBalance": "hirree keessatti galmeeffame",

  "ls.newList": "Tarree haaraa",
  "ls.total": "Waliigalaa",
  "ls.allSettled": "Hundinuu wal qixa",
  "ls.items": "wantoota",

  "calc.useValue": "Gatii kana fayyadami",
  "calc.history": "Seenaa",
  "calc.noCalculations": "Herreen hin jiru",

  "kp.enterNumber": "Lakkoofsa galchi",
  "kp.readyToConnect": "Wal qunnamuuf qophaa'e",

  "rm.overdue": "Yaada yeroo dabarsise",
  "rm.today": "Har'a",
  "rm.upcoming": "Kan dhufu",
  "rm.done": "Xumurame",
  "rm.snooze": "Snooze",

  "st.appearance": "Mul'ata",
  "st.theme": "Bifa",
  "st.light": "Ifa",
  "st.dark": "Dukkana",
  "st.auto": "Ofiin",
  "st.language": "Afaan",
  "st.hideBalances": "Hirree dhokki",
  "st.backup": "Kuusaa fi deebii",
  "st.restore": "Deebisi",
  "st.storage": "Kuusaa",
  "st.about": "Waa'ee",
  "st.notifications": "Beeksisa",
  "st.security": "Nageenya",

  "misc.searchPlaceholder": "Barbaadi",
  "misc.noResults": "Argamsiisa hin jiru",
  "misc.confirm": "Mirkaneessi",
  "misc.attach": "Kunnisi",
  "misc.attached": "Kunneeffame",
};

/* العربية */
const AR: Record<string, string> = {
  "nav.recents": "الأحدث",
  "nav.contacts": "جهات الاتصال",
  "nav.keypad": "لوحة المفاتيح",
  "nav.calculator": "الآلة الحاسبة",
  "nav.more": "المزيد",

  "tab.ledger": "السجل",
  "tab.lists": "القوائم",
  "tab.notes": "الملاحظات",
  "tab.bank": "البنك",
  "tab.reminders": "التذكيرات",
  "tab.info": "معلومات",

  "act.save": "حفظ",
  "act.cancel": "إلغاء",
  "act.delete": "حذف",
  "act.edit": "تعديل",
  "act.add": "إضافة",
  "act.copy": "نسخ",
  "act.copied": "تم النسخ",
  "act.share": "مشاركة",
  "act.search": "بحث",
  "act.close": "إغلاق",
  "act.back": "رجوع",
  "act.done": "تم",
  "act.remove": "إزالة",
  "act.rename": "إعادة تسمية",
  "act.call": "اتصال",
  "act.message": "رسالة",
  "act.sms": "رسالة نصية",
  "act.whatsapp": "واتساب",
  "act.telegram": "تلغرام",
  "act.email": "بريد إلكتروني",
  "act.more": "المزيد",
  "act.newContact": "إضافة جهة اتصال",
  "act.dial": "اتصل",

  "hdr.general": "عام",
  "hdr.trash": "المحذوفات",
  "hdr.settings": "الإعدادات",
  "hdr.tagline": "اتصل. سجّل. سوِّ الحساب.",

  "cd.theyOweYou": "يستحق لك",
  "cd.youOweThem": "تستحق عليه",
  "cd.settledUp": "تمت التسوية",
  "cd.favorites": "المفضلة",
  "cd.addToFavorites": "أُضيف إلى المفضلة",
  "cd.removedFromFavorites": "أُزيل من المفضلة",
  "cd.editContact": "تعديل جهة الاتصال",
  "cd.chooseNumber": "اختر رقمًا",
  "cd.chooseAccount": "اختر حسابًا",
  "cd.default": "الافتراضي",
  "cd.primary": "الرئيسي",
  "cd.noPhone": "لا يوجد رقم هاتف لهذه الجهة",
  "cd.noEmail": "لا يوجد بريد إلكتروني لهذه الجهة",
  "cd.noTelegram": "لا يوجد تلغرام لهذه الجهة",

  "lg.credit": "له",
  "lg.debit": "عليه",
  "lg.income": "دخل",
  "lg.expense": "مصروف",
  "lg.net": "الصافي",
  "lg.balance": "الرصيد",
  "lg.paid": "مدفوع",
  "lg.pending": "متبقٍّ",
  "lg.remaining": "المتبقي",
  "lg.payments": "دفوعات",
  "lg.addPayment": "إضافة دفعة",
  "lg.paymentHistory": "سجل الدفعات",
  "lg.settleUp": "تسوية الحساب",
  "lg.group": "مجموعة",
  "lg.groups": "المجموعات",
  "lg.newGroup": "مجموعة جديدة",
  "lg.ungrouped": "قيود مفردة",
  "lg.inGroup": "في هذه المجموعة",
  "lg.overpaidBy": "زائد بمقدار",
  "lg.openLists": "قوائم مفتوحة",
  "lg.countedInBalance": "محسوب في الرصيد",

  "ls.newList": "قائمة جديدة",
  "ls.total": "الإجمالي",
  "ls.allSettled": "تم تسوية الكل",
  "ls.items": "عناصر",

  "calc.useValue": "استخدم هذه القيمة",
  "calc.history": "السجل",
  "calc.noCalculations": "لا حسابات بعد",

  "kp.enterNumber": "أدخل رقمًا",
  "kp.readyToConnect": "جاهز للاتصال",

  "rm.overdue": "متأخر",
  "rm.today": "اليوم",
  "rm.upcoming": "قادم",
  "rm.done": "منجز",
  "rm.snooze": "غفوة",

  "st.appearance": "المظهر",
  "st.theme": "النمط",
  "st.light": "فاتح",
  "st.dark": "داكن",
  "st.auto": "تلقائي",
  "st.language": "اللغة",
  "st.hideBalances": "إخفاء الأرصدة",
  "st.backup": "نسخ احتياطي واستعادة",
  "st.restore": "استعادة",
  "st.storage": "التخزين",
  "st.about": "حول",
  "st.notifications": "الإشعارات",
  "st.security": "الأمان",

  "misc.searchPlaceholder": "بحث",
  "misc.noResults": "لا نتائج",
  "misc.confirm": "تأكيد",
  "misc.attach": "إرفاق",
  "misc.attached": "مُرفق",
};

/* ትግርኛ */
const TI: Record<string, string> = {
  "nav.recents": "ኣብ ቀረባ",
  "nav.contacts": "እዋቂታት",
  "nav.keypad": "ኪፓድ",
  "nav.calculator": "ካልኩሌተር",
  "nav.more": "ተወሳኺ",

  "tab.ledger": "መዝገብ",
  "tab.lists": "ዝርዝርታት",
  "tab.notes": "መዘኻኸርታት",
  "tab.bank": "ባንክ",
  "tab.reminders": "ኣስታውራት",
  "tab.info": "ሓበሬታ",

  "act.save": "ኣቐምጥ",
  "act.cancel": "ተወግዥ",
  "act.delete": "ስርዝ",
  "act.edit": "ኣርትዕ",
  "act.add": "ወስኽ",
  "act.copy": "ቀዳሕ",
  "act.copied": "ተቐዲሑ",
  "act.share": "ኣካፍል",
  "act.search": "ምንዳይ",
  "act.close": "ዕጸው",
  "act.back": "ዝምስል",
  "act.done": "ወዲእካ",
  "act.remove": "ኣውጽእ",
  "act.rename": "ኣደምሽር ስም",
  "act.call": "ደውል",
  "act.message": "መልእኽቲ",
  "act.sms": "SMS",
  "act.whatsapp": "WhatsApp",
  "act.telegram": "Telegram",
  "act.email": "ኢመይል",
  "act.more": "ተወሳኺ",
  "act.newContact": "እዋቂ ወስኽ",
  "act.dial": "ደውል",

  "hdr.general": "ብሓፈሻ",
  "hdr.trash": "ቆሻሻ",
  "hdr.settings": "መቐጻረርታ",
  "hdr.tagline": "ደውል። መዝግብ። ስምምዕ።",

  "cd.theyOweYou": "ይገበርካ እዩ",
  "cd.youOweThem": "ተገበርካ እዩ",
  "cd.settledUp": "ተመዳደለ",
  "cd.favorites": "ዝፈቱኡ",
  "cd.addToFavorites": "ናብ ዝፈቱኡ ተወሰኸ",
  "cd.removedFromFavorites": "ካብ ዝፈቱኡ ተነጸለ",
  "cd.editContact": "እዋቂ ኣርትዕ",
  "cd.chooseNumber": "ቁጽሪ ምረጽ",
  "cd.chooseAccount": "መለልያ ምረጽ",
  "cd.default": "ነባር",
  "cd.primary": "መርበቕ",
  "cd.noPhone": "ቁጽሪ ተሌፎን የብሉን",
  "cd.noEmail": "ኢመይል የብሉን",
  "cd.noTelegram": "ቴሌግራም የብሉን",

  "lg.credit": "ክሬዲት",
  "lg.debit": "ዴቢት",
  "lg.income": "ኣታይ",
  "lg.expense": "ውጻኢ",
  "lg.net": "ተረፈ",
  "lg.balance": "ቀሪ ሂሳብ",
  "lg.paid": "ተኺሉ",
  "lg.pending": "ዘይተኺለ",
  "lg.remaining": "ዝተረፈ",
  "lg.payments": "ክፍሊታት",
  "lg.addPayment": "ክፍሊት ወስኽ",
  "lg.paymentHistory": "ታሪኽ ክፍሊት",
  "lg.settleUp": "ሂሳብ ኣመሓድስ",
  "lg.group": "ጉጅለ",
  "lg.groups": "ጉጅላት",
  "lg.newGroup": "ሓድሽ ጉጅለ",
  "lg.ungrouped": "ጠቃላላ መዝገባት",
  "lg.inGroup": "ኣብዚ ጉጅለ",
  "lg.overpaidBy": "በዚ ዝወዓለ ተኺሉ",
  "lg.openLists": "ንጹር ዝርዝርታት",
  "lg.countedInBalance": "ኣብ ሂሳብ ተቐሪቡ",

  "ls.newList": "ሓድሽ ዝርዝር",
  "ls.total": "ጠቕላላ",
  "ls.allSettled": "ኩሉ ተኺሉ",
  "ls.items": "ኣቕሳት",

  "calc.useValue": "ነዚ ክብርቲ ተጠቐም",
  "calc.history": "ታሪኽ",
  "calc.noCalculations": "ስምያይ የለን",

  "kp.enterNumber": "ቁጽሪ ኣእቱ",
  "kp.readyToConnect": "ንምትኻልል ዝዱን",

  "rm.overdue": "ዝሓለፈ ግዜ",
  "rm.today": "ትሕቲ",
  "rm.upcoming": "ዝመጽእ",
  "rm.done": "ተዛዚሙ",
  "rm.snooze": "ኣስቕምጽ",

  "st.appearance": "ምርኣይ",
  "st.theme": "ቅርጺ",
  "st.light": "ብርሃናዊ",
  "st.dark": "ጸሊም",
  "st.auto": "ኣውቶማቲክ",
  "st.language": "ቋንቋ",
  "st.hideBalances": "ሂሳብ ሓብእ",
  "st.backup": "ቅጂ ምዕቃብን መለስን",
  "st.restore": "መለስ",
  "st.storage": "መኽዘን",
  "st.about": "ብዛዕባ",
  "st.notifications": "መልክዕታት",
  "st.security": "ደህንነት",

  "misc.searchPlaceholder": "ምንዳይ",
  "misc.noResults": "ውጽኢት የለን",
  "misc.confirm": "ኣረጋግጽ",
  "misc.attach": "ኣካትት",
  "misc.attached": "ተኣኪቡ",
};

const DICTS: Record<Lang, Record<string, string>> = { en: EN, am: AM, om: OM, ar: AR, ti: TI };

const normalizeLang = (v: string | undefined): Lang => {
  const l = (v || "en") as Lang;
  return DICTS[l] ? l : "en";
};

/** Imperative translate — reads the current language from the store (no reactivity). */
export function t(key: string): string {
  const lang = normalizeLang(useApp.getState()?.settings?.language);
  return DICTS[lang][key] ?? EN[key] ?? key;
}

/** Reactive translate — component re-renders when the language setting changes. */
export function useT(): (key: string) => string {
  const language = useApp((s) => s.settings.language);
  const lang = normalizeLang(language);
  const dict = DICTS[lang];
  return (key: string) => dict[key] ?? EN[key] ?? key;
}

/** Current language + direction, reactive. */
export function useLang(): { lang: Lang; rtl: boolean; dir: "ltr" | "rtl" } {
  const language = useApp((s) => s.settings.language);
  const lang = normalizeLang(language);
  const rtl = isRTL(lang);
  return { lang, rtl, dir: rtl ? "rtl" : "ltr" };
}

export const setLang = (lang: Lang) => useApp.getState().updateSettings({ language: lang });
