# فایل بزرگ رو حذف کن (اگر هست)
rm -f gradle-8.9-bin.zip

# پوشه مخفی .git رو حذف کن (برای شروع کاملاً تمیز)
rm -rf .git

# دوباره git رو مقداردهی اولیه کن
git init
git branch -M main

# همه فایل‌ها رو اضافه کن (فایل بزرگ دیگه نیست)
git add .

# commit کن
git commit -m "Initial commit: SorenNGTunnel without large binary files"
