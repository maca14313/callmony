/* One-shot: append RN-specific keys to each language dictionary. */
const fs = require("fs");

const EXTRA = {
  en: {
    "date.months": "Jan,Feb,Mar,Apr,May,Jun,Jul,Aug,Sep,Oct,Nov,Dec",
    "date.weekdays": "Sunday,Monday,Tuesday,Wednesday,Thursday,Friday,Saturday",
    "date.today": "Today",
    "date.yesterday": "Yesterday",
    "perm.contactsTitle": "Contacts access",
    "perm.contactsMsg": "callmony needs access to your phone's contacts to show them here. Your data stays on your phone.",
    "perm.callLogTitle": "Call log access",
    "perm.callLogMsg": "callmony needs access to your call log to show your recent calls.",
    "perm.openSettings": "Open settings",
    "rn.restartTitle": "Restart needed",
    "rn.restartMsg": "The layout direction changes after you restart the app.",
    "st.version": "Version",
    "cl.appCallsHint": "Calls you started from callmony. iPhone doesn't let apps read the system call log.",
    "misc.on": "On",
    "misc.off": "Off",
  },
  am: {
    "date.months": "ጃንዩወሪ,ፌብሩወሪ,ማርች,ኤፕሪል,ሜይ,ጁን,ጁላይ,ኦገስት,ሴፕቴምበር,ኦክቶበር,ኖቬምበር,ዲሴምበር",
    "date.weekdays": "እሁድ,ሰኞ,ማክሰኞ,ረቡዕ,ሐሙስ,ዓርብ,ቅዳሜ",
    "date.today": "ዛሬ",
    "date.yesterday": "ትናንት",
    "perm.contactsTitle": "የእውቂያዎች መዳረሻ",
    "perm.contactsMsg": "እውቂያዎችን እዚህ ለማሳየት callmony የስልክዎን እውቂያዎች መድረስ ያስፈልገዋል። የእርስዎ መረጃ በስልክዎ ላይ ይቆያል።",
    "perm.callLogTitle": "የጥሪ መዝገብ መዳረሻ",
    "perm.callLogMsg": "የቅርብ ጥሪዎችዎን ለማሳየት callmony የጥሪ መዝገብዎን መድረስ ያስፈልገዋል።",
    "perm.openSettings": "ቅንብሮችን ክፈት",
    "rn.restartTitle": "እንደገና ማስጀመር ያስፈልጋል",
    "rn.restartMsg": "የአቀማመጥ አቅጣጫ ከመተግበሪያው እንደገና ከተጀመረ በኋላ ይቀየራል።",
    "st.version": "ቅጽ",
    "cl.appCallsHint": "ከcallmony የጀመሩት ጥሪዎች። አይፎን መተግበሪያዎች የስርዓቱን የጥሪ መዝገብ አያነብም።",
    "misc.on": "በርቷል",
    "misc.off": "ጠፍቷል",
  },
  om: {
    "date.months": "Jaanwaar,Febrwaar,Maars,Epril,Meey,Juun,Juul,Aagost,Seftembar,Oktobar,Noowembar,Disembar",
    "date.weekdays": "Dilbata,Wiixata,Qibxata,Roobii,Kamisa,Jimaata,Sanbata",
    "date.today": "Har'a",
    "date.yesterday": "Kaleessa",
    "perm.contactsTitle": "Seensa namtolchee",
    "perm.contactsMsg": "Namtolchee asitti agarsiisuuf callmony telefala kee namtolchee seensuu barbaachisa. Deetaan kee telefala keetti hafu.",
    "perm.callLogTitle": "Seensa galmee bilbila",
    "perm.callLogMsg": "Bilbiloota haaromsa agarsiisuuf callmony galmee bilbila kee seensuu barbaachisa.",
    "perm.openSettings": "Qindaa'inna banaa",
    "rn.restartTitle": "Haaromsa barbaachisa",
    "rn.restartMsg": "Qajeelfa caasaa appiin akka haaromtuu isaa booda ni jijjiirama.",
    "st.version": "Verishinii",
    "cl.appCallsHint": "Bilbiloota callmony irraa jalqaban. iPhoneiin appoonni galmee bilbila sirnaa dubbachuu hin dandeenyu.",
    "misc.on": "Banaa'e",
    "misc.off": "Cufame",
  },
  ar: {
    "date.months": "يناير,فبراير,مارس,أبريل,مايو,يونيو,يوليو,أغسطس,سبتمبر,أكتوبر,نوفمبر,ديسمبر",
    "date.weekdays": "الأحد,الإثنين,الثلاثاء,الأربعاء,الخميس,الجمعة,السبت",
    "date.today": "اليوم",
    "date.yesterday": "أمس",
    "perm.contactsTitle": "الوصول إلى جهات الاتصال",
    "perm.contactsMsg": "يحتاج callmony إلى الوصول إلى جهات اتصال هاتفك لعرضها هنا. تبقى بياناتك على هاتفك.",
    "perm.callLogTitle": "الوصول إلى سجل المكالمات",
    "perm.callLogMsg": "يحتاج callmony إلى الوصول إلى سجل مكالماتك لعرض مكالماتك الأخيرة.",
    "perm.openSettings": "افتح الإعدادات",
    "rn.restartTitle": "مطلوب إعادة التشغيل",
    "rn.restartMsg": "يتغير اتجاه التخطيط بعد إعادة تشغيل التطبيق.",
    "st.version": "الإصدار",
    "cl.appCallsHint": "المكالمات التي بدأتها من callmony. لا يسمح iPhone للتطبيقات بقراءة سجل مكالمات النظام.",
    "misc.on": "مفعل",
    "misc.off": "معطل",
  },
  ti: {
    "date.months": "ጥሪ,ለካቲት,መጋቢት,ሚያዝያ,ግንቦት,ሰነ,ሓምሌ,ነሓሰ,መስከረም,ጥቅምቲ,ህዳር,ታሕሳስ",
    "date.weekdays": "ሰንበት,ሰኑይ,ሰሉስታ,ረቡዕ,ከሚሽ,ዓርቢ,ቀዳም",
    "date.today": "ወይኒ",
    "date.yesterday": "ትማሕሲ",
    "perm.contactsTitle": "ተላውቕታ ንኽትረኽብ",
    "perm.contactsMsg": "ተላውቕታ ኣብዚ ንኽስረይ፣ callmony ተላውቕታ ባዕልኹም ክኣቱ ኣለዎ። ሓበንታኹም ኣብ ባዕልኹም ኮይኑ ይቕጽል።",
    "perm.callLogTitle": "መዝገብ ጻውዒት ንኽትረኽብ",
    "perm.callLogMsg": "ጻውዒትቲ ሓድሽን ንኽስረይ፣ callmony መዝገብ ጻውዒትኹም ክኣቱ ኣለዎ።",
    "perm.openSettings": "መርጻእታት ክፈት",
    "rn.restartTitle": "ደግማ ምጅማር የድሊ",
    "rn.restartMsg": "ናይ ኣቀማመጥ ኣንፈት ምስ እቲ መተግበሪ ደግማ ምስ ተጀመረ ይለዋወጥ።",
    "st.version": "ዓይነት",
    "cl.appCallsHint": "ካብ callmony ዝተጀመራ ጻውዒትታት። ኣይፎን መተግበሪታት ናይ ስርዓት መዝገብ ጻውዒት ኣይንባብን።",
    "misc.on": "ተኺሺሉ",
    "misc.off": "ተቐሚጡ",
  },
};

const FILES = { en: "en.ts", am: "am.ts", om: "om.ts", ar: "ar.ts", ti: "ti.ts" };
const DIR = "/home/z/my-project/mobile/src/i18n";

for (const [code, file] of Object.entries(FILES)) {
  const path = `${DIR}/${file}`;
  let src = fs.readFileSync(path, "utf8");
  if (src.includes('"date.months"')) {
    console.log(`${file}: already patched`);
    continue;
  }
  const idx = src.lastIndexOf("};");
  if (idx < 0) throw new Error(`no closing brace in ${file}`);
  const lines = Object.entries(EXTRA[code])
    .map(([k, v]) => `  ${JSON.stringify(k)}: ${JSON.stringify(v)},`)
    .join("\n");
  src = `${src.slice(0, idx)}  /* react-native edition extras */\n${lines}\n${src.slice(idx)}`;
  fs.writeFileSync(path, src);
  console.log(`${file}: appended ${Object.keys(EXTRA[code]).length} keys`);
}
