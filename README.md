# Scroll Counter v1.2 (Android · Kotlin · Jetpack Compose)

Facebook, Instagram, TikTok, YouTube, X, Threads, Snapchat, Reddit, LinkedIn আর Pinterest-এ কতবার স্ক্রল করছেন আর কত দূর স্ক্রল করছেন, অ্যাপটা তা গোনে।

## স্ক্রিন ও ফিচার
- **প্রথমবারের সেটআপ (৪ ধাপ):** Welcome → Accessibility (**বাধ্যতামূলক**, এটা না দিলে সামনে যাওয়া যায় না) → Notification ও Battery (সুপারিশকৃত, চাইলে স্কিপ করা যায়)।
- **Today:**
  - প্রিমিয়াম হিরো রিং: রং ৮০%-এ কমলা আর লিমিট পার হলে লাল হয়
  - "Change" চাপলে লিমিট বদলানো যায়
  - স্ট্যাটাস চিপ: Tracking / Paused / Off
  - চারটা টাইল: দূরত্ব, স্ট্রিক (লিমিটের নিচে থাকা দিন), পিক আওয়ার, সবচেয়ে বেশি স্ক্রল করা অ্যাপ
  - ২৪ ঘণ্টার চার্ট আর অ্যাপভিত্তিক শতাংশ
- **History:**
  - ৭ দিন / ৩০ দিন টগল
  - বারে ট্যাপ করলে সেই দিনের হিসাব দেখায়
  - দৈনিক গড়, টার্গেটে থাকা দিন, মোট স্ক্রল, দূরত্ব
  - দিনের তালিকায় ট্যাপ করলে বটম শিটে সেই দিনের ঘণ্টা আর অ্যাপভিত্তিক বিস্তারিত দেখায়
- **Settings:** সব বাটন কাজ করে।
  - Tracking অন/Manage, Pause tracking
  - Battery optimization (অবস্থা দেখায় আর ঠিক করার পেজ খোলে)
  - Daily limit (স্লাইডার আর দ্রুত বাছাইয়ের চিপ)
  - Notification permission (Allow / Test), ৮০% সতর্কতা আর লিমিট পার হওয়ার অ্যালার্ট আলাদা অন/অফ
  - কোন অ্যাপ ইনস্টল আছে তা দেখায়, প্রতিটা অ্যাপ আলাদা অন/অফ করা যায়
  - Theme: System/Light/Dark
  - CSV Export (শেয়ার শিট), Reset today, Delete all history (দুটোতেই নিশ্চিত করার ডায়ালগ আছে)
  - Privacy policy (অ্যাপের ভেতরেই), Ad privacy choices (শুধু যেসব দেশে দরকার), Rate app, Share app, Version

## Permission তালিকা (সব যোগ করা আছে)
| Permission / ঘোষণা | কেন |
|---|---|
| `BIND_ACCESSIBILITY_SERVICE` (service-এ) | স্ক্রল ইভেন্ট শোনার জন্য। শুধু সিস্টেম bind করতে পারে। |
| `POST_NOTIFICATIONS` | Android 13+-এ ৮০% আর লিমিট অ্যালার্টের জন্য, রানটাইমে চাওয়া হয় |
| `INTERNET`, `ACCESS_NETWORK_STATE` | AdMob বিজ্ঞাপন |
| `com.google.android.gms.permission.AD_ID` | Android 13+-এ AdMob-এর advertising ID |
| `<queries>` (১৩টা প্যাকেজ) | কোন সোশ্যাল অ্যাপ ইনস্টল আছে তা দেখার জন্য, `QUERY_ALL_PACKAGES` ছাড়াই |
| Battery optimization | কোনো permission লাগে না, ইউজারকে শুধু সেটিংস পেজে পাঠানো হয় (Play policy মেনে) |
| UMP consent (GDPR) | EEA/UK ইউজারদের জন্য AdMob-এর বাধ্যতামূলক consent ফর্ম |

## বিল্ড
1. Android Studio দিয়ে `ScrollCounter` ফোল্ডারটা Open করুন, Gradle sync হতে দিন।
2. Run চাপুন (minSdk 26 / Android 8.0+)।

## Play Store-এ দেওয়ার আগে
- `AndroidManifest.xml`-এর AdMob **App ID** আর `MainActivity.kt`-এর `BANNER_AD_UNIT_ID` নিজের আইডি দিয়ে বদলান। এখন টেস্ট আইডি বসানো আছে।
- Play Console-এ যা করতে হবে:
  - **Accessibility API declaration** ফর্ম পূরণ করুন। সাথে একটা ছোট ভিডিও দিতে হবে, যেখানে সেটআপের "Allow scroll tracking" ধাপ দেখা যায়।
  - **Data safety** ফর্মে লিখুন: অ্যাপ নিজে কোনো ডেটা সংগ্রহ বা শেয়ার করে না, শুধু AdMob advertising ID ব্যবহার করে।
  - **Privacy policy URL** দিন। অ্যাপের ভেতরের privacy policy-র লেখাটা Blogger পেজে বসিয়ে সেই লিংক দিতে পারেন।
- Release বিল্ড: Build → Generate Signed App Bundle (.aab)।
