# صلاتي

تطبيق أندرويد: مواقيت الصلاة مع الأذان، المصحف، تلاوات ياسر الدوسري، وأذكار على شاشة القفل.

## بناء الـ APK
كل ما ترفع تعديل على GitHub، بيتبنى الـ APK لحاله من تبويبة **Actions** ← آخر تشغيل ← **Artifacts** ← `salati-apk`.

محليًا (Android Studio أو Gradle 8.9 + JDK 17 + Android SDK 34):
```
gradle assembleDebug
```
