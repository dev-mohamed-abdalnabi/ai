# Flow Agent

تطبيق أندرويد AI Agent: بتحط **OpenRouter API key** و **GitHub token**، وتدّيله ملفات/ZIP، وهو يرفعها على المستودع كـ commit واحد (تحديث). بيتخطى الملفات اللي ماتغيرتش، وبيطلب منك تأكيد قبل أي رفع/مسح/إنشاء مستودع.

## GitHub token
- Classic: صلاحيات `repo` + `workflow` (لو هترفع ملفات `.github/workflows`).
- Fine-grained: Contents (Read/Write) + Workflows (Read/Write) + Administration (Write) لو عايزه ينشئ مستودعات.

## الرفع على GitHub (من غير فولدرات)
1. ارفع كل الملفات (drag & drop) في جذر المستودع.
2. الـ workflow: *Add file → Create new file*، اكتب الاسم `.github/workflows/build.yml` والصق محتواه.

## الإصدار
`git tag v1.0.0 && git push --tags` أو زرار *Run workflow* (بيعمل tag لو مش موجود). الـ APK بيطلع في Releases.

## تحذير التوقيع
المفتاح الافتراضي (`test_keystore_b64.txt`) عام وللتجربة فقط. لا تنشر به على Google Play. لمفتاحك: secrets `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## ملاحظات
- لازم موديل OpenRouter يدعم Tools (الافتراضي `openai/gpt-4o-mini` قابل للتغيير من الإعدادات).
- المفاتيح متخزنة في تخزين التطبيق الخاص (غير مشفّرة).
- الحد: 5000 ملف في الرفعة، وكل ملف أقل من 90MB.
- خط Tajawal/Cairo مش مضمّن (مفيش وصول لتحميله هنا)، التطبيق بيستخدم خط النظام.
